package io.github.vexpaer.openlistener

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.vexpaer.openlistener.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real in-memory Room, not a production fake database. */
class SessionRepositoryAndroidTest {
    @Test fun revisionsTranslationSearchInterruptionAndCascadeDeletion() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TranscriptDatabase::class.java).build()
        try {
            val repository = SessionRepository(database.sessionDao(), database.segmentDao())
            val id = repository.startSession("课堂", "en", "zh-CN", "local", "mlkit")
            assertEquals("", repository.previews("").first().single().preview)
            repository.saveFinalSegment(id, 2, 1000, 2000, "second", 0)
            repository.saveFinalSegment(id, 1, 0, 1000, "draft", 0)
            repository.saveFinalSegment(id, 1, 0, 1100, "corrected", 1)
            repository.updateTranslation(id, 1, "过期译文", "COMPLETED", 0)
            assertNull(repository.listSegments(id).first().translated)
            repository.updateTranslation(id, 1, "确认译文", "COMPLETED", 1)
            assertEquals(listOf(1L, 2L), repository.listSegments(id).map { it.sequence })
            assertEquals("确认译文", repository.listSegments(id).first().translated)
            assertEquals(id, repository.search("确认").first().single().id)
            val preview = repository.previews("确认").first().single()
            assertEquals(id, preview.session.id)
            assertEquals("corrected", preview.preview)
            repository.saveFinalSegment(id, 1, 0, 1100, "updated preview", 2)
            assertEquals("updated preview", repository.previews("").first().single().preview)
            repository.renameSession(id, "New title")
            assertEquals("New title", repository.getSession(id)!!.title)
            repository.markInterruptedSessions()
            assertEquals("INTERRUPTED", repository.getSession(id)!!.state)
            repository.deleteSession(id)
            assertTrue(repository.listSegments(id).isEmpty())
        } finally { database.close() }
    }
}
