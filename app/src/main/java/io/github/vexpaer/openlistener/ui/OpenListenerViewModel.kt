package io.github.vexpaer.openlistener.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vexpaer.openlistener.OpenListenerApp
import io.github.vexpaer.openlistener.asr.EngineAvailability
import io.github.vexpaer.openlistener.data.AppPreferences
import io.github.vexpaer.openlistener.data.SegmentEntity
import io.github.vexpaer.openlistener.data.SessionEntity
import io.github.vexpaer.openlistener.export.TranscriptExporter
import io.github.vexpaer.openlistener.translation.MlKitTranslation
import io.github.vexpaer.openlistener.service.MicrophoneRecognitionService
import io.github.vexpaer.openlistener.service.RecognitionRuntime
import io.github.vexpaer.openlistener.updates.GitHubReleaseUpdater
import io.github.vexpaer.openlistener.updates.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

sealed interface UiEffect {
    data object RequestMicrophoneStart : UiEffect
    data class StartActivity(val intent: Intent) : UiEffect
    data class CreateDocument(val format: DocumentFormat, val name: String) : UiEffect
}

enum class DocumentFormat(val extension: String, val mime: String) {
    MARKDOWN("md", "text/markdown"), PDF("pdf", "application/pdf")
}

data class UpdateUiState(
    val checking: Boolean = false,
    val checked: Boolean = false,
    val update: UpdateInfo? = null,
    val downloading: Boolean = false,
    val received: Long = 0,
    val total: Long? = null,
    val file: File? = null,
    val error: String? = null
)

data class EngineCheck(val busy: Boolean = false, val ready: Boolean = false, val detail: String? = null)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class OpenListenerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OpenListenerApp
    private val repository = app.repository
    private val updater = GitHubReleaseUpdater(app)
    val modelCatalog get() = app.models.catalog
    val modelStates = app.models.state
    private val modelJobs = mutableMapOf<String, Job>()
    private val languageManager = MlKitTranslation()
    private val _installedLanguages = MutableStateFlow<Set<String>>(emptySet())
    val installedLanguages = _installedLanguages.asStateFlow()
    private val _languageBusy = MutableStateFlow(false)
    val languageBusy = _languageBusy.asStateFlow()
    private val effectChannel = Channel<UiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    val preferences = app.preferences.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())
    val serviceState = RecognitionRuntime.state
    val activeSessionId = RecognitionRuntime.sessionId
    val level = RecognitionRuntime.level
    val elapsedMs = RecognitionRuntime.elapsedMs
    val serviceError = RecognitionRuntime.error
    val partial = RecognitionRuntime.partial
    val liveSegments = activeSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeSegments(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sessions = repository.observeSessions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val searchQuery = MutableStateFlow("")
    val searchResults = searchQuery.debounce(250).distinctUntilChanged().flatMapLatest { query ->
        repository.previews(query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val selectedId = MutableStateFlow<String?>(null)
    val selectedSession = combine(sessions, selectedId) { all, id -> all.find { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val detailSegments = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeSegments(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _engineCheck = MutableStateFlow(EngineCheck())
    val engineCheck = _engineCheck.asStateFlow()
    private val _updates = MutableStateFlow(UpdateUiState())
    val updates = _updates.asStateFlow()
    private val _retrying = MutableStateFlow<Set<Long>>(emptySet())
    val retrying = _retrying.asStateFlow()
    private var downloadJob: Job? = null
    private var pendingExport: Pair<String, DocumentFormat>? = null
    private var awaitingInstallPermission = false

    init {
        viewModelScope.launch {
            serviceState.collect { state ->
                if (state == MicrophoneRecognitionService.State.FINISHED) {
                    notify("已确认的文字已保存到记录。", "Confirmed text has been saved to Library.")
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) { cleanShareCache() }
    }

    private fun text(zh: String, en: String): String {
        val lang = preferences.value.appLanguage
        return if (lang == "zh" || (lang == "system" && Locale.getDefault().language == "zh")) zh else en
    }
    private fun notify(zh: String, en: String) { _notice.value = text(zh, en) }
    fun showError(error: Throwable) {
        if (error is CancellationException) throw error
        _notice.value = error.message ?: text("操作失败，请重试。", "The operation failed. Please try again.")
    }
    fun clearNotice() { _notice.value = null }

    /** This is a preflight, not the service's PREPARING state. Only the service creates sessions. */
    fun startListening() {
        if (_engineCheck.value.busy || serviceState.value.isSessionActive()) return
        viewModelScope.launch {
            _engineCheck.value = EngineCheck(busy = true)
            try {
                val prefs = preferences.value
                val asr = app.newAsr(prefs)
                val translator = app.newTranslator(prefs)
                val availability = try {
                    val speech = asr.availability
                    if (speech !is EngineAvailability.Ready) speech else translator.prepare()
                } finally {
                    asr.close()
                    translator.close()
                }
                val detail = availability.detailOrNull()
                _engineCheck.value = EngineCheck(ready = availability is EngineAvailability.Ready, detail = detail)
                if (availability is EngineAvailability.Ready) effectChannel.send(UiEffect.RequestMicrophoneStart)
                else _notice.value = detail
            } catch (error: Exception) {
                _engineCheck.value = EngineCheck(detail = error.message)
                showError(error)
            }
        }
    }

    fun microphoneDenied() = notify("需要麦克风权限才能聆听；请允许权限后重试。", "Microphone access is required. Allow it in app permissions and try again.")
    fun activityNotVisible() = notify("请回到应用可见页面后开始聆听。", "Return to the visible app before starting the microphone.")
    fun pauseListening() = serviceAction(MicrophoneRecognitionService.ACTION_PAUSE)
    fun resumeListening() = serviceAction(MicrophoneRecognitionService.ACTION_RESUME)
    fun stopListening() = serviceAction(MicrophoneRecognitionService.ACTION_STOP)
    private fun serviceAction(action: String) {
        if (!serviceState.value.isSessionActive()) return
        try { app.startService(Intent(app, MicrophoneRecognitionService::class.java).setAction(action)) }
        catch (error: Exception) { showError(error) }
    }

    fun setRetainAudio(value: Boolean) = editEnginePreference { app.preferences.setRetainAudio(value) }
    fun setLocalAsrModel(value: String) = editEnginePreference { app.preferences.setLocalAsrModel(value) }
    fun setTheme(value: String) = editPreference { app.preferences.setTheme(value) }
    fun setLanguage(value: String) = editPreference { app.preferences.setLanguage(value) }
    fun setReducedMotion(value: Boolean) = editPreference { app.preferences.setReducedMotion(value) }
    fun setSubtitleFontScale(value: Float) = editPreference { app.preferences.setSubtitleFontScale(value) }
    fun setProviders(asr: String, translation: String) = editEnginePreference { app.preferences.setProviders(asr, translation) }
    fun setLanguages(source: String, target: String) = editEnginePreference { app.preferences.setLanguages(source, target) }
    private fun editPreference(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (error: Exception) { showError(error) }
    }
    private fun editEnginePreference(block: suspend () -> Unit) {
        if (serviceState.value.isSessionActive()) {
            notify("请先结束当前聆听，再切换引擎或语言。", "End the current session before changing engines or languages.")
        } else editPreference { block(); _engineCheck.value = EngineCheck() }
    }

    fun credential(provider: String, field: String): String = app.credentials.get(provider, field).orEmpty()
    fun saveCredentials(provider: String, fields: Map<String, String>) {
        if (serviceState.value.isSessionActive()) return
        try {
            fields.forEach { (field, value) ->
                if (value.isBlank()) app.credentials.remove(provider, field) else app.credentials.set(provider, field, value.trim())
            }
            _engineCheck.value = EngineCheck()
            notify("配置已加密保存在本机；不会自动切换服务商。", "Configuration is encrypted on this device. No provider fallback is used.")
        } catch (error: Exception) { showError(error) }
    }
    fun checkSelectedEngines() {
        if (_engineCheck.value.busy) return
        viewModelScope.launch {
            _engineCheck.value = EngineCheck(busy = true)
            try {
                val speech = app.newAsr(preferences.value)
                val translation = app.newTranslator(preferences.value)
                val result = try {
                    val asr = speech.availability
                    if (asr !is EngineAvailability.Ready) asr else translation.prepare()
                } finally { speech.close(); translation.close() }
                _engineCheck.value = EngineCheck(ready = result is EngineAvailability.Ready,
                    detail = result.detailOrNull() ?: text("所选引擎与语言包已就绪。", "Selected engines and language models are ready."))
            } catch (error: Exception) { _engineCheck.value = EngineCheck(detail = error.message); showError(error) }
        }
    }

    fun downloadModel(id: String) {
        if (modelJobs[id]?.isActive == true || serviceState.value.isSessionActive()) return
        modelJobs[id] = viewModelScope.launch {
            try { app.models.download(id); checkSelectedEngines() } catch (error: Exception) { showError(error) }
        }
    }
    fun cancelModelDownload(id: String) { modelJobs[id]?.cancel() }
    fun deleteModel(id: String) = viewModelScope.launch {
        if (serviceState.value.isSessionActive()) return@launch
        try { app.models.delete(id); checkSelectedEngines() } catch (error: Exception) { showError(error) }
    }
    fun refreshLanguageModels() = viewModelScope.launch {
        try { _installedLanguages.value = languageManager.installed() } catch (error: Exception) { showError(error) }
    }
    fun downloadLanguageModels() {
        if (_languageBusy.value) return
        viewModelScope.launch {
            _languageBusy.value = true
            try {
                languageManager.downloadModels(preferences.value.sourceLanguage, preferences.value.targetLanguage)
                _installedLanguages.value = languageManager.installed()
                checkSelectedEngines()
                notify("所选语言包已安装。", "Selected language models are installed.")
            } catch (error: Exception) { showError(error) }
            finally { _languageBusy.value = false }
        }
    }
    fun deleteLanguageModel(language: String) {
        if (_languageBusy.value || serviceState.value.isSessionActive()) return
        viewModelScope.launch {
            _languageBusy.value = true
            try { languageManager.delete(language); _installedLanguages.value = languageManager.installed(); checkSelectedEngines() }
            catch (error: Exception) { showError(error) }
            finally { _languageBusy.value = false }
        }
    }
    fun openUrl(url: String) = viewModelScope.launch {
        effectChannel.send(UiEffect.StartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
    }

    fun selectSession(id: String?) { selectedId.value = id }
    fun renameSession(id: String, title: String) = viewModelScope.launch {
        val clean = title.trim().take(160)
        if (clean.isEmpty()) return@launch
        try { repository.renameSession(id, clean) } catch (error: Exception) { showError(error) }
    }
    fun deleteSession(id: String) = viewModelScope.launch {
        if (id == activeSessionId.value && serviceState.value.isSessionActive()) {
            notify("不能删除正在聆听的记录。请先结束。", "End this session before deleting its record.")
            return@launch
        }
        try {
            repository.deleteSession(id)
            if (selectedId.value == id) selectedId.value = null
            notify("记录已删除。", "Record deleted.")
        } catch (error: Exception) { showError(error) }
    }
    fun playRecording(id: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val path = repository.getSession(id)?.audioPath ?: return@launch
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", File(path))
            effectChannel.send(UiEffect.StartActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "audio/wav")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
        } catch (error: Exception) { showError(error) }
    }
    fun copyText(value: String) {
        app.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Open Listener", value))
        notify("文字已复制。", "Text copied.")
    }
    fun copySession(id: String) = withSession(id) { session, segments -> copyText(TranscriptExporter.markdown(session, segments)) }
    fun retryTranslation(sessionId: String, sequence: Long) {
        if (sequence in _retrying.value) return
        viewModelScope.launch {
            _retrying.value += sequence
            try {
                val session = repository.getSession(sessionId) ?: return@launch
                val segment = repository.listSegments(sessionId).find { it.sequence == sequence } ?: return@launch
                val translator = app.newTranslator(preferences.value.copy(sourceLanguage = session.sourceLang, targetLanguage = session.targetLang))
                try {
                    val availability = translator.prepare()
                    check(availability is EngineAvailability.Ready) { availability.detailOrNull().orEmpty() }
                    val result = translator.translate(sequence, segment.original, session.sourceLang, session.targetLang)
                    repository.updateTranslation(sessionId, sequence, result.text, result.status, segment.revision)
                    if (result.errorMessage != null) _notice.value = result.errorMessage
                } finally { translator.close() }
            } catch (error: Exception) { showError(error) }
            finally { _retrying.value -= sequence }
        }
    }

    fun requestExport(id: String, format: DocumentFormat) = withSession(id) { session, _ ->
        pendingExport = id to format
        effectChannel.send(UiEffect.CreateDocument(format, safeFileName(session.title) + "." + format.extension))
    }
    fun saveDocument(uri: Uri?) {
        val request = pendingExport.also { pendingExport = null } ?: return
        if (uri == null) return
        withSession(request.first) { session, segments ->
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    when (request.second) {
                        DocumentFormat.MARKDOWN -> output.write(TranscriptExporter.markdown(session, segments).toByteArray(Charsets.UTF_8))
                        DocumentFormat.PDF -> TranscriptExporter.pdf(session, segments, output)
                    }
                } ?: error(text("无法写入所选文件。", "Cannot write to the selected document."))
            }
            notify("文本文档已导出。", "Text document exported.")
        }
    }
    fun shareSession(id: String, format: DocumentFormat) = withSession(id) { session, segments ->
        val file = withContext(Dispatchers.IO) {
            cleanShareCache()
            val directory = File(app.cacheDir, "exports").apply { check(exists() || mkdirs()) }
            File(directory, "${safeFileName(session.title)}-${System.currentTimeMillis()}.${format.extension}").also { target ->
                target.outputStream().use { output ->
                    if (format == DocumentFormat.MARKDOWN) output.write(TranscriptExporter.markdown(session, segments).toByteArray(Charsets.UTF_8))
                    else TranscriptExporter.pdf(session, segments, output)
                }
            }
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Open Listener", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        effectChannel.send(UiEffect.StartActivity(Intent.createChooser(send, text("分享文字记录", "Share text record"))))
        viewModelScope.launch { delay(SHARE_TTL); withContext(Dispatchers.IO) { cleanShareCache() } }
    }
    private fun withSession(id: String, block: suspend (SessionEntity, List<SegmentEntity>) -> Unit) = viewModelScope.launch {
        try {
            val session = repository.getSession(id) ?: error(text("记录已不存在。", "This record no longer exists."))
            block(session, repository.listSegments(id))
        } catch (error: Exception) { showError(error) }
    }
    private fun cleanShareCache() {
        val threshold = System.currentTimeMillis() - SHARE_TTL
        File(app.cacheDir, "exports").listFiles()?.filter { it.isFile && it.lastModified() < threshold }?.forEach { it.delete() }
    }

    fun checkUpdates() {
        if (_updates.value.checking || _updates.value.downloading) return
        viewModelScope.launch {
            _updates.value = UpdateUiState(checking = true)
            try {
                updater.check().fold(
                    onSuccess = { update -> _updates.value = UpdateUiState(checked = true, update = update) },
                    onFailure = { error -> _updates.value = _updates.value.copy(checking = false, error = error.message) }
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _updates.value = _updates.value.copy(checking = false, error = error.message)
            }
        }
    }
    fun downloadUpdate() {
        val update = _updates.value.update ?: return
        if (_updates.value.downloading || serviceState.value.isSessionActive()) return
        downloadJob = viewModelScope.launch {
            _updates.value = _updates.value.copy(downloading = true, error = null, received = 0, total = null, file = null)
            try {
                val file = updater.download(update) { received, total ->
                    _updates.value = _updates.value.copy(received = received, total = total)
                }
                _updates.value = _updates.value.copy(downloading = false, file = file)
            } catch (error: CancellationException) {
                _updates.value = _updates.value.copy(downloading = false, error = text("下载已取消，可重试。", "Download cancelled. You can retry."))
                throw error
            } catch (error: Exception) {
                _updates.value = _updates.value.copy(downloading = false, error = error.message)
            }
        }
    }
    fun cancelUpdateDownload() { downloadJob?.cancel() }
    fun installUpdate() {
        val file = _updates.value.file ?: return
        if (serviceState.value.isSessionActive()) return
        viewModelScope.launch {
            try {
                if (!updater.canRequestPackageInstalls()) {
                    awaitingInstallPermission = true
                    effectChannel.send(UiEffect.StartActivity(updater.unknownSourcesSettingsIntent()))
                } else effectChannel.send(UiEffect.StartActivity(updater.installIntentFile(file)))
            } catch (error: Exception) { showError(error) }
        }
    }
    fun onActivityResumed() {
        if (awaitingInstallPermission && updater.canRequestPackageInstalls()) {
            awaitingInstallPermission = false
            installUpdate()
        }
    }

    companion object {
        private const val SHARE_TTL = 24 * 60 * 60 * 1_000L
        private fun safeFileName(title: String): String = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(80).ifBlank { "OpenListener" }
    }
}

internal fun EngineAvailability.detailOrNull(): String? = when (this) {
    EngineAvailability.Ready -> null
    is EngineAvailability.NeedsDownload -> detail
    is EngineAvailability.NeedsCredentials -> detail
    is EngineAvailability.Unavailable -> detail
}
internal fun MicrophoneRecognitionService.State.isSessionActive(): Boolean = this in setOf(
    MicrophoneRecognitionService.State.PREPARING, MicrophoneRecognitionService.State.RECORDING,
    MicrophoneRecognitionService.State.PAUSED, MicrophoneRecognitionService.State.STOPPING
)
