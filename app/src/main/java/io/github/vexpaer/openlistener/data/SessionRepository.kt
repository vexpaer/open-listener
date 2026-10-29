package io.github.vexpaer.openlistener.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SessionRepository(private val sessions: SessionDao, private val segments: SegmentDao) {
    fun observeSessions(): Flow<List<SessionEntity>> = sessions.observeAll()
    fun search(query: String): Flow<List<SessionEntity>> = sessions.search(query.trim())
    fun observeSegments(sessionId: String): Flow<List<SegmentEntity>> = segments.observeForSession(sessionId)
    suspend fun startSession(title: String, sourceLang: String, targetLang: String,
        asrProvider: String, translatorProvider: String, now: Long = System.currentTimeMillis()): String {
        val id = UUID.randomUUID().toString()
        sessions.upsert(SessionEntity(id, title, now, null, 0, sourceLang, targetLang,
            asrProvider, translatorProvider, "PREPARING", now))
        return id
    }
    suspend fun saveFinalSegment(sessionId: String, sequence: Long, startOffsetMs: Long,
        endOffsetMs: Long?, original: String, revision: Int = 0): Boolean = segments.putFinal(
        SegmentEntity(sessionId, sequence, startOffsetMs, endOffsetMs, original, null, "PENDING", revision))
    suspend fun updateTranslation(sessionId: String, sequence: Long, translated: String?, status: String, revision: Int? = null) =
        segments.updateTranslation(sessionId, sequence, translated, status, revision)
    suspend fun finishSession(id: String, state: String, durationMs: Long, endedAt: Long? = System.currentTimeMillis()) {
        sessions.get(id)?.let { sessions.update(it.copy(state = state, durationMs = durationMs, endedAt = endedAt)) }
    }
    suspend fun renameSession(id: String, title: String) { require(title.isNotBlank()); sessions.rename(id, title.trim().take(200)) }
    suspend fun deleteSession(id: String) = sessions.delete(id)
    suspend fun markInterruptedSessions() = sessions.markOrphanedAsInterrupted(System.currentTimeMillis())
    suspend fun getSession(id: String): SessionEntity? = sessions.get(id)
    suspend fun listSegments(id: String): List<SegmentEntity> = segments.listForSession(id)
}
