package io.github.vexpaer.openlistener.asr

import kotlinx.coroutines.flow.Flow

sealed interface EngineAvailability {
    data object Ready : EngineAvailability
    data class NeedsDownload(val detail: String) : EngineAvailability
    data class NeedsCredentials(val detail: String) : EngineAvailability
    data class Unavailable(val detail: String) : EngineAvailability
}

data class AsrCapabilities(
    val providerId: String,
    val displayName: String,
    val streaming: Boolean,
    val languages: Set<String>,
    val uploadsAudio: Boolean
)

data class TranscriptEvent(
    val sessionId: String,
    val segmentId: Long,
    val partial: Boolean,
    val text: String,
    val startOffsetMs: Long,
    val endOffsetMs: Long?,
    val revision: Int = 0
)

interface AsrEngine {
    val capabilities: AsrCapabilities
    val availability: EngineAvailability
    val events: Flow<TranscriptEvent>
    suspend fun prepare(): EngineAvailability
    suspend fun start(sessionId: String)
    suspend fun acceptPcm(pcm16: ByteArray)
    suspend fun pause()
    suspend fun resume()
    suspend fun flush()
    suspend fun stop()
    suspend fun close()
}
