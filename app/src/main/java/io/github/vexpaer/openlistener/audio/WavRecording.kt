package io.github.vexpaer.openlistener.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** PCM16, mono, 16 kHz. Header can be repaired from file length after process death. */
object WavAudio {
    fun header(size: Int): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + size); put("WAVEfmt ".toByteArray()); putInt(16)
        putShort(1); putShort(1); putInt(16000); putInt(32000); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(size)
    }.array()
    fun encode(pcm: ByteArray): ByteArray = header(pcm.size) + pcm
    fun repair(file: File) {
        if (file.length() < 44) { file.delete(); return }
        RandomAccessFile(file, "rw").use { it.write(header((it.length() - 44).toInt())); it.fd.sync() }
    }
}

class WavRecording(val file: File) : AutoCloseable {
    private val output = RandomAccessFile(file, "rw").apply { setLength(0); write(WavAudio.header(0)) }
    @Synchronized fun append(pcm: ByteArray) {
        check(output.length() + pcm.size < Int.MAX_VALUE) { "Recording has reached the WAV size limit" }
        output.write(pcm)
    }
    @Synchronized override fun close() {
        output.use { it.seek(0); it.write(WavAudio.header((it.length() - 44).toInt())); it.fd.sync() }
    }
}
