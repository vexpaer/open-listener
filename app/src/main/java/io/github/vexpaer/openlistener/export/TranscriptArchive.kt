package io.github.vexpaer.openlistener.export

import io.github.vexpaer.openlistener.audio.WavAudio
import io.github.vexpaer.openlistener.data.SessionEntity
import io.github.vexpaer.openlistener.data.SegmentEntity
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Text plus any retained audio, streamed to ZIP without loading recordings into memory. */
object TranscriptArchive {
    data class Record(val session: SessionEntity, val segments: List<SegmentEntity>)

    fun write(records: List<Record>, output: OutputStream, check: () -> Unit = {}) {
        ZipOutputStream(output.buffered()).use { zip ->
            records.forEachIndexed { index, record ->
                check()
                val title = record.session.title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                    .trim().take(70).ifBlank { "record" }
                // A numeric prefix avoids collisions even when titles are identical.
                val folder = "%04d-%s".format(index + 1, title)
                zip.putNextEntry(ZipEntry("$folder/transcript.md"))
                zip.write(TranscriptExporter.markdown(record.session, record.segments).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                val audio = record.session.audioPath?.let(::File)?.takeIf { it.isFile }
                if (audio != null) {
                    // Freeze the length for an active recording so export stays bounded.
                    var remaining = ((audio.length() - 44).coerceAtLeast(0) / 2 * 2)
                    require(remaining <= Int.MAX_VALUE - 44) { "Recording exceeds WAV size limit" }
                    zip.putNextEntry(ZipEntry("$folder/recording.wav"))
                    audio.inputStream().buffered().use { input ->
                        kotlin.check(input.skip(44) == 44L) { "Incomplete WAV header" }
                        zip.write(WavAudio.header(remaining.toInt()))
                        val buffer = ByteArray(32 * 1024)
                        while (remaining > 0) {
                            check()
                            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            if (read < 0) break
                            zip.write(buffer, 0, read); remaining -= read
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
    }
}
