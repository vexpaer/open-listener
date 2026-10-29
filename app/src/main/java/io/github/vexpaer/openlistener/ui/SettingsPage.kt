package io.github.vexpaer.openlistener.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.vexpaer.openlistener.BuildConfig
import io.github.vexpaer.openlistener.data.AppPreferences
import io.github.vexpaer.openlistener.models.ModelPhase
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsPage(model: OpenListenerViewModel) {
    val prefs by model.preferences.collectAsState()
    val state by model.serviceState.collectAsState()
    val check by model.engineCheck.collectAsState()
    val updates by model.updates.collectAsState()
    val installed by model.installedLanguages.collectAsState()
    val languageBusy by model.languageBusy.collectAsState()
    val modelStates by model.modelStates.collectAsState()
    val editingAllowed = !state.isSessionActive()
    val list = rememberLazyListState()
    var credentialsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteModel by rememberSaveable { mutableStateOf<String?>(null) }
    var font by remember(prefs.subtitleFontScale) { mutableFloatStateOf(prefs.subtitleFontScale) }
    LaunchedEffect(Unit) { model.refreshLanguageModels() }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("settings-page"),
        verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            SettingsSection(tr("使用体验", "Experience"), Icons.Outlined.WbSunny) {
                SettingChoice(tr("主题", "Appearance"), listOf("system" to tr("跟随系统", "System"), "light" to tr("浅色", "Light"), "dark" to tr("深色", "Dark")), prefs.theme, model::setTheme)
                SettingChoice(tr("界面语言", "App language"), listOf("system" to tr("跟随系统", "System"), "zh" to "简体中文", "en" to "English"), prefs.appLanguage, model::setLanguage)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("减少动态效果", "Reduce motion"))
                        Hint(tr("关闭天空漂移与过渡动画", "Stop sky drift and animated transitions"))
                    }
                    Switch(prefs.reducedMotion, model::setReducedMotion, modifier = Modifier.testTag("reduced-motion"))
                }
                Text("${tr("字幕字号", "Subtitle size")} · ${(font * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(font, { font = it }, valueRange = .85f..1.5f, steps = 12,
                    onValueChangeFinished = { model.setSubtitleFontScale(font) }, modifier = Modifier.testTag("subtitle-size"))
                Hint(tr("在系统字体缩放的基础上调整。", "Applied in addition to the system font size."))
                LanguageChoice(tr("语音源语言", "Spoken language"), prefs.sourceLanguage, editingAllowed) { model.setLanguages(it, prefs.targetLanguage) }
                LanguageChoice(tr("翻译目标语言", "Translation language"), prefs.targetLanguage, editingAllowed) { model.setLanguages(prefs.sourceLanguage, it) }
                if (!editingAllowed) Hint(tr("引擎与语言在当前聆听结束后可修改。", "You can change engines and languages after ending this session."))
            }
        }
        item {
            SettingsSection(tr("语音识别", "Speech recognition"), Icons.Outlined.Mic) {
                SettingChoice(tr("音频交给谁识别", "Who receives the audio"), listOf("local" to tr("设备端", "On-device"), "doubao" to tr("豆包流式", "Doubao streaming")),
                    prefs.asrProvider, enabled = editingAllowed) { model.setProviders(it, prefs.translationProvider) }
                Hint(if (prefs.asrProvider == "local") tr("sherpa-onnx · 真流式 · 中英双语 · 音频不上传", "sherpa-onnx · Streaming · Chinese / English · No audio upload")
                    else tr("豆包语音 2.0 · WebSocket 流式；实时音频会发往火山引擎。", "Doubao Speech 2.0 · WebSocket streaming. Live audio is sent to Volcengine."))
                if (prefs.asrProvider == "doubao") {
                    OutlinedButton(onClick = { credentialsFor = "doubao" }, enabled = editingAllowed, modifier = Modifier.testTag("asr-credentials")) {
                        Icon(Icons.Outlined.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("配置豆包 API", "Configure Doubao API"))
                    }
                    Hint("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(false, onClick = {}, enabled = false, label = { Text(tr("阿里云 ASR · 未实现", "Aliyun ASR · Not implemented")) })
                }
                FilterChip(false, onClick = {}, enabled = false, label = { Text(tr("OpenAI ASR · 未实现", "OpenAI ASR · Not implemented")) })
                OutlinedButton(onClick = model::checkSelectedEngines, enabled = !check.busy) {
                    Text(if (check.busy) tr("检查中…", "Checking…") else tr("检查所选配置", "Check selected setup"))
                }
                check.detail?.let { Hint(it) }
                Hint(tr("配置检查不发送音频、不发起计费请求，也不保证账户额度。", "Setup checks send no audio or billable requests; they do not verify account credit."))
            }
        }
        item { Text(tr("离线语音模型", "Offline speech models"), style = MaterialTheme.typography.titleLarge) }
        model.modelCatalog.forEach { manifest ->
            item(key = "model-${manifest.id}") {
                ReadingSurface {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(manifest.displayName, style = MaterialTheme.typography.titleMedium)
                        Hint("${manifest.languages.joinToString(" / ")} · ${if (manifest.streaming) tr("真流式", "Streaming") else tr("分段识别", "Segmented")} · ${bytes(manifest.bytes)}")
                        Hint("${manifest.license} · ${manifest.version}")
                        val status = modelStates[manifest.id]
                        val phase = status?.phase ?: ModelPhase.NOT_INSTALLED
                        Text(phaseLabel(phase), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (phase == ModelPhase.DOWNLOADING) {
                            val downloaded = status?.downloadedBytes ?: 0
                            val total = status?.totalBytes ?: manifest.bytes
                            LinearProgressIndicator(progress = { if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                            Hint("${bytes(downloaded)} / ${bytes(total)}")
                            TextButton(onClick = { model.cancelModelDownload(manifest.id) }) { Text(tr("取消下载", "Cancel download")) }
                        } else if (phase in listOf(ModelPhase.VERIFYING, ModelPhase.EXTRACTING, ModelPhase.VALIDATING)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else if (phase == ModelPhase.INSTALLED) {
                            OutlinedButton(onClick = { deleteModel = manifest.id }, enabled = editingAllowed && status?.inUse != true) { Text(tr("删除模型", "Delete model")) }
                        } else {
                            OutlinedButton(onClick = { model.downloadModel(manifest.id) }, enabled = editingAllowed,
                                modifier = Modifier.testTag("download-asr-model")) { Text(if (phase == ModelPhase.ERROR) tr("重试下载", "Retry download") else tr("下载模型", "Download model")) }
                        }
                        status?.error?.let { ErrorText(it) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { model.openUrl(manifest.sourceUrl) }) { Text(tr("来源", "Source")) }
                            TextButton(onClick = { model.openUrl(manifest.licenseUrl) }) { Text(tr("许可证", "License")) }
                        }
                        Hint(tr("下载到本机私有目录，验证 SHA-256 后才可使用。", "Stored privately on this device and verified with SHA-256 before use."))
                    }
                }
            }
        }
        item {
            SettingsSection(tr("文字翻译", "Translation"), Icons.Outlined.Language) {
                SettingChoice(tr("与语音识别独立选择", "Independent of speech recognition"), listOf(
                    "mlkit" to tr("设备端 ML Kit", "On-device ML Kit"), "doubao" to tr("豆包方舟", "Doubao Ark"),
                    "qwen" to "Qwen", "openai" to "OpenAI", "custom" to tr("自定义", "Custom")),
                    prefs.translationProvider, enabled = editingAllowed) { model.setProviders(prefs.asrProvider, it) }
                if (prefs.translationProvider == "mlkit") {
                    Hint(tr("Google ML Kit · 第三方非开源 SDK。译文在设备端生成；首次需联网下载语言包，不会自动下载或回退云端。", "Google ML Kit is a third-party, non-open-source SDK. Translation runs on-device. Download language models explicitly; no automatic download or cloud fallback."))
                    OutlinedButton(onClick = model::downloadLanguageModels, enabled = !languageBusy && editingAllowed,
                        modifier = Modifier.testTag("download-language-models")) { Text(if (languageBusy) tr("处理中…", "Working…") else tr("下载所选语言包", "Download selected languages")) }
                    if (languageBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (installed.isEmpty()) Hint(tr("尚无已安装的翻译语言包（英语通常无需独立包）。", "No downloaded translation models. English may not require a separate model."))
                    installed.sorted().forEach { language ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${languageName(language)} · ${tr("已安装", "Installed")}", modifier = Modifier.weight(1f))
                            IconButton(onClick = { deleteLanguage = language }, enabled = !languageBusy && editingAllowed) {
                                Icon(Icons.Outlined.DeleteOutline, "${tr("删除语言包", "Delete language model")} $language")
                            }
                        }
                    }
                    TextButton(onClick = model::refreshLanguageModels, enabled = !languageBusy) { Text(tr("刷新已安装语言", "Refresh installed languages")) }
                } else {
                    Hint(tr("只上传已确认文字到所选服务商；密钥与语音识别独立，没有隐式回退。", "Only confirmed text is sent to the selected provider. Translation keys are separate from speech keys; no implicit fallback."))
                    OutlinedButton(onClick = { credentialsFor = "translation_${prefs.translationProvider}" }, enabled = editingAllowed,
                        modifier = Modifier.testTag("translation-credentials")) { Text(tr("配置翻译 API", "Configure translation API")) }
                }
            }
        }
        item {
            SettingsSection(tr("隐私与存储", "Privacy & storage"), Icons.Outlined.Lock) {
                Text(tr("音频从不保存", "Audio is never saved"), style = MaterialTheme.typography.titleMedium)
                Hint(tr("PCM 只经过有界内存；本机仅保留确认后的文字、偏好和下载的模型。导出分享缓存 24 小时后清理。", "PCM passes through bounded memory only. This device keeps confirmed text, preferences and downloaded models. Share documents expire after 24 hours."))
                Text(dataPath(prefs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Hint(tr("息屏后识别由前台服务继续；系统或厂商强制停止可能中断，但已确认文字不会因此删除。", "The foreground service continues while the screen is off. OS or manufacturer restrictions may interrupt it; confirmed text remains."))
                TextButton(onClick = { model.openUrl("https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPOSITORY}/blob/main/PRIVACY.md") }) { Text(tr("阅读隐私说明", "Privacy policy")) }
            }
        }
        item {
            SettingsSection(tr("更新与关于", "Updates & about"), Icons.Outlined.SystemUpdate) {
                Text("Open Listener  v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = model::checkUpdates, enabled = !updates.checking && !updates.downloading,
                        modifier = Modifier.testTag("check-updates")) { Text(if (updates.checking) tr("检查中…", "Checking…") else tr("检查新版本", "Check for updates")) }
                    OutlinedButton(onClick = model::downloadUpdate, enabled = updates.update != null && !updates.downloading && !updates.checking && editingAllowed && updates.file == null,
                        modifier = Modifier.testTag("download-update")) { Text(tr("下载新版本", "Download update")) }
                }
                when {
                    updates.checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    updates.error != null -> ErrorText(updates.error.orEmpty())
                    updates.checked && updates.update == null -> Text(tr("已是最新版本。", "You're up to date."), style = MaterialTheme.typography.bodyMedium)
                    updates.update != null -> {
                        Text("${tr("可用版本", "Available")}: ${updates.update?.versionName}", style = MaterialTheme.typography.labelLarge)
                        updates.update?.notes?.takeIf { it.isNotBlank() }?.let { notes -> Text(notes, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (updates.downloading) {
                    val total = updates.total
                    if (total != null && total > 0) LinearProgressIndicator(progress = { (updates.received.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Hint("${bytes(updates.received)} / ${total?.let(::bytes) ?: "—"}")
                    TextButton(onClick = model::cancelUpdateDownload) { Text(tr("取消下载", "Cancel download")) }
                }
                if (updates.file != null) {
                    Button(onClick = model::installUpdate, enabled = editingAllowed, modifier = Modifier.testTag("install-update")) { Text(tr("交给系统安装", "Install with Android")) }
                    Hint(tr("安装需要系统确认，可能需允许此来源安装。不会静默升级。", "Android will ask you to confirm installation and may request permission for this source. No silent update."))
                }
                if (!editingAllowed) Hint(tr("更新下载与安装在本次聆听结束后可用。", "Update download and installation are available after this session ends."))
                TextButton(onClick = { model.openUrl("https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPOSITORY}") }) { Text(tr("开源代码 · Apache-2.0", "Source code · Apache-2.0")) }
            }
        }
    }
    credentialsFor?.let { provider -> CredentialsDialog(provider, model, editingAllowed) { credentialsFor = null } }
    deleteLanguage?.let { language ->
        ConfirmDelete(tr("删除语言包？", "Delete language model?"), tr("之后使用该语言翻译，需要重新下载。", "You'll need to download it again to translate this language."),
            onDismiss = { deleteLanguage = null }, onConfirm = { model.deleteLanguageModel(language); deleteLanguage = null })
    }
    deleteModel?.let { id ->
        ConfirmDelete(tr("删除离线语音模型？", "Delete offline speech model?"), tr("文字记录不受影响。本地识别需重新下载模型。", "Text records are unaffected. On-device speech will require another model download."),
            onDismiss = { deleteModel = null }, onConfirm = { model.deleteModel(id); deleteModel = null })
    }
}

@Composable
private fun CredentialsDialog(provider: String, model: OpenListenerViewModel, enabled: Boolean, onDismiss: () -> Unit) {
    val asr = provider == "doubao"
    val defaultBase = when (provider) {
        "translation_doubao" -> "https://ark.cn-beijing.volces.com/api/v3"
        "translation_qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
        "translation_openai" -> "https://api.openai.com/v1"
        else -> ""
    }
    val defaultModel = when (provider) { "translation_qwen" -> "qwen-plus"; "translation_openai" -> "gpt-4.1-mini"; else -> "" }
    // Deliberately not rememberSaveable: secrets must never enter Activity saved-state bundles.
    var key by remember(provider) { mutableStateOf(model.credential(provider, "api_key")) }
    var resource by remember(provider) { mutableStateOf(model.credential(provider, "resource_id")) }
    var base by remember(provider) { mutableStateOf(model.credential(provider, "base_url").ifBlank { defaultBase }) }
    var modelId by remember(provider) { mutableStateOf(model.credential(provider, "model").ifBlank { defaultModel }) }
    val valid = key.isNotBlank() && if (asr) resource.isNotBlank() else base.startsWith("https://") && modelId.isNotBlank()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (asr) tr("豆包实时语音", "Doubao streaming speech") else tr("独立翻译 API", "Independent translation API")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Hint(tr("所有字段在本机加密保存。清空密钥后保存可移除凭据。", "All fields are encrypted on this device. Clear the key and save to remove credentials."))
                SecretField(key, { key = it }, if (asr) "X-Api-Key" else "API key", true)
                if (asr) SecretField(resource, { resource = it }, "X-Api-Resource-Id", true)
                else {
                    SecretField(base, { base = it }, "Base URL · HTTPS", false)
                    SecretField(modelId, { modelId = it }, "Model / Endpoint ID", false)
                    Hint(tr("Base URL 为 API 根路径，不含 /chat/completions。豆包需填写自己的模型或 ep ID。", "Use the API root, without /chat/completions. Doubao requires your own model or ep ID."))
                }
                Hint(tr("使用所选云服务可能计费；不会因认证失败换用其他服务。", "The selected cloud service may charge you. Authentication failures never switch providers."))
            }
        }, confirmButton = {
            TextButton(onClick = {
                model.saveCredentials(provider, if (asr) mapOf("api_key" to key, "resource_id" to resource) else mapOf("api_key" to key, "base_url" to base, "model" to modelId))
                onDismiss(); model.checkSelectedEngines()
            }, enabled = enabled && (valid || key.isBlank())) { Text(tr("加密保存", "Save encrypted")) }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) } })
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String, password: Boolean) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ReadingSurface {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingChoice(title: String, choices: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) = SettingChoice(title, choices, selected, true, onSelect)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingChoice(title: String, choices: List<Pair<String, String>>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (value, label) -> FilterChip(selected == value, onClick = { onSelect(value) }, enabled = enabled, label = { Text(label) }) }
        }
    }
}

@Composable
private fun LanguageChoice(title: String, value: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$title · ${languageName(value)}", Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            listOf("en", "zh-CN", "ja", "ko", "fr", "de", "es").forEach { language ->
                DropdownMenuItem(text = { Text(languageName(language)) }, onClick = { onSelect(language); expanded = false })
            }
        }
    }
}
@Composable private fun Hint(value: String) { Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun ConfirmDelete(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(tr("删除", "Delete"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("保留", "Keep")) } })
}
@Composable private fun dataPath(prefs: AppPreferences): String = when {
    prefs.asrProvider == "local" && prefs.translationProvider == "mlkit" -> tr("全设备端 · 不上传音频或文字", "On-device · No audio or text upload")
    prefs.asrProvider != "local" && prefs.translationProvider != "mlkit" -> tr("上传音频 + 上传已确认文字", "Audio + confirmed text are uploaded")
    prefs.asrProvider != "local" -> tr("上传音频 · 文字在设备端翻译", "Audio uploaded · Translation on-device")
    else -> tr("音频留在设备 · 上传已确认文字", "Audio stays on-device · Confirmed text uploaded")
}
@Composable private fun phaseLabel(phase: ModelPhase): String = when (phase) {
    ModelPhase.NOT_INSTALLED -> tr("未安装", "Not installed"); ModelPhase.DOWNLOADING -> tr("下载中", "Downloading")
    ModelPhase.VERIFYING -> tr("校验 SHA-256", "Verifying SHA-256"); ModelPhase.EXTRACTING -> tr("安全解压中", "Extracting")
    ModelPhase.VALIDATING -> tr("验证模型中", "Validating model"); ModelPhase.INSTALLED -> tr("已安装", "Installed"); ModelPhase.ERROR -> tr("安装失败", "Installation failed")
}
private fun bytes(value: Long): String = if (value < 1_048_576) "%.0f KB".format(Locale.ROOT, value / 1024.0) else "%.1f MB".format(Locale.ROOT, value / 1_048_576.0)
