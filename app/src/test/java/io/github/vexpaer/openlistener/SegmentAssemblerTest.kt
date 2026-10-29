package io.github.vexpaer.openlistener

import io.github.vexpaer.openlistener.asr.SegmentAssembler
import io.github.vexpaer.openlistener.asr.TranscriptEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentAssemblerTest {
    @Test fun partialBecomesFinalWithoutDuplicate() {
        val assembler = SegmentAssembler()
        assembler.accept(TranscriptEvent("s", 1, true, "hel", 0, null, 0))
        assembler.accept(TranscriptEvent("s", 1, true, "hello", 0, null, 1))
        assembler.accept(TranscriptEvent("s", 1, false, "hello world", 0, 1000, 2))
        assertEquals(listOf("hello world"), assembler.state.value.map { it.text })
        assertTrue(!assembler.state.value.single().partial)
    }

    @Test fun blankFinalClearsVisiblePartial() {
        val assembler = SegmentAssembler()
        assembler.accept(TranscriptEvent("s", 2, true, "draft", 0, null, 0))
        assembler.accept(TranscriptEvent("s", 2, false, "", 0, 1000, 1))
        assertTrue(assembler.state.value.isEmpty())
    }

    @Test fun staleRevisionDoesNotOverwriteConfirmedText() {
        val assembler = SegmentAssembler()
        assembler.accept(TranscriptEvent("s", 3, false, "new", 0, 1000, 4))
        assembler.accept(TranscriptEvent("s", 3, false, "old", 0, 900, 3))
        assertEquals("new", assembler.state.value.single().text)
    }
}
