package io.github.vexpaer.openlistener.asr

import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
import io.github.vexpaer.openlistener.models.ModelLease
import io.github.vexpaer.openlistener.models.ModelStore
import io.github.vexpaer.openlistener.models.SherpaModelRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Genuine online Zipformer recognition using the pinned official Android JNI API.
 * PCM must be signed little-endian PCM16, mono, 16 kHz. Exactly one service-owned
 * collector must collect events before feeding PCM/flush/pause/stop. Rendezvous
 * delivery applies backpressure instead of dropping partials or final segments.
 */
class SherpaStreamingAsr(
    private val models: ModelStore,
    private val modelId: String = ModelStore.DEFAULT_ID
) : AsrEngine {
    private val manifest = models.catalog.single { it.id == modelId }
    override val capabilities = AsrCapabilities(
        providerId = "sherpa-onnx", displayName = manifest.displayName,
        streaming = true, languages = manifest.languages, uploadsAudio = false
    )
    @Volatile private var preparationError: String? = null
    @Volatile private var ready = false
    override val availability: EngineAvailability
        get() = when {
            ready -> EngineAvailability.Ready
            preparationError != null -> EngineAvailability.Unavailable(requireNotNull(preparationError))
            models.isInstalled(modelId) -> EngineAvailability.Ready
            else -> EngineAvailability.NeedsDownload("Download the selected verified Zipformer model")
        }
    private val output = Channel<TranscriptEvent>(Channel.RENDEZVOUS)
    override val events: Flow<TranscriptEvent> = output.receiveAsFlow()
    private val lock = Mutex()
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var lease: ModelLease? = null
    private var session: String? = null
    private var paused = false
    private var finished = false
    private var closed = false
    private var segmentId = 0L
    private var revision = 0
    private var lastPartial = ""
    private var samplesAccepted = 0L
    private var segmentStartSample = 0L

    override suspend fun prepare(): EngineAvailability = withContext(Dispatchers.IO) {
        lock.withLock {
            check(!closed) { "This recognition engine is closed" }
            if (recognizer != null) return@withLock EngineAvailability.Ready
            if (!models.isInstalled(modelId)) return@withLock EngineAvailability.NeedsDownload(
                "Download the selected verified Zipformer model"
            )
            preparationError = null
            var acquired: ModelLease? = null
            try {
                acquired = models.acquire(modelId)
                recognizer = SherpaModelRuntime.create(acquired.directory, manifest)
                lease = acquired
                ready = true
                EngineAvailability.Ready
            } catch (cancelled: CancellationException) {
                acquired?.close()
                throw cancelled
            } catch (error: Exception) {
                acquired?.close()
                preparationError = error.message ?: "Local model initialization failed"
                EngineAvailability.Unavailable(requireNotNull(preparationError))
            } catch (error: LinkageError) {
                acquired?.close()
                preparationError = "The installed Sherpa JNI runtime is unavailable or incompatible"
                EngineAvailability.Unavailable(requireNotNull(preparationError))
            }
        }
    }

    override suspend fun start(sessionId: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            check(!closed && session == null) { "An ASR session is already active or closed" }
            require(sessionId.isNotBlank())
            val decoder = checkNotNull(recognizer) { "Call prepare() before start()" }
            stream = decoder.createStream()
            session = sessionId
            segmentId = 0
            samplesAccepted = 0
            segmentStartSample = 0
            revision = 0
            lastPartial = ""
            paused = false
            finished = false
        }
    }

    override suspend fun acceptPcm(pcm16: ByteArray) = withContext(Dispatchers.IO) {
        lock.withLock {
            check(session != null && !closed && !finished) { "ASR is not accepting audio" }
            check(!paused) { "Resume the ASR engine before accepting PCM" }
            require(pcm16.size % 2 == 0) { "PCM16 frames must contain complete little-endian samples" }
            require(pcm16.size <= 32_000) { "PCM frames must be at most one second" }
            val decoder = checkNotNull(recognizer)
            val input = checkNotNull(stream)
            // Small slices keep endpoint timestamps bounded, even for one-second callers.
            var byteOffset = 0
            while (byteOffset < pcm16.size) {
                currentCoroutineContext().ensureActive()
                val count = minOf(1_600, (pcm16.size - byteOffset) / 2)
                val floats = FloatArray(count) { index ->
                    val offset = byteOffset + index * 2
                    val value = (pcm16[offset].toInt() and 0xff) or (pcm16[offset + 1].toInt() shl 8)
                    value.toShort().toFloat() / 32768.0f
                }
                input.acceptWaveform(floats, SAMPLE_RATE)
                samplesAccepted += count
                byteOffset += count * 2
                drain(decoder, input)
                val text = decoder.getResult(input).text.trim()
                if (decoder.isEndpoint(input)) {
                    emitFinal(text)
                    decoder.reset(input)
                    segmentStartSample = samplesAccepted
                } else if (text != lastPartial) {
                    // Empty revisions also clear a previously visible partial.
                    output.send(event(text, partial = true))
                    lastPartial = text
                }
            }
        }
    }

    override suspend fun pause() = withContext(Dispatchers.IO) {
        lock.withLock {
            check(session != null && !finished && !closed)
            if (!paused) {
                finishStream()
                paused = true
            }
        }
    }

    override suspend fun resume() = withContext(Dispatchers.IO) {
        lock.withLock {
            check(session != null && !finished && !closed)
            if (paused) {
                stream = checkNotNull(recognizer).createStream()
                paused = false
                segmentStartSample = samplesAccepted
            }
        }
    }

    override suspend fun flush() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (session != null && !finished) {
                finishStream()
                finished = true
            }
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (session != null) {
                if (!finished) finishStream()
                session = null
                paused = false
                finished = false
            }
        }
    }

    /** Caller must flush/stop with its event collector alive before closing. */
    override suspend fun close(): Unit = withContext(NonCancellable + Dispatchers.IO) {
        lock.withLock {
            if (!closed) {
                closed = true
                ready = false
                try {
                    stream?.release()
                } finally {
                    stream = null
                    try {
                        recognizer?.release()
                    } finally {
                        recognizer = null
                        lease?.close()
                        lease = null
                        session = null
                        output.close()
                    }
                }
            }
        }
    }

    private suspend fun finishStream() {
        val input = stream ?: return
        val decoder = checkNotNull(recognizer)
        try {
            // Official Android example feeds tail silence, then inputFinished(),
            // then drains isReady(). Synthetic zeros exist only in bounded RAM.
            input.acceptWaveform(FloatArray(8_000), SAMPLE_RATE)
            input.inputFinished()
            drain(decoder, input)
            emitFinal(decoder.getResult(input).text.trim())
            segmentStartSample = samplesAccepted
        } finally {
            input.release()
            stream = null
        }
    }

    private suspend fun drain(decoder: OnlineRecognizer, input: OnlineStream) {
        while (decoder.isReady(input)) {
            currentCoroutineContext().ensureActive()
            decoder.decode(input)
        }
    }

    private suspend fun emitFinal(text: String) {
        if (text.isNotEmpty() || lastPartial.isNotEmpty()) {
            // Even an empty final closes an earlier partial rather than leaving it stale.
            output.send(event(text, partial = false))
            segmentId++
        }
        lastPartial = ""
        revision = 0
    }

    private fun event(text: String, partial: Boolean) = TranscriptEvent(
        sessionId = checkNotNull(session), segmentId = segmentId, partial = partial, text = text,
        startOffsetMs = segmentStartSample * 1_000 / SAMPLE_RATE,
        endOffsetMs = if (partial) null else samplesAccepted * 1_000 / SAMPLE_RATE,
        revision = revision++
    )

    companion object { private const val SAMPLE_RATE = 16_000 }
}
