package io.github.vexpaer.openlistener

import io.github.vexpaer.openlistener.data.SegmentEntity
import io.github.vexpaer.openlistener.data.SentenceGrouper
import org.junit.Assert.*
import org.junit.Test

class SentenceGrouperTest {
    private fun row(id: Long, original: String, translated: String? = null, status: String = "COMPLETED") =
        SegmentEntity("session", id, (id - 1) * 1000, id * 1000, original, translated, status, id.toInt())

    @Test fun mergesEnglishFragmentsUntilSentencePunctuation() {
        val grouped = SentenceGrouper.group(listOf(
            row(1, "I think", "我认为"),
            row(2, "we should", "我们应该"),
            row(3, "try again.", "再试一次。"),
            row(4, "Another", "另一"),
            row(5, "sentence!", "句话！")
        ))
        assertEquals(2, grouped.size)
        assertEquals("I think we should try again.", grouped[0].original)
        assertEquals("我认为我们应该再试一次。", grouped[0].translated)
        assertEquals(1L, grouped[0].sequence)
        assertEquals("Another sentence!", grouped[1].original)
    }

    @Test fun keepsChinesePunctuationAndRecalculatesUpdatedTranslation() {
        val raw = listOf(
            row(1, "欢迎", "Welcome", "STREAMING"),
            row(2, "来到这里，", "here,", "STREAMING"),
            row(3, "开始学习。", "let's begin.", "COMPLETED")
        )
        assertEquals("欢迎来到这里，开始学习。", SentenceGrouper.group(raw).single().original)
        assertEquals("Welcome here, let's begin.", SentenceGrouper.group(raw).single().translated)
        assertEquals("STREAMING", SentenceGrouper.group(raw).single().translationStatus)
        val revised = raw.toMutableList().also { it[1] = it[1].copy(translated = "to this place,", revision = 12) }
        assertEquals("Welcome to this place, let's begin.", SentenceGrouper.group(revised).single().translated)
        assertEquals(12, SentenceGrouper.group(revised).single().revision)
    }

    @Test fun boundsLongParagraphAndNeverMergesAcrossTerminalPunctuation() {
        assertEquals(2, SentenceGrouper.group(listOf(row(1, "Hello!"), row(2, "World."))).size)
        assertEquals(2, SentenceGrouper.group(listOf(row(1, "a".repeat(250)), row(2, "b".repeat(100)))).size)
        assertTrue(SentenceGrouper.isSentenceEnd("你好！”"))
        assertFalse(SentenceGrouper.isSentenceEnd("你好，"))
    }
}
