package io.github.vexpaer.openlistener.export

import io.github.vexpaer.openlistener.audio.WavAudio
import io.github.vexpaer.openlistener.data.SessionEntity
import io.github.vexpaer.openlistener.data.SegmentEntity
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

class TranscriptArchiveTest {
    @Test fun archivesAllRecordsWithSafeUniquePathsUnicodeAndPlayableAudioSnapshot() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val recording = File.createTempFile("listener-archive", ".wav")
        try {
            // An active recording's header is unfinished until the service closes it.
            recording.writeBytes(WavAudio.header(0) + pcm)
            val session = SessionEntity("one", "../同名/标题", 0, null, 0, "en", "zh", "local", "mlkit", "RECORDING", 0, recording.path)
            val segment = SegmentEntity("one", 0, 0, null, "English", "中文", "COMPLETED", 0)
            val records = listOf(TranscriptArchive.Record(session, listOf(segment)),
                TranscriptArchive.Record(session.copy(id = "two", audioPath = null), emptyList()))
            val output = ByteArrayOutputStream()
            TranscriptArchive.write(records, output)
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(output.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    assertFalse(entry.name.startsWith("/"))
                    assertFalse(entry.name.split('/').contains(".."))
                    assertNull(entries.put(entry.name, zip.readBytes()))
                }
            }
            assertEquals(3, entries.size)
            val texts = entries.filterKeys { it.endsWith(".md") }.values
            assertEquals(2, texts.size)
            assertTrue(texts.first().toString(Charsets.UTF_8).contains("中文"))
            val wav = entries.values.single { it.size == 48 }
            assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
            assertEquals(pcm.size, ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int)
            assertEquals(0, ByteBuffer.wrap(recording.readBytes(), 40, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        } finally { recording.delete() }
    }
}
