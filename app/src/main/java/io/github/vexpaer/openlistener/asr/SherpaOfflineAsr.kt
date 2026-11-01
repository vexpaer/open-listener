package io.github.vexpaer.openlistener.asr

import com.k2fsa.sherpa.onnx.OfflineRecognizer
import io.github.vexpaer.openlistener.models.ModelLease
import io.github.vexpaer.openlistener.models.ModelStore
import io.github.vexpaer.openlistener.models.SherpaModelRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Simulated streaming for offline models. Finalizes on speech pauses or bounded
 * 4-second chunks without ever uploading microphone audio or persisting PCM.
 * This engine does not advertise partial results it cannot produce.
 */
class SherpaOfflineAsr(private val models: ModelStore, private val modelId: String) : AsrEngine {
    private val manifest = models.catalog.single { it.id == modelId }
    init { require(manifest.runtime != "online") }
    override val capabilities = AsrCapabilities(
        providerId = "sherpa-onnx-offline", displayName = manifest.displayName,
        streaming = false, languages = manifest.languages, uploadsAudio = false)
    private val output = Channel<TranscriptEvent>(Channel.RENDEZVOUS)
    override val events: Flow<TranscriptEvent> = output.receiveAsFlow()
    private val mutex = Mutex()
    private var recognizer: OfflineRecognizer? = null
    private var lease: ModelLease? = null
    private var activeSession: String? = null
    private var closed = false
    private var paused = false
    private var error: String? = null
    private var positionSamples = 0L
    private var chunkStartSamples = 0L
    private var silenceSamples = 0
    private var segmentId = 0L
    private val samples = FloatArray(16_000 * 5)
    private var buffered = 0

    override val availability: EngineAvailability
        get() = when {
            recognizer != null -> EngineAvailability.Ready
            error != null -> EngineAvailability.Unavailable(requireNotNull(error))
            models.isInstalled(modelId) -> EngineAvailability.Ready
            else -> EngineAvailability.NeedsDownload("Download the selected offline speech model")
        }

    override suspend fun prepare(): EngineAvailability = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed)
            if (recognizer != null) return@withLock EngineAvailability.Ready
            if (!models.isInstalled(modelId))
                return@withLock EngineAvailability.NeedsDownload("Download the selected offline speech model")
            var acquired: ModelLease? = null
            try {
                acquired = models.acquire(modelId)
                recognizer = SherpaModelRuntime.createOffline(acquired.directory, manifest)
                lease = acquired
                error = null
                EngineAvailability.Ready
            } catch (c: CancellationException) {
                acquired?.close()
                throw c
            } catch (e: Exception) {
                acquired?.close()
                error = e.message ?: "Offline speech recognition initialization failed"
                EngineAvailability.Unavailable(requireNotNull(error))
            } catch (e: LinkageError) {
                acquired?.close()
                error = "Sherpa offline JNI runtime is incompatible"
                EngineAvailability.Unavailable(requireNotNull(error))
            }
        }
    }

    override suspend fun start(sessionId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(sessionId.isNotBlank())
            check(!closed && activeSession == null && recognizer != null)
            activeSession = sessionId
            buffered = 0
            silenceSamples = 0
            segmentId = 0
            chunkStartSamples = 0
            positionSamples = 0
            paused = false
        }
    }

    override suspend fun acceptPcm(pcm16: ByteArray) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!paused && !closed && activeSession != null)
            require(pcm16.size % 2 == 0 && pcm16.size <= 32_000)
            var offset = 0
            while (offset < pcm16.size) {
                val toCopy = minOf((pcm16.size - offset) / 2, samples.size - buffered)
                var energy = 0f
                for (index in 0 until toCopy) {
                    val p = offset + index * 2
                    val sample = ((pcm16[p].toInt() and 0xff) or (pcm16[p + 1].toInt() shl 8)).toShort()
                    val value = sample.toFloat() / 32768f
                    samples[buffered + index] = value
                    energy += value * value
                }
                buffered += toCopy
                positionSamples += toCopy
                offset += toCopy * 2
                val quiet = toCopy > 0 && energy / toCopy < 0.0001f
                silenceSamples = if (quiet) silenceSamples + toCopy else 0
                // Avoid clipping normal utterances; long speech is periodically decoded.
                if (buffered >= 16_000 * 4 ||
                    (buffered >= 16_000 && silenceSamples >= 16_000 / 2)) {
                    decodeChunk()
                }
            }
        }
    }

    override suspend fun pause() = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(activeSession != null && !closed)
            if (!paused) decodeChunk()
            paused = true
        }
    }
    override suspend fun resume() = withContext(Dispatchers.IO) {
        mutex.withLock { check(activeSession != null && !closed); paused = false }
    }
    override suspend fun flush() = withContext(Dispatchers.IO) {
        mutex.withLock { if (activeSession != null) decodeChunk() }
    }
    override suspend fun stop() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (activeSession != null) decodeChunk()
            activeSession = null
            paused = false
        }
    }
    private suspend fun decodeChunk() {
        if (buffered <= 0) return
        val decoder = checkNotNull(recognizer)
        val session = checkNotNull(activeSession)
        val input = decoder.createStream()
        try {
            input.acceptWaveform(samples.copyOfRange(0, buffered), 16_000)
            decoder.decode(input)
            val text = decoder.getResult(input).text.trim()
            if (text.isNotBlank()) {
                output.send(TranscriptEvent(
                    sessionId = session, segmentId = segmentId++, partial = false,
                    text = text, startOffsetMs = chunkStartSamples * 1000 / 16_000,
                    endOffsetMs = positionSamples * 1000 / 16_000))
            }
        } finally {
            input.release()
            buffered = 0
            silenceSamples = 0
            chunkStartSamples = positionSamples
        }
    }

    override suspend fun close(): Unit = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            if (!closed) {
                closed = true
                try { recognizer?.release() } finally {
                    recognizer = null
                    lease?.close()
                    lease = null
                    activeSession = null
                    output.close()
                }
            }
        }
    }
}
