package io.github.vexpaer.openlistener

import io.github.vexpaer.openlistener.asr.TranscriptEvent
import io.github.vexpaer.openlistener.data.SegmentEntity
import io.github.vexpaer.openlistener.data.SessionEntity
import io.github.vexpaer.openlistener.data.SegmentDao
import io.github.vexpaer.openlistener.data.SessionDao
import io.github.vexpaer.openlistener.data.SessionRepository
import io.github.vexpaer.openlistener.translation.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationQueueTest {
    @Test fun failedTranslationIsRetriedAndWrittenWithRevision() = runBlocking {
        var calls = 0
        val engine = object : TranslationEngine {
            override val capabilities = TranslationCapabilities("test", "Test", false, setOf("en", "zh-CN"))
            override val availability = io.github.vexpaer.openlistener.asr.EngineAvailability.Ready
            override suspend fun prepare() = availability
            override suspend fun translate(segmentId: Long, text: String, source: String, target: String): TranslationResult {
                calls++
                return if (calls < 2) TranslationResult(segmentId, null, "FAILED") else TranslationResult(segmentId, "译文", "COMPLETED")
            }
            override suspend fun close() = Unit
        }
        val writes = mutableListOf<Pair<Long, String?>>()
        val segmentDao = FakeSegmentDao(writes)
        val repository = SessionRepository(FakeSessionDao(), segmentDao)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val queue = TranslationQueue(engine, repository, "en", "zh-CN", scope)
        assertEquals(true, queue.offer(TranscriptEvent("s", 4, false, "hello", 0, 1000, 2)))
        queue.drain(5_000)
        assertEquals(2, calls)
        assertEquals(listOf(4L to "译文"), writes)
        assertEquals(listOf(2), segmentDao.revisions)
    }

    private class FakeSessionDao : SessionDao {
        override suspend fun upsert(session: SessionEntity) = Unit
        override suspend fun update(session: SessionEntity) = Unit
        override fun observeAll() = kotlinx.coroutines.flow.emptyFlow<List<SessionEntity>>()
        override suspend fun get(id: String): SessionEntity? = null
        override fun search(query: String) = kotlinx.coroutines.flow.emptyFlow<List<SessionEntity>>()
        override suspend fun rename(id: String, title: String) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun markOrphanedAsInterrupted(now: Long) = Unit
    }
    private class FakeSegmentDao(private val writes: MutableList<Pair<Long, String?>>) : SegmentDao() {
        val revisions = mutableListOf<Int?>()
        override suspend fun insert(segment: SegmentEntity) = Unit
        override suspend fun update(segment: SegmentEntity) = Unit
        override suspend fun get(sessionId: String, sequence: Long): SegmentEntity? = null
        override fun observeForSession(sessionId: String) = kotlinx.coroutines.flow.emptyFlow<List<SegmentEntity>>()
        override suspend fun listForSession(sessionId: String) = emptyList<SegmentEntity>()
        override suspend fun updateTranslation(sessionId: String, sequence: Long, translated: String?, status: String, revision: Int?) { writes += sequence to translated; revisions += revision }
        override suspend fun deleteForSession(sessionId: String) = Unit
    }
}
