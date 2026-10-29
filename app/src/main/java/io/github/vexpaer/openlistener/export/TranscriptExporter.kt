package io.github.vexpaer.openlistener.export

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import io.github.vexpaer.openlistener.data.SegmentEntity
import io.github.vexpaer.openlistener.data.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object TranscriptExporter {
    /** Plain text is escaped, never interpreted as arbitrary Markdown or embedded HTML. */
    fun markdown(session: SessionEntity, segments: List<SegmentEntity>): String = markdownText(session, segments) { checkThread() }

    /** Takes ownership of output, including on validation failure. Suitable for a SAF stream. */
    fun markdown(session: SessionEntity, segments: List<SegmentEntity>, output: OutputStream) {
        output.use { stream -> stream.write(markdown(session, segments).toByteArray(Charsets.UTF_8)) }
    }

    /** Cancellable IO API; the caller opens an ACTION_CREATE_DOCUMENT URI, not a raw storage path. */
    suspend fun writeMarkdown(session: SessionEntity, segments: List<SegmentEntity>, output: OutputStream) {
        // The outer use also closes the stream if cancellation prevents dispatching the IO block.
        output.use { stream ->
            withContext(Dispatchers.IO) {
                val job = currentCoroutineContext()
                val text = markdownText(session, segments) { job.ensureActive() }
                val bytes = text.toByteArray(Charsets.UTF_8)
                var offset = 0
                while (offset < bytes.size) {
                    job.ensureActive()
                    val count = minOf(8192, bytes.size - offset)
                    stream.write(bytes, offset, count)
                    offset += count
                }
                stream.flush()
            }
        }
    }

    data class PdfOptions(val fontScale: Float = 1f) {
        init { require(fontScale.isFinite() && fontScale in 0.75f..2f) { "PDF fontScale must be between 0.75 and 2" } }
    }

    /** Synchronous compatibility API; call off Main. All output streams/documents are closed. */
    fun pdf(session: SessionEntity, segments: List<SegmentEntity>, output: OutputStream, options: PdfOptions = PdfOptions()) {
        output.use { renderPdf(session, segments, it, options) { checkThread() } }
    }

    /** Preferred coroutine API: cancellation is checked between paragraphs, lines, pages and writes. */
    suspend fun writePdf(session: SessionEntity, segments: List<SegmentEntity>, output: OutputStream, options: PdfOptions = PdfOptions()) {
        output.use { stream ->
            withContext(Dispatchers.IO) {
                val job = currentCoroutineContext()
                renderPdf(session, segments, stream, options) { job.ensureActive() }
            }
        }
    }

    private fun markdownText(session: SessionEntity, segments: List<SegmentEntity>, check: () -> Unit): String = buildString {
        check()
        appendLine("# ${escapeMarkdown(singleLine(session.title))}")
        appendLine()
        appendLine("- Date: ${date(session.startedAt)}")
        appendLine("- Languages: ${escapeMarkdown(singleLine(session.sourceLang))} → ${escapeMarkdown(singleLine(session.targetLang))}")
        appendLine("- Duration: ${max(0, session.durationMs) / 1000}s")
        appendLine("- State: ${escapeMarkdown(singleLine(session.state))}")
        appendLine()
        segments.sortedBy { it.sequence }.forEach { segment ->
            check()
            appendLine("## ${formatOffset(segment.startOffsetMs)}")
            appendLine()
            appendLine(escapeMarkdown(segment.original, check))
            appendLine()
            appendLine(segment.translated?.let { escapeMarkdown(it, check) } ?: "_Translation pending_")
            appendLine()
        }
    }

    internal fun escapeMarkdown(value: String, check: () -> Unit = {}): String = buildString {
        var leadingWhitespace = true
        value.replace("\r\n", "\n").replace('\r', '\n').forEachIndexed { index, char ->
            if (index % 4096 == 0) check()
            when {
                char == '\n' -> { append("  \n"); leadingWhitespace = true }
                leadingWhitespace && char == ' ' -> append("&#32;")
                leadingWhitespace && char == '\t' -> append("&#9;")
                else -> {
                    leadingWhitespace = false
                    when (char) {
                        '&' -> append("&amp;")
                        '<' -> append("&lt;")
                        '>' -> append("&gt;")
                        '\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '|', '~' -> { append('\\'); append(char) }
                        else -> append(char)
                    }
                }
            }
        }
    }

    private fun renderPdf(session: SessionEntity, segments: List<SegmentEntity>, output: OutputStream, options: PdfOptions, check: () -> Unit) {
        val document = PdfDocument()
        var activePage: PdfDocument.Page? = null
        try {
            check()
            val bodyPaint = textPaint(12f * options.fontScale, Color.DKGRAY)
            val titlePaint = textPaint(20f * options.fontScale, Color.BLACK, bold = true)
            val smallPaint = textPaint(9f * options.fontScale, Color.DKGRAY)
            val header = layout("Open Listener · ${date(session.startedAt)}", smallPaint, CONTENT_WIDTH, maxLines = 1)
            var pageNumber = 0
            var y = CONTENT_TOP

            fun startPage() {
                check()
                pageNumber++
                activePage = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                activePage!!.canvas.apply {
                    save()
                    translate(MARGIN.toFloat(), 24f)
                    header.draw(this)
                    restore()
                    drawLine(MARGIN.toFloat(), 52f, (PAGE_WIDTH - MARGIN).toFloat(), 52f, smallPaint)
                    val footer = layout("$pageNumber", smallPaint, CONTENT_WIDTH, alignment = Layout.Alignment.ALIGN_CENTER)
                    save()
                    translate(MARGIN.toFloat(), (PAGE_HEIGHT - 36).toFloat())
                    footer.draw(this)
                    restore()
                }
                y = CONTENT_TOP
            }

            fun finishPage() {
                activePage?.let { document.finishPage(it) }
                activePage = null
            }

            fun paragraph(text: String, paint: TextPaint, gap: Int = 10) {
                check()
                if (activePage == null) startPage()
                // Layout owns shaping and Unicode line boundaries. Never split UTF-8 bytes,
                // surrogate pairs or emoji clusters into independent drawText/chunk calls.
                val measured = layout(normalizeLines(text), paint, CONTENT_WIDTH)
                var first = 0
                while (first < measured.lineCount) {
                    check()
                    val top = measured.getLineTop(first)
                    val firstHeight = measured.getLineBottom(first) - top
                    if (y + firstHeight > CONTENT_BOTTOM && y > CONTENT_TOP) {
                        finishPage()
                        startPage()
                    }
                    require(firstHeight <= CONTENT_BOTTOM - CONTENT_TOP) { "A PDF text line is taller than the printable page" }
                    var end = first + 1
                    while (end < measured.lineCount && y + measured.getLineBottom(end) - top <= CONTENT_BOTTOM) {
                        check()
                        end++
                    }
                    val height = measured.getLineBottom(end - 1) - top
                    activePage!!.canvas.apply {
                        save()
                        try {
                            clipRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + height)
                            translate(MARGIN.toFloat(), (y - top).toFloat())
                            measured.draw(this)
                        } finally {
                            restore()
                        }
                    }
                    y += height
                    first = end
                    if (first < measured.lineCount) {
                        finishPage()
                        startPage()
                    }
                }
                y += gap
            }

            paragraph(session.title, titlePaint, 14)
            paragraph("${date(session.startedAt)} · ${session.sourceLang} → ${session.targetLang}\nDuration: ${max(0, session.durationMs) / 1000}s · ${session.state}", smallPaint, 18)
            segments.sortedBy { it.sequence }.forEach { segment ->
                check()
                paragraph(formatOffset(segment.startOffsetMs), smallPaint, 4)
                paragraph(segment.original, bodyPaint, 6)
                paragraph(segment.translated ?: "Translation pending", bodyPaint, 14)
            }
            finishPage()
            check()
            document.writeTo(object : OutputStream() {
                override fun write(value: Int) { check(); output.write(value) }
                override fun write(buffer: ByteArray, offset: Int, length: Int) { check(); output.write(buffer, offset, length) }
            })
            check()
            output.flush()
        } finally {
            try {
                activePage?.let { document.finishPage(it) }
            } finally {
                document.close()
            }
        }
    }

    private fun textPaint(size: Float, textColor: Int, bold: Boolean = false): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = textColor
        // System sans-serif retains Android's CJK/emoji fallback chain, unlike a bundled Latin-only font.
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        textLocales = android.os.LocaleList(Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH)
    }

    @SuppressLint("WrongConstant")
    internal fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int = Int.MAX_VALUE, alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(true)
            .setLineSpacing(3f, 1.1f)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setMaxLines(maxLines)
            .apply { if (Build.VERSION.SDK_INT >= 28) setUseLineSpacingFromFallbacks(true) }
            .build()

    private fun normalizeLines(value: String): String = value.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ')
    private fun singleLine(value: String): String = normalizeLines(value).replace('\n', ' ')
    private fun date(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
    private fun formatOffset(value: Long): String = max(0, value).let { "%02d:%02d".format(Locale.ROOT, it / 60_000, (it / 1000) % 60) }
    private fun checkThread() { if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException("Export interrupted") }

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40
    internal const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    private const val CONTENT_TOP = 66
    private const val CONTENT_BOTTOM = 790
}
