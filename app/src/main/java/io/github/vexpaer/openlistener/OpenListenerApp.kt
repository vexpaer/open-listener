package io.github.vexpaer.openlistener

import android.app.Application
import io.github.vexpaer.openlistener.asr.*
import io.github.vexpaer.openlistener.data.*
import io.github.vexpaer.openlistener.models.ModelStore
import io.github.vexpaer.openlistener.security.CredentialsStore
import io.github.vexpaer.openlistener.translation.*
import kotlinx.coroutines.*

open class OpenListenerApp : Application() {
    val database by lazy { TranscriptDatabase.create(this) }
    val repository by lazy { SessionRepository(database.sessionDao(), database.segmentDao()) }
    val preferences by lazy { PreferencesStore(this) }
    val credentials by lazy { CredentialsStore(this) }
    val models by lazy { ModelStore(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val recovery by lazy { scope.async {
        java.io.File(filesDir, "recordings").listFiles()?.filter { it.extension == "wav" }?.forEach {
            io.github.vexpaer.openlistener.audio.WavAudio.repair(it)
        }
        repository.markInterruptedSessions()
    } }

    override fun onCreate() {
        super.onCreate()
        recovery.start()
        scope.launch {
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
            java.io.File(cacheDir, "exports").listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }
    open fun newCapture(): io.github.vexpaer.openlistener.audio.PcmCapture = io.github.vexpaer.openlistener.audio.AudioPcmCapture()

    open fun newAsr(prefs: AppPreferences): AsrEngine = when (prefs.asrProvider) {
        "local" -> SherpaStreamingAsr(models, prefs.localAsrModel)
        "doubao" -> DoubaoRealtimeAsr(credentials)
        "qwen", "openai", "custom" -> CloudChunkAsr(prefs.asrProvider, credentials, prefs.sourceLanguage)
        else -> error("Unsupported speech provider; select a supported engine in Settings")
    }
    open fun newTranslator(prefs: AppPreferences): TranslationEngine = when (prefs.translationProvider) {
        "mlkit" -> MlKitTranslation(prefs.sourceLanguage, prefs.targetLanguage)
        "doubao" -> CloudTextTranslation(CloudTranslationProvider.DOUBAO_ARK, credentials)
        "qwen" -> CloudTextTranslation(CloudTranslationProvider.QWEN, credentials)
        "openai" -> CloudTextTranslation(CloudTranslationProvider.OPENAI, credentials)
        "custom" -> CloudTextTranslation(CloudTranslationProvider.CUSTOM, credentials)
        else -> error("Unsupported translation provider; select a supported engine in Settings")
    }
}
