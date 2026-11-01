package io.github.vexpaer.openlistener.data

/**
 * Combine adjacent AST provider fragments into complete punctuation-delimited sentences.
 * Raw per-event rows stay in Room for revisions; grouped records are used for reading,
 * copy, PDF/Markdown and ZIP export so late translation updates cannot overwrite neighbours.
 */
object SentenceGrouper {
    private const val MAX_CHARS = 320
    private const val MAX_SPAN_MS = 20_000L
    private val endPunctuation = setOf('。', '！', '？', '.', '!', '?', '…')
    private val closingPunctuation = setOf('，', '。', '！', '？', '；', '：', '、', ',', '.', '!', '?', ';', ':', ')', ']', '}', '”', '’', '…')

    fun isSentenceEnd(text: String): Boolean =
        text.trimEnd().trimEnd('”', '’', '"', '\'').lastOrNull() in endPunctuation

    private fun cjk(ch: Char): Boolean =
        ch in '\u3400'..'\u9fff' || ch in '\u3040'..'\u30ff' || ch in '\uac00'..'\ud7af'

    fun join(left: String?, right: String?): String {
        val a = left.orEmpty()
        val b = right.orEmpty()
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        if (a.last().isWhitespace() || b.first().isWhitespace() || b.first() in closingPunctuation) return a + b
        if (cjk(a.last()) || cjk(b.first())) return a + b
        return "$a $b"
    }

    fun group(segments: List<SegmentEntity>): List<SegmentEntity> {
        if (segments.isEmpty()) return emptyList()
        val result = mutableListOf<SegmentEntity>()
        var merged: SegmentEntity? = null
        for (item in segments.sortedBy { it.sequence }) {
            val current = merged
            if (current == null) {
                merged = item
                continue
            }
            if (isSentenceEnd(current.original) ||
                current.original.length + item.original.length > MAX_CHARS ||
                item.startOffsetMs - current.startOffsetMs > MAX_SPAN_MS) {
                result += current
                merged = item
                continue
            }
            val status = when {
                current.translationStatus == "INCOMPLETE" || item.translationStatus == "INCOMPLETE" -> "INCOMPLETE"
                current.translationStatus == "COMPLETED" && item.translationStatus == "COMPLETED" -> "COMPLETED"
                current.translationStatus == "STREAMING" || item.translationStatus == "STREAMING" -> "STREAMING"
                else -> "PENDING"
            }
            merged = current.copy(
                endOffsetMs = item.endOffsetMs ?: current.endOffsetMs,
                original = join(current.original, item.original),
                translated = join(current.translated, item.translated).ifEmpty { null },
                translationStatus = status,
                revision = maxOf(current.revision, item.revision)
            )
        }
        merged?.let { result += it }
        return result
    }
}
