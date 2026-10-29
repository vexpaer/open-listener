package io.github.vexpaer.openlistener.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import io.github.vexpaer.openlistener.asr.EngineAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** ML Kit is a proprietary Google SDK; model download is an explicit user operation. */
class MlKitTranslation(private val source: String = "en", private val target: String = "zh-CN") : TranslationEngine {
    private val manager = RemoteModelManager.getInstance()
    private val mutex = Mutex()
    private var translator = Translation.getClient(TranslatorOptions.Builder()
        .setSourceLanguage(normalize(source)).setTargetLanguage(normalize(target)).build())
    override val capabilities = TranslationCapabilities("mlkit", "ML Kit · on-device · proprietary",
        false, TranslateLanguage.getAllLanguages().toSet())
    override var availability: EngineAvailability = EngineAvailability.NeedsDownload("Download translation language models in Settings")
        private set

    override suspend fun prepare(): EngineAvailability {
        val required = setOf(normalize(source), normalize(target)) - TranslateLanguage.ENGLISH
        val present = installed()
        availability = if (normalize(source) == normalize(target) || present.containsAll(required)) EngineAvailability.Ready
        else EngineAvailability.NeedsDownload("Download ${required.minus(present).joinToString()} translation models in Settings")
        return availability
    }

    suspend fun downloadModels(source: String, target: String) {
        for (code in (setOf(normalize(source), normalize(target)) - TranslateLanguage.ENGLISH)) {
            manager.download(TranslateRemoteModel.Builder(code).build(), DownloadConditions.Builder().build()).await()
        }
        prepare()
    }

    suspend fun installed(): Set<String> = manager.getDownloadedModels(TranslateRemoteModel::class.java)
        .await().map { it.language }.toSet()

    suspend fun delete(language: String) {
        manager.deleteDownloadedModel(TranslateRemoteModel.Builder(normalize(language)).build()).await()
        prepare()
    }

    override suspend fun translate(segmentId: Long, text: String, source: String, target: String): TranslationResult = mutex.withLock {
        try {
            require(normalize(source) == normalize(this.source) && normalize(target) == normalize(this.target)) {
                "Language direction changed; start a new listening session"
            }
            if (normalize(source) == normalize(target)) return@withLock TranslationResult(segmentId, text, "COMPLETED")
            check(prepare() == EngineAvailability.Ready) { "Translation language model is missing; download it in Settings" }
            TranslationResult(segmentId, translator.translate(text).await(), "COMPLETED")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { TranslationResult(segmentId, null, "FAILED", e.message ?: "On-device translation failed") }
    }

    override suspend fun close() { translator.close() }

    companion object {
        fun normalize(language: String): String = when (language.lowercase()) {
            "zh", "zh-cn", "zh_hans" -> TranslateLanguage.CHINESE
            "en", "en-us", "en-gb" -> TranslateLanguage.ENGLISH
            else -> TranslateLanguage.fromLanguageTag(language) ?: error("Unsupported translation language: $language")
        }
    }
}
