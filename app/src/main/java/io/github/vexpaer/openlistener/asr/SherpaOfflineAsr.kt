package io.github.vexpaer.openlistener.asr

import com.k2fsa.sherpa.onnx.OfflineRecognizer
import io.github.vexpaer.openlistener.models.ModelLease
import io.github.vexpaer.openlistener.models.ModelStore
import io.github.vexpaer.openlistener.models.SherpaModelRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Bounded offline recognizer for continuous microphone input. Whisper-like offline
 * models do not produce transducer partials; completed utterances appear after
 * silence or a seven-second window, then are passed to the normal translation queue.
 */
class SherpaOfflineAsr(private val models: ModelStore, private val modelId: String) : AsrEngine {
    private val manifest = models.catalog.single { it.id == modelId && it.backend.startsWith("offline-") }
    override val capabilities = AsrCapabilities("sherpa-onnx", manifest.displayName,
        streaming = false, languages = manifest.languages, uploadsAudio = false)
    private val eventsChannel = Channel<TranscriptEvent>(Channel.RENDEZVOUS)
    override val events = eventsChannel.receiveAsFlow()
    private val lock = Mutex()
    private var decoder: OfflineRecognizer? = null
    private var lease: ModelLease? = null
    private var sessionId: String? = null
    private var closed = false
    private var paused = false
    private var sequence = 0L
    private var acceptedSamples = 0L
    private var utteranceStart = 0L
    private val pcm = ByteArrayOutputStream()
    private var silenceSamples = 0L
    @Volatile private var failure: String? = null

    override val availability: EngineAvailability get() = when {
        closed -> EngineAvailability.Unavailable("Local recognizer is closed")
        failure != null -> EngineAvailability.Unavailable(requireNotNull(failure))
        models.isInstalled(modelId) -> EngineAvailability.Ready
        else -> EngineAvailability.NeedsDownload("Download the selected local model")
    }

    override suspend fun prepare(): EngineAvailability = withContext(Dispatchers.IO) {
        lock.withLock {
            check(!closed)
            if (decoder != null) return@withLock EngineAvailability.Ready
            if (!models.isInstalled(modelId)) return@withLock availability
            var acquired: ModelLease? = null
            try {
                acquired = models.acquire(modelId)
                decoder = SherpaModelRuntime.createOffline(acquired.directory, manifest)
                lease = acquired
                failure = null
                EngineAvailability.Ready
            } catch (e: CancellationException) {
                acquired?.close()
                throw e
            } catch (e: Exception) {
                acquired?.close()
                failure = e.message ?: "Unable to initialize offline speech recognition"
                EngineAvailability.Unavailable(requireNotNull(failure))
            } catch (e: LinkageError) {
                acquired?.close()
                failure = "Installed native ASR runtime is incompatible"
                EngineAvailability.Unavailable(requireNotNull(failure))
            }
        }
    }

    override suspend fun start(sessionId: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            check(!closed && this@SherpaOfflineAsr.sessionId == null && decoder != null)
            require(sessionId.isNotBlank())
            this@SherpaOfflineAsr.sessionId = sessionId
            paused = false
            sequence = 0
            acceptedSamples = 0
            utteranceStart = 0
            pcm.reset()
            silenceSamples = 0
        }
    }

    override suspend fun acceptPcm(pcm16: ByteArray) = withContext(Dispatchers.IO) {
        lock.withLock {
            check(sessionId != null && !paused && !closed)
            require(pcm16.isNotEmpty() && pcm16.size % 2 == 0 && pcm16.size <= 32_000)
            pcm.write(pcm16)
            acceptedSamples += pcm16.size / 2
            var energy = 0.0
            for (i in pcm16.indices step 2) {
                val value = ((pcm16[i].toInt() and 255) or
                    (pcm16[i + 1].toInt() shl 8)).toShort()
                energy += value.toDouble() * value
            }
            val rms = kotlin.math.sqrt(energy / (pcm16.size / 2))
            silenceSamples = if (rms < 190.0) silenceSamples + pcm16.size / 2 else 0
            val size = pcm.size()
            if (size >= MAX_SEGMENT_BYTES || (size >= MIN_SEGMENT_BYTES && silenceSamples >= SILENCE_SAMPLES))
                decodeSegment()
        }
    }

    override suspend fun pause() = withContext(Dispatchers.IO) {
        lock.withLock {
            check(sessionId != null && !closed)
            if (!paused) { decodeSegment(); paused = true }
        }
    }

    override suspend fun resume() = withContext(Dispatchers.IO) {
        lock.withLock {
            check(sessionId != null && !closed)
            paused = false
            utteranceStart = acceptedSamples
        }
    }

    override suspend fun flush() = withContext(Dispatchers.IO) {
        lock.withLock { if (sessionId != null && !closed) decodeSegment() }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (sessionId != null && !closed) decodeSegment()
            sessionId = null
            paused = false
        }
    }

    override suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        lock.withLock {
            if (!closed) {
                closed = true
                try { decoder?.release() } finally {
                    decoder = null
                    lease?.close()
                    lease = null
                    sessionId = null
                    eventsChannel.close()
                }
            }
        }
    }

    private suspend fun decodeSegment() {
        val audio = pcm.toByteArray()
        pcm.reset()
        silenceSamples = 0
        val start = utteranceStart
        utteranceStart = acceptedSamples
        if (audio.size < MIN_DECODE_BYTES) return
        val recognizer = checkNotNull(decoder)
        val stream = recognizer.createStream()
        val text = try {
            val samples = FloatArray(audio.size / 2) { index ->
                val i = index * 2
                ((audio[i].toInt() and 255) or (audio[i + 1].toInt() shl 8))
                    .toShort().toFloat() / 32768f
            }
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally { stream.release() }
        if (text.isNotEmpty()) {
            eventsChannel.send(TranscriptEvent(checkNotNull(sessionId), sequence++, false, text,
                start * 1_000L / SAMPLE_RATE,
                acceptedSamples * 1_000L / SAMPLE_RATE))
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SEGMENT_BYTES = 16_000 * 2 * 7
        const val MIN_SEGMENT_BYTES = 16_000 * 2
        const val MIN_DECODE_BYTES = 16_000 // 500 ms
        const val SILENCE_SAMPLES = 16_000L * 7 / 10
    }
}
