package io.github.vexpaer.openlistener.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** User-selectable settings. Provider credentials deliberately live in CredentialsStore. */
data class AppPreferences(
    val appLanguage: String = "system",
    val theme: String = "system",
    val reducedMotion: Boolean = false,
    val subtitleFontScale: Float = 1f,
    val sourceLanguage: String = "en",
    val targetLanguage: String = "zh-CN",
    val retainAudio: Boolean = false,
    val localAsrModel: String = io.github.vexpaer.openlistener.models.ModelCatalog.DEFAULT_ID,
    val asrProvider: String = "local",
    val translationProvider: String = "mlkit"
)

class PreferencesStore(private val context: Context) {
    private object Keys {
        val appLanguage = stringPreferencesKey("app_language")
        val theme = stringPreferencesKey("theme")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val subtitleFontScale = floatPreferencesKey("subtitle_font_scale")
        val sourceLanguage = stringPreferencesKey("source_language")
        val targetLanguage = stringPreferencesKey("target_language")
        val retainAudio = booleanPreferencesKey("retain_audio")
        val localAsrModel = stringPreferencesKey("local_asr_model")
        val asrProvider = stringPreferencesKey("asr_provider")
        val translationProvider = stringPreferencesKey("translation_provider")
    }

    val preferences: Flow<AppPreferences> = context.settingsDataStore.data.map { values ->
        AppPreferences(
            appLanguage = values[Keys.appLanguage] ?: "system",
            theme = values[Keys.theme] ?: "system",
            reducedMotion = values[Keys.reducedMotion] ?: false,
            subtitleFontScale = (values[Keys.subtitleFontScale] ?: 1f).coerceIn(.85f, 1.5f),
            sourceLanguage = values[Keys.sourceLanguage] ?: "en",
            targetLanguage = values[Keys.targetLanguage] ?: "zh-CN",
            retainAudio = values[Keys.retainAudio] ?: false,
            localAsrModel = values[Keys.localAsrModel] ?: io.github.vexpaer.openlistener.models.ModelCatalog.DEFAULT_ID,
            asrProvider = values[Keys.asrProvider] ?: "local",
            translationProvider = values[Keys.translationProvider] ?: "mlkit"
        )
    }

    suspend fun setRetainAudio(value: Boolean) = context.settingsDataStore.edit { it[Keys.retainAudio] = value }
    suspend fun setLocalAsrModel(value: String) = context.settingsDataStore.edit { it[Keys.localAsrModel] = value }
    suspend fun setTheme(value: String) = context.settingsDataStore.edit { it[Keys.theme] = value }
    suspend fun setReducedMotion(value: Boolean) = context.settingsDataStore.edit { it[Keys.reducedMotion] = value }
    suspend fun setLanguage(value: String) = context.settingsDataStore.edit { it[Keys.appLanguage] = value }
    suspend fun setSubtitleFontScale(value: Float) = context.settingsDataStore.edit {
        it[Keys.subtitleFontScale] = value.coerceIn(.85f, 1.5f)
    }
    suspend fun setLanguages(source: String, target: String) = context.settingsDataStore.edit {
        it[Keys.sourceLanguage] = source
        it[Keys.targetLanguage] = target
    }
    suspend fun setProviders(asr: String, translation: String) = context.settingsDataStore.edit {
        it[Keys.asrProvider] = asr
        it[Keys.translationProvider] = translation
    }
}
