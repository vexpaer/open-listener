package io.github.vexpaer.openlistener

import io.github.vexpaer.openlistener.asr.SegmentAssembler
import io.github.vexpaer.openlistener.asr.TranscriptEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentAssemblerTest {
    @Test
    fun partialIsReplacedByFinalWithoutDuplicate() = runTest {
        val assembler = SegmentAssembler()
        assembler.accept(TranscriptEvent("s", 1, true, "hel", 0, null, 0))
        assembler.accept(TranscriptEvent("s", 1, true, "hello", 0, null, 1))
        assembler.accept(TranscriptEvent("s", 1, false, "hello world", 0, 1000, 2))
        assertEquals(listOf("hello world"), assembler.state.value.map { it.text })
        assertEquals(false, assembler.state.value.single().partial)
    }

    @Test
    fun staleRevisionDoesNotOverwriteNewerText() = runTest {
        val assembler = SegmentAssembler()
        assembler.accept(TranscriptEvent("s", 2, false, "new", 0, 1000, 4))
        assembler.accept(TranscriptEvent("s", 2, false, "old", 0, 900, 3))
        assertEquals("new", assembler.state.value.single().text)
    }
}
