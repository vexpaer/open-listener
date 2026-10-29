package io.github.vexpaer.openlistener.translation

import io.github.vexpaer.openlistener.asr.EngineAvailability

 data class TranslationCapabilities(
    val providerId: String,
    val displayName: String,
    val uploadsText: Boolean,
    val supportedLanguages: Set<String>
)

interface TranslationEngine {
    val capabilities: TranslationCapabilities
    val availability: EngineAvailability
    suspend fun prepare(): EngineAvailability
    suspend fun translate(segmentId: Long, text: String, source: String, target: String): TranslationResult
    suspend fun close()
}

data class TranslationResult(
    val segmentId: Long,
    val text: String?,
    val status: String,
    val errorMessage: String? = null
)
