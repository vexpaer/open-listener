package io.github.vexpaer.openlistener.asr

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Honest local state until a verified sherpa-onnx model is installed. */
class UnavailableLocalAsr : AsrEngine {
    override val capabilities = AsrCapabilities(
        providerId = "sherpa-onnx",
        displayName = "Offline Zipformer",
        streaming = true,
        languages = setOf("en", "zh-CN"),
        uploadsAudio = false
    )
    override val availability = EngineAvailability.NeedsDownload(
        "Download a verified sherpa-onnx streaming model before listening."
    )
    override val events: Flow<TranscriptEvent> = emptyFlow()
    override suspend fun prepare() = availability
    override suspend fun start(sessionId: String) = Unit
    override suspend fun acceptPcm(pcm16: ByteArray) = Unit
    override suspend fun pause() = Unit
    override suspend fun resume() = Unit
    override suspend fun flush() = Unit
    override suspend fun stop() = Unit
    override suspend fun close() = Unit
}
