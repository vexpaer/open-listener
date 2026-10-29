package io.github.vexpaer.openlistener.translation

import io.github.vexpaer.openlistener.asr.TranscriptEvent
import io.github.vexpaer.openlistener.data.SessionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Two bounded workers; a saturated queue leaves persisted PENDING text for an explicit retry. */
class TranslationQueue(private val engine: TranslationEngine, private val repository: SessionRepository,
    private val source: String, private val target: String, scope: CoroutineScope) {
    private val queue = Channel<TranscriptEvent>(64)
    private val workers = List(2) { scope.launch {
        for (event in queue) {
            var result = TranslationResult(event.segmentId, null, "FAILED", "Translation timed out")
            for (attempt in 0..2) {
                result = withTimeoutOrNull(25_000) {
                    engine.translate(event.segmentId, event.text, source, target)
                } ?: result
                if (result.status == "COMPLETED") break
                if (attempt < 2) delay(500L shl attempt)
            }
            repository.updateTranslation(event.sessionId, event.segmentId, result.text, result.status, event.revision)
        }
    } }
    fun offer(event: TranscriptEvent) = queue.trySend(event).isSuccess
    suspend fun drain(timeoutMs: Long = 8_000) {
        queue.close()
        withTimeoutOrNull(timeoutMs) { workers.joinAll() }
        workers.forEach { it.cancelAndJoin() }
    }
    fun cancel() { queue.cancel(); workers.forEach { it.cancel() } }
}
