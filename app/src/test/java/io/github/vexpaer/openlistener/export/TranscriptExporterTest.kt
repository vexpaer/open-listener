package io.github.vexpaer.openlistener.export

import io.github.vexpaer.openlistener.data.SegmentEntity
import io.github.vexpaer.openlistener.data.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class TranscriptExporterTest {
    private val session = SessionEntity("session", "标题 # [链接]\n下一行", 0, null, 7_200_000, "en", "zh", "local", "local", "FINISHED", 0)
    private fun segment(sequence: Long, text: String, translated: String? = null) = SegmentEntity("session", sequence, sequence * 1000, null, text, translated, "PENDING", 1)

    @Test fun markdownEscapesHtmlLinksFormattingAndIndentedCodeWithoutDamagingUnicode() {
        val escaped = TranscriptExporter.escapeMarkdown("    # 中文 *English* [x](https://example.test) <script>&\n👩🏽‍💻😀 _译文_ | a\\b")
        assertTrue(escaped.startsWith("&#32;&#32;&#32;&#32;\\# 中文 \\*English\\* \\[x\\]\\(https://example\\.test\\)"))
        assertTrue(escaped.contains("&lt;script&gt;&amp;"))
        assertTrue(escaped.contains("  \n👩🏽‍💻😀 \\_译文\\_"))
        assertFalse(escaped.contains("<script>"))
    }

    @Test fun markdownIncludesMetadataSortsSegmentsAndShowsMissingTranslation() {
        val result = TranscriptExporter.markdown(session, listOf(segment(2, "second", "第二"), segment(1, "first")))
        assertTrue(result.startsWith("# 标题 \\# \\[链接\\] 下一行\n"))
        assertTrue(result.contains("- Languages: en → zh"))
        assertTrue(result.contains("- Duration: 7200s"))
        assertTrue(result.contains("## 00:01"))
        assertTrue(result.indexOf("first") < result.indexOf("second"))
        assertTrue(result.contains("_Translation pending_"))
        assertTrue(result.contains("第二"))
    }

    @Test fun streamExportUsesUtf8AndClosesOnSuccessAndWriteFailure() {
        val output = TrackingOutput()
        TranscriptExporter.markdown(session, listOf(segment(1, "中文😀")), output)
        assertTrue(output.closed)
        assertTrue(output.toString("UTF-8").contains("中文😀"))
        val broken = TrackingOutput(fail = true)
        try { TranscriptExporter.markdown(session, emptyList(), broken); throw AssertionError("Expected I/O error") }
        catch (_: java.io.IOException) { }
        assertTrue(broken.closed)
    }

    @Test fun longTextAndEmojiAreNotCharacterCountChunked() {
        val text = "中英👨‍👩‍👧‍👦混排 English ".repeat(1000)
        val markdown = TranscriptExporter.markdown(session, listOf(segment(1, text, text)))
        assertEquals(2, Regex(Regex.escape(text)).findAll(markdown).count())
    }

    private class TrackingOutput(private val fail: Boolean = false) : ByteArrayOutputStream() {
        var closed = false
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (fail) throw java.io.IOException("Synthetic output failure")
            super.write(buffer, offset, length)
        }
        override fun close() { closed = true; super.close() }
    }
}
