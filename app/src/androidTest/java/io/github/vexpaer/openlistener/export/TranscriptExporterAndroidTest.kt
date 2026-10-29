package io.github.vexpaer.openlistener.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.TextPaint
import androidx.test.core.app.ApplicationProvider
import io.github.vexpaer.openlistener.data.SegmentEntity
import io.github.vexpaer.openlistener.data.SessionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class TranscriptExporterAndroidTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val session = SessionEntity("s", "很长的中文标题 Mixed language 😀 ".repeat(60), 0, null, 7_200_000, "en", "zh", "local", "local", "FINISHED", 0)
    private val text = "中文混排 English: a very long paragraph 👩🏽‍💻👨‍👩‍👧‍👦 😀，需要按实际字宽换行。".repeat(400)
    private val segment = SegmentEntity("s", 0, 0, null, text, text, "DONE", 1)

    @Test fun cjkEnglishAndEmojiLayoutFitsWidthAndDoesNotSplitSurrogatesOrZwj() {
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        assertTrue("System fallback must contain CJK glyphs", paint.hasGlyph("中"))
        val measured = TranscriptExporter.layout(text, paint, TranscriptExporter.CONTENT_WIDTH)
        assertTrue(measured.lineCount > 100)
        for (line in 0 until measured.lineCount) {
            assertTrue("Line $line overflows", measured.getLineWidth(line) <= TranscriptExporter.CONTENT_WIDTH + 1f)
            val start = measured.getLineStart(line)
            if (start > 0 && start < text.length) {
                assertTrue(!Character.isLowSurrogate(text[start]))
                assertTrue(text[start] != '‍' && text[start - 1] != '‍')
            }
        }
    }

    @Test fun longTitleAndParagraphsProduceReadablePagesAtLargeFontAndCloseOutput() = runBlocking {
        val file = File.createTempFile("export-test-", ".pdf", context.cacheDir)
        try {
            val output = TrackingOutput()
            TranscriptExporter.writePdf(session, listOf(segment), output, TranscriptExporter.PdfOptions(2f))
            assertTrue(output.closed)
            file.writeBytes(output.toByteArray())
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertTrue("Long title and bilingual body must paginate", renderer.pageCount > 10)
                    renderer.openPage(0).use { page ->
                        assertEquals(595, page.width)
                        assertEquals(842, page.height)
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            assertTrue("Header must be rendered", containsInk(bitmap, 40, 20, 555, 55))
                            assertTrue("Body must be rendered", containsInk(bitmap, 40, 66, 555, 790))
                            assertTrue("Page number must be rendered", containsInk(bitmap, 200, 800, 395, 841))
                            assertTrue("Content must not overflow right margin", !containsInk(bitmap, 558, 66, 594, 790))
                        } finally { bitmap.recycle() }
                    }
                }
            }
        } finally { file.delete() }
    }

    @Test fun writeFailureClosesPdfDocumentAndOutputAndNextExportStillWorks() {
        val broken = TrackingOutput(fail = true)
        try { TranscriptExporter.pdf(session, emptyList(), broken); throw AssertionError("Expected stream failure") }
        catch (_: IOException) { }
        assertTrue(broken.closed)
        val good = TrackingOutput()
        TranscriptExporter.pdf(session, emptyList(), good)
        assertTrue(good.closed && good.size() > 0)
    }

    @Test fun cancellationPropagatesAndClosesOwnedOutput() = runBlocking {
        val output = TrackingOutput()
        var cancelled = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try { TranscriptExporter.writePdf(session, List(200) { segment.copy(sequence = it.toLong()) }, output) }
            catch (failure: CancellationException) { cancelled = true; throw failure }
        }
        job.cancelAndJoin()
        assertTrue(cancelled)
        assertTrue(output.closed)
    }

    private fun containsInk(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        for (y in top until bottom) for (x in left until right) {
            val color = bitmap.getPixel(x, y)
            if (Color.alpha(color) > 100 && Color.red(color) < 200 && Color.green(color) < 200 && Color.blue(color) < 200) return true
        }
        return false
    }

    private class TrackingOutput(private val fail: Boolean = false) : ByteArrayOutputStream() {
        var closed = false
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (fail) throw IOException("Synthetic SAF failure")
            super.write(buffer, offset, length)
        }
        override fun write(value: Int) {
            if (fail) throw IOException("Synthetic SAF failure")
            super.write(value)
        }
        override fun flush() {
            if (fail) throw IOException("Synthetic SAF flush failure")
            super.flush()
        }
        override fun close() {
            closed = true
            super.close()
            if (fail) throw IOException("Synthetic SAF close failure")
        }
    }
}
