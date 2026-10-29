package io.github.vexpaer.openlistener.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/** One producer and consumer; no audio file or replay queue. Slow consumers cause an explicit failure. */
interface PcmCapture {
    val audioFrames: Channel<ByteArray>
    suspend fun start(scope: CoroutineScope, onLevel: (Float) -> Unit, onError: (Throwable) -> Unit)
    suspend fun stop()
}

class AudioPcmCapture : PcmCapture {
    override val audioFrames = Channel<ByteArray>(8)
    private var recorder: AudioRecord? = null
    private var job: Job? = null

    @SuppressLint("MissingPermission")
    override suspend fun start(scope: CoroutineScope, onLevel: (Float) -> Unit, onError: (Throwable) -> Unit) = withContext(Dispatchers.IO) {
        check(recorder == null) { "Microphone already active" }
        val minimum = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "16 kHz mono PCM is unsupported on this device" }
        val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16_000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6_400))
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            error("Microphone could not be initialized")
        }
        recorder = audio
        try { audio.startRecording() } catch (e: Exception) { audio.release(); recorder = null; throw e }
        job = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(3_200)
            try {
                while (isActive) {
                    val count = audio.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (!isActive) break
                    check(count > 0) { "Microphone interrupted ($count); return to the app and start again" }
                    var sum = 0.0
                    for (i in 0 until count - 1 step 2) {
                        val sample = ((buffer[i].toInt() and 255) or (buffer[i + 1].toInt() shl 8)).toShort() / 32768.0
                        sum += sample * sample
                    }
                    onLevel(sqrt(sum / (count / 2)).toFloat())
                    check(audioFrames.trySend(buffer.copyOf(count)).isSuccess) {
                        "Recognition cannot keep up; audio was not buffered to disk. Restart listening."
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { onError(e) }
        }
    }

    override suspend fun stop(): Unit = withContext(Dispatchers.IO) {
        val recording = recorder
        job?.cancel()
        try {
            recording?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() }
        } finally {
            job?.join()
            job = null
            recording?.release()
            recorder = null
        }
    }
}
