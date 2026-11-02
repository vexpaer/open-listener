package io.github.vexpaer.openlistener.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import io.github.vexpaer.openlistener.BuildConfig
import io.github.vexpaer.openlistener.ui.theme.LocalDarkAppearance
import io.github.vexpaer.openlistener.security.CloudProfile
import io.github.vexpaer.openlistener.models.ModelPhase
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsPage(model: OpenListenerViewModel, active: Boolean = true,
                          openPanel: String? = null, onPanelOpened: () -> Unit = {}) {
    val prefs by model.preferences.collectAsState()
    val state by model.serviceState.collectAsState()
    val check by model.engineCheck.collectAsState()
    val updates by model.updates.collectAsState()
    val installed by model.installedLanguages.collectAsState()
    val languageBusy by model.languageBusy.collectAsState()
    val modelStates by model.modelStates.collectAsState()
    val cloudProfiles by model.cloudProfiles.collectAsState()
    val activeCloudProfiles by model.activeCloudProfiles.collectAsState()
    val editingAllowed = !state.isSessionActive()
    val list = rememberLazyListState()
    var guideFor by rememberSaveable { mutableStateOf<String?>(null) }
    var credentialsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var chooseCloudKind by rememberSaveable { mutableStateOf<String?>(null) }
    var newCloudKind by rememberSaveable { mutableStateOf<String?>(null) }
    var newCloudProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var editingCloudProfile by remember { mutableStateOf<CloudProfile?>(null) }
    var deletingCloudProfile by remember { mutableStateOf<CloudProfile?>(null) }
    var deleteLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteModel by rememberSaveable { mutableStateOf<String?>(null) }
    var font by remember(prefs.subtitleFontScale) { mutableFloatStateOf(prefs.subtitleFontScale) }
    LaunchedEffect(Unit) { model.refreshLanguageModels() }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    val sublist = key(page) { rememberLazyListState() }
    LaunchedEffect(openPanel) {
        if (openPanel != null) {
            page = when (openPanel) {
                "translation" -> if (prefs.asrProvider == "doubao_ast") "translation-integrated"
                    else if (prefs.translationProvider == "mlkit") "translation-local" else "translation-cloud"
                else -> if (prefs.asrProvider == "local") "asr-local" else "asr-cloud"
            }
            onPanelOpened()
        }
    }
    BackHandler(enabled = active && page != null) { page = null }
    Column(Modifier.fillMaxSize()) {
        page?.let { current ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page = null }, modifier = Modifier.testTag("model-page-back")) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("返回设置", "Back to settings"))
                }
                Text(if (current.startsWith("asr")) tr("语音识别", "Speech recognition") else tr("文字翻译", "Translation"), style = MaterialTheme.typography.titleMedium)
            }
        }
        val connectedPanel = page != null && page != "translation-integrated"
        CompositionLocalProvider(LocalConnectedModelPanel provides connectedPanel, LocalReadingSurfaceAlpha provides .30f) {
            Column(Modifier.weight(1f).fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (connectedPanel) MaterialTheme.colorScheme.surface.copy(alpha = .30f) else Color.Transparent)) {
                page?.let { current ->
                    if (current != "translation-integrated") {
                    val speech = current.startsWith("asr")
                    val local = current.endsWith("local")
                    ModelCategories(local, editingAllowed, if (speech) "asr" else "translation", onLocal = {
                        if (speech) model.setProviders("local", prefs.translationProvider) else model.setProviders(prefs.asrProvider, "mlkit")
                        page = if (speech) "asr-local" else "translation-local"
                    }, onCloud = {
                        page = if (speech) "asr-cloud" else "translation-cloud"
                    })
                    }
                }
                LazyColumn(state = if (page == null) list else sublist,
                    modifier = Modifier.weight(1f).testTag(if (page == null) "settings-page" else "settings-model-page"),
                    verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                    if (page == null) {
                        item {
                            SettingsSection(tr("目标语言", "Target language"), Icons.Outlined.Language) {
                                LanguageChoice(tr("从", "From"), prefs.sourceLanguage, editingAllowed) { model.setLanguages(it, prefs.targetLanguage) }
                                LanguageChoice(tr("翻译为", "To"), prefs.targetLanguage, editingAllowed) { model.setLanguages(prefs.sourceLanguage, it) }
                                if (!editingAllowed) Hint(tr("结束聆听后可修改", "Finish listening to edit"))
                            }
                        }
                        item {
                            ReadingSurface {
                                Column(Modifier.fillMaxWidth()) {
                                    CompactSettingRow(tr("语音识别", "Speech recognition"), Icons.Outlined.Mic, "asr-settings",
                                        onClick = { page = if (prefs.asrProvider == "local") "asr-local" else "asr-cloud" })
                                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                                    CompactSettingRow(tr("文字翻译", "Translation"), Icons.Outlined.Translate, "translation-settings",
                                        onClick = { page = if (prefs.asrProvider == "doubao_ast") "translation-integrated" else if (prefs.translationProvider == "mlkit") "translation-local" else "translation-cloud" })
                                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.GraphicEq, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text(tr("语音保存", "Keep recordings"), Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
                                        Switch(prefs.retainAudio, model::setRetainAudio, enabled = editingAllowed, modifier = Modifier.testTag("retain-audio"))
                                    }
                                }
                            }
                        }
                        item {
                            SettingsSection(tr("外观", "Appearance"), Icons.Outlined.WbSunny) {
                                InlineToggle(tr("主题", "Theme"), "light" to tr("浅色", "Light"), "dark" to tr("深色", "Dark"),
                                    if (LocalDarkAppearance.current) "dark" else "light", model::setTheme)
                                InlineToggle(tr("界面语言", "Language"), "zh" to "中文", "en" to "English",
                                    if (LocalChinese.current) "zh" else "en", model::setLanguage)
                                InlineToggle(tr("通知字幕", "Notification"), "original" to tr("原文", "Original"),
                                    "translation" to tr("译文", "Translated"), prefs.notificationSubtitle, model::setNotificationSubtitle)
                                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(tr("纯色背景", "Solid background"), Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                                    Switch(prefs.solidBackground, model::setSolidBackground,
                                        modifier = Modifier.testTag("solid-background"))
                                }
                                Text(tr("主题颜色", "Accent color"), style = MaterialTheme.typography.labelLarge)
                                val accents = listOf(0xFFD97757L, 0xFF406B88L, 0xFF8B78C9L, 0xFF5FAF91L, 0xFFE5A548L)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    accents.forEach { color ->
                                        val selected = prefs.themeColor == color
                                        Surface(onClick = { model.setThemeColor(color) }, shape = RoundedCornerShape(50),
                                            color = Color(color.toInt()), border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
                                            modifier = Modifier.size(if (selected) 38.dp else 34.dp).testTag("theme-color-" + color)) {}
                                    }
                                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(50))
                                        .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                                        .clickable { showColorPicker = true }.testTag("theme-color-custom"), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.Palette, tr("自定义颜色", "Custom color"), Modifier.size(21.dp), tint = Color.White)
                                    }
                                }
                                Text("${tr("字幕字号", "Subtitle size")} · ${(font * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                                Slider(font, { font = it }, valueRange = .85f..1.5f, steps = 12,
                                    onValueChangeFinished = { model.setSubtitleFontScale(font) }, modifier = Modifier.testTag("subtitle-size"))
                            }
                        }
                        item {
                            SettingsSection(tr("更新与关于", "Updates & about"), Icons.Outlined.SystemUpdate) {
                                Text("Open Listener  v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.titleMedium)
                                Row(Modifier.fillMaxWidth().testTag("update-actions"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = model::checkUpdates, enabled = !updates.checking && !updates.downloading,
                                        modifier = Modifier.weight(1f).testTag("check-updates"), contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (updates.checking) tr("检查中…", "Checking…") else tr("检查更新", "Check updates"), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                                    OutlinedButton(onClick = model::downloadUpdate, enabled = updates.update != null && !updates.downloading && !updates.checking && editingAllowed && updates.file == null,
                                        modifier = Modifier.weight(1f).testTag("download-update"), contentPadding = PaddingValues(horizontal = 8.dp)) { Text(tr("下载更新", "Download"), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                                }
                                when {
                                    updates.checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                    updates.error != null -> ErrorText(updates.error.orEmpty())
                                    updates.checked && updates.update == null -> Text(tr("已是最新版本。", "You're up to date."), style = MaterialTheme.typography.bodyMedium)
                                    updates.update != null -> {
                                        Text("${tr("可用版本", "Available")}: ${updates.update?.versionName}", style = MaterialTheme.typography.labelLarge)
                                        updates.update?.let { update ->
                                            Hint("${tr("自动匹配安装包", "Matched APK")}: ${update.abi} · ${bytes(update.apk.size)}")
                                        }
                                        
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
                                    Button(onClick = model::installUpdate, enabled = editingAllowed, modifier = Modifier.testTag("install-update")) { Text(tr("安装", "Install")) }
                                }
                                updates.update?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                                    Text(notes, style = MaterialTheme.typography.bodySmall)
                                }
                                if (!editingAllowed) Hint(tr("结束聆听后可更新", "Finish listening to update"))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { model.openUrl("https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPOSITORY}") }) { Text("GitHub") }
                                    Text("by Vexpaer", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else when (page) {
                        "asr-local" -> {
                            model.modelCatalog.forEach { manifest ->
                                item(key = "model-${manifest.id}") {
                                    ReadingSurface {
                                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val status = modelStates[manifest.id]
                                            val phase = status?.phase ?: ModelPhase.NOT_INSTALLED
                                            var more by remember { mutableStateOf(false) }
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = phase == ModelPhase.INSTALLED &&
                                                        prefs.asrProvider == "local" && prefs.localAsrModel == manifest.id,
                                                    onCheckedChange = { if (it) model.selectLocalModel(manifest.id) },
                                                    enabled = editingAllowed && phase == ModelPhase.INSTALLED,
                                                    modifier = Modifier.size(44.dp).testTag("select-model-${manifest.id}"),
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = MaterialTheme.colorScheme.primary,
                                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant))
                                                Column(Modifier.weight(1f)) {
                                                    Text(manifest.displayName.replace(" (INT8)", ""),
                                                        style = MaterialTheme.typography.titleSmall)
                                                    Row(verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                                        Text("${bytes(manifest.bytes)} · ${phaseLabel(phase)}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        if (phase == ModelPhase.INSTALLED)
                                                            Icon(Icons.Outlined.CheckCircle, null,
                                                                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                                Box {
                                                    IconButton(onClick = { more = true }) {
                                                        Icon(Icons.Outlined.MoreHoriz, tr("模型选项", "Model options"))
                                                    }
                                                    DropdownMenu(more, { more = false }) {
                                                        if (phase in listOf(ModelPhase.NOT_INSTALLED, ModelPhase.ERROR))
                                                            DropdownMenuItem(text = { Text(tr("下载模型", "Download model")) },
                                                                enabled = editingAllowed, onClick = {
                                                                    more = false
                                                                    model.downloadModel(manifest.id)
                                                                }, modifier = Modifier.testTag("download-asr-model-${manifest.id}"))
                                                        DropdownMenuItem(text = { Text(tr("来源", "Source")) },
                                                            onClick = { more = false; model.openUrl(manifest.sourceUrl) })
                                                        DropdownMenuItem(text = { Text(tr("许可证", "License")) },
                                                            onClick = { more = false; model.openUrl(manifest.licenseUrl) })
                                                        if (phase == ModelPhase.INSTALLED)
                                                            DropdownMenuItem(text = { Text(tr("删除模型", "Delete model")) },
                                                                enabled = editingAllowed && status?.inUse != true,
                                                                onClick = { more = false; deleteModel = manifest.id })
                                                    }
                                                }
                                            }
                                            if (phase == ModelPhase.DOWNLOADING) {
                                                val downloaded = status?.downloadedBytes ?: 0
                                                val total = status?.totalBytes ?: manifest.bytes
                                                LinearProgressIndicator(progress = { if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                    Text("${bytes(downloaded)} / ${bytes(total)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                                    TextButton(onClick = { model.cancelModelDownload(manifest.id) }) { Text(tr("取消", "Cancel")) }
                                                }
                                            } else if (phase in listOf(ModelPhase.VERIFYING, ModelPhase.EXTRACTING, ModelPhase.VALIDATING)) {
                                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                                if (phase == ModelPhase.EXTRACTING) {
                                                    Text(tr("已解压 ", "Extracted ") + bytes(status?.extractedBytes ?: 0L),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            status?.error?.let { ErrorText(it) }
                                        }
                                    }
                                }
                            }
                        }
                        "asr-cloud" -> item {
                            CloudProfilePanel("asr", cloudProfiles.filter { it.kind == "asr" },
                                activeCloudProfiles["asr"]?.takeIf { prefs.asrProvider != "local" }, editingAllowed,
                                onSelect = model::selectCloudProfile,
                                onAdd = { chooseCloudKind = "asr" },
                                onEdit = { editingCloudProfile = it })
                        }
                        "translation-integrated" -> item {
                            SettingsSection(tr("豆包同声传译", "Doubao simultaneous translation"), Icons.Outlined.Translate) {
                                Text(tr("原文与译文由同传模型直接生成。", "The simultaneous model generates both original and translated subtitles."), style = MaterialTheme.typography.bodyMedium)
                                Hint(tr("仅输出文字。源语言与目标语言可在设置首页修改。", "Text output only. Choose spoken and target languages on the settings overview."))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(onClick = { credentialsFor = "doubao_ast" }, enabled = editingAllowed) { Text(tr("配置 API", "Configure API")) }
                                    ApiHelpButton("ast-translation-help") { guideFor = "doubao_ast" }
                                }
                            }
                        }
                        "translation-local" -> item {
                            SettingsSection("Google ML Kit", Icons.Outlined.Translate) {
                                OutlinedButton(onClick = model::downloadLanguageModels, enabled = !languageBusy && editingAllowed,
                                    modifier = Modifier.testTag("download-language-models")) { Text(if (languageBusy) tr("处理中…", "Working…") else tr("下载所选语言包", "Download selected languages")) }
                                if (languageBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                if (installed.isEmpty()) Hint(tr("尚未下载语言包", "No language models downloaded"))
                                installed.sorted().forEach { language ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${languageName(language)} · ${tr("已安装", "Installed")}", modifier = Modifier.weight(1f))
                                        IconButton(onClick = { deleteLanguage = language }, enabled = !languageBusy && editingAllowed) {
                                            Icon(Icons.Outlined.DeleteOutline, "${tr("删除语言包", "Delete language model")} $language")
                                        }
                                    }
                                }
                                TextButton(onClick = model::refreshLanguageModels, enabled = !languageBusy) { Text(tr("刷新已安装语言", "Refresh installed languages")) }
                            }
                        }
                        "translation-cloud" -> item {
                            CloudProfilePanel("translation", cloudProfiles.filter { it.kind == "translation" },
                                activeCloudProfiles["translation"]?.takeIf { prefs.translationProvider != "mlkit" }, editingAllowed,
                                onSelect = model::selectCloudProfile,
                                onAdd = { chooseCloudKind = "translation" },
                                onEdit = { editingCloudProfile = it })
                        }
                    }
                }
            }
        }
    }
    chooseCloudKind?.let { kind ->
        val candidates = if (kind == "asr") listOf("doubao", "doubao_ast", "qwen", "openai", "custom")
            else listOf("doubao", "qwen", "openai", "custom")
        AlertDialog(onDismissRequest = { chooseCloudKind = null },
            title = { Text(tr("添加云端配置", "Add cloud model")) },
            text = {
                Column {
                    candidates.forEach { provider ->
                        TextButton(onClick = {
                            newCloudKind = kind
                            newCloudProvider = provider
                            chooseCloudKind = null
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(when (provider) {
                                "doubao" -> tr("豆包", "Doubao")
                                "doubao_ast" -> tr("豆包同传", "Doubao AST")
                                "qwen" -> tr("阿里云", "Aliyun")
                                "openai" -> "OpenAI"
                                else -> tr("自定义", "Custom")
                            })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooseCloudKind = null }) { Text(tr("取消", "Cancel")) } })
    }
    newCloudProvider?.let { selected ->
        val kind = newCloudKind ?: "asr"
        val providerKey = if (kind == "translation") "translation_$selected"
            else if (selected in listOf("doubao", "doubao_ast")) selected else "asr_$selected"
        CredentialsDialog(providerKey, model, editingAllowed,
            onHelp = { guideFor = providerKey },
            onDismiss = { newCloudProvider = null; newCloudKind = null },
            onSaved = { fields -> model.addCloudProfile(kind, selected, fields) })
    }
    editingCloudProfile?.let { profile ->
        val providerKey = if (profile.kind == "translation") "translation_${profile.provider}"
            else if (profile.provider in listOf("doubao", "doubao_ast")) profile.provider else "asr_${profile.provider}"
        CredentialsDialog(providerKey, model, editingAllowed,
            onHelp = { guideFor = providerKey },
            onDismiss = { editingCloudProfile = null },
            onSaved = { fields -> model.updateCloudProfile(profile, fields) },
            editing = profile, onDelete = { deletingCloudProfile = profile; editingCloudProfile = null })
    }
    deletingCloudProfile?.let { profile ->
        ConfirmDelete(tr("删除云端配置？", "Delete cloud configuration?"),
            tr("此配置的 API 凭据也将从设备删除。", "The saved API credentials will also be deleted."),
            onDismiss = { deletingCloudProfile = null },
            onConfirm = { model.deleteCloudProfile(profile); deletingCloudProfile = null })
    }
    if (showColorPicker) ThemeColorPicker(prefs.themeColor,
        onDismiss = { showColorPicker = false },
        onConfirm = { model.setThemeColor(it); showColorPicker = false })
    credentialsFor?.let { provider -> CredentialsDialog(provider, model, editingAllowed,
        onHelp = { guideFor = provider }, onDismiss = { credentialsFor = null }) }
    guideFor?.let { provider ->
        ApiGuidePage(provider, model.credential(provider, "base_url"), model::openUrl) { guideFor = null }
    }
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
private fun CloudProfilePanel(kind: String, profiles: List<CloudProfile>, selected: String?,
                              enabled: Boolean, onSelect: (CloudProfile) -> Unit,
                              onAdd: () -> Unit, onEdit: (CloudProfile) -> Unit) {
    SettingsSection(tr("云端配置", "Cloud configurations"), Icons.Outlined.Cloud) {
        profiles.forEach { profile ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = { onSelect(profile) }, enabled = enabled,
                    modifier = Modifier.weight(1f).testTag("$kind-profile-${profile.id}"),
                    color = if (selected == profile.id) MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                        else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selected == profile.id,
                            onCheckedChange = null, enabled = enabled,
                            modifier = Modifier.size(36.dp),
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant))
                        Text(profile.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 6.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                IconButton(onClick = { onEdit(profile) }, enabled = enabled) {
                    Icon(Icons.Outlined.Edit, tr("编辑", "Edit"))
                }
            }
        }
        OutlinedButton(onClick = onAdd, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("$kind-add-cloud-profile")) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(7.dp))
            Text(tr("添加配置", "Add configuration"))
        }
    }
}

@Composable
private fun CredentialsDialog(provider: String, model: OpenListenerViewModel, enabled: Boolean, onHelp: () -> Unit,
                              onDismiss: () -> Unit, onSaved: ((Map<String, String>) -> Unit)? = null,
                              editing: CloudProfile? = null, onDelete: (() -> Unit)? = null) {
    val asr = provider == "doubao"
    val ast = provider == "doubao_ast"
    val supportsLegacyAuth = asr || ast
    val defaultBase = when (provider) {
        "translation_doubao" -> "https://ark.cn-beijing.volces.com/api/v3"
        "translation_qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
        "translation_openai", "asr_openai" -> "https://api.openai.com/v1"
        "asr_qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
        else -> ""
    }
    val defaultModel = when (provider) { "translation_qwen" -> "qwen-plus"; "translation_openai" -> "gpt-4.1-mini"; "asr_openai" -> "gpt-transcribe"; "asr_qwen" -> "qwen3-asr-flash"; else -> "" }
    val existing: (String) -> String = { field ->
        when {
            editing != null -> model.profileCredential(editing, field)
            onSaved != null -> ""
            else -> model.credential(provider, field)
        }
    }
    // Deliberately not rememberSaveable: secrets must never enter Activity saved-state bundles.
    var key by remember(provider, editing?.id, onSaved != null) { mutableStateOf(existing("api_key").ifBlank { if (ast && onSaved == null) model.credential("doubao", "api_key") else "" }) }
    var appId by remember(provider, editing?.id, onSaved != null) { mutableStateOf(existing("app_id").ifBlank { if (ast && onSaved == null) model.credential("doubao", "app_id") else "" }) }
    var accessKey by remember(provider, editing?.id, onSaved != null) { mutableStateOf(existing("access_key").ifBlank { if (ast && onSaved == null) model.credential("doubao", "access_key") else "" }) }
    var legacyAuth by remember(provider, editing?.id, onSaved != null) {
        mutableStateOf(appId.isNotBlank() || accessKey.isNotBlank())
    }
    var resource by remember(provider, editing?.id, onSaved != null) { mutableStateOf(existing("resource_id")) }
    var base by remember(provider, editing?.id, onSaved != null) { mutableStateOf(existing("base_url").ifBlank { defaultBase }) }
    var modelId by remember(provider, editing?.id, onSaved != null) { mutableStateOf(existing("model").ifBlank { defaultModel }) }
    val authValid = if (!supportsLegacyAuth || !legacyAuth) key.isNotBlank() else appId.isNotBlank() && accessKey.isNotBlank()
    val valid = authValid && if (asr) resource.isNotBlank() else if (ast) true else base.startsWith("https://") && modelId.isNotBlank()
    val clearAuth = key.isBlank() && appId.isBlank() && accessKey.isBlank()
    AlertDialog(onDismissRequest = onDismiss, title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (ast) tr("豆包同声传译", "Doubao simultaneous translation") else if (asr) tr("豆包实时语音", "Doubao streaming speech") else if (provider.startsWith("asr_")) tr("语音识别 API", "Speech API") else tr("翻译 API", "Translation API"), modifier = Modifier.weight(1f))
            ApiHelpButton("credentials-api-help", onHelp)
        }
    },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Hint(tr("密钥在本机加密保存", "Keys are encrypted on this device"))
                if (supportsLegacyAuth) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !legacyAuth, onClick = { legacyAuth = false },
                            label = { Text(tr("新版 API Key", "New API Key")) })
                        FilterChip(selected = legacyAuth, onClick = { legacyAuth = true },
                            label = { Text(tr("旧版控制台", "Legacy console")) })
                    }
                }
                if (supportsLegacyAuth && legacyAuth) {
                    SecretField(appId, { appId = it }, if (ast) "X-Api-App-Id" else "X-Api-App-Key", false)
                    SecretField(accessKey, { accessKey = it }, "X-Api-Access-Key", true)
                } else {
                    SecretField(key, { key = it }, if (supportsLegacyAuth) "X-Api-Key" else "API key", true)
                }
                if (asr) SecretField(resource, { resource = it }, "X-Api-Resource-Id", true)
                else if (ast) {
                    Hint(tr("请在 API Key 所属项目开通同传 2.0。应用使用 S2T 字幕模式；旧版控制台需填写 App ID 和 Access Token。", "Enable AST 2.0 in the credential's project. The app uses S2T subtitles; legacy console credentials require an App ID and Access Token."))
                } else {
                    SecretField(base, { base = it }, "Base URL · HTTPS", false)
                    SecretField(modelId, { modelId = it }, "Model / Endpoint ID", false)
                    Hint(if (provider.startsWith("asr_")) tr("填写 API 根路径和语音模型 ID。OpenAI / 自定义使用 /audio/transcriptions，阿里云使用 Qwen-ASR /chat/completions。", "Enter the API root and speech model ID. OpenAI / Custom use /audio/transcriptions; Aliyun uses Qwen-ASR /chat/completions.") else tr("Base URL 为 API 根路径，不含 /chat/completions。豆包需填写自己的模型或 ep ID。", "Use the API root, without /chat/completions. Doubao requires your own model or ep ID."))
                }
            }
        }, confirmButton = {
            TextButton(onClick = {
                val authFields = if (!supportsLegacyAuth) mapOf("api_key" to key)
                    else if (legacyAuth) mapOf("api_key" to "", "app_id" to appId, "access_key" to accessKey)
                    else mapOf("api_key" to key, "app_id" to "", "access_key" to "")
                val fields = when {
                    asr -> authFields + mapOf("resource_id" to resource)
                    ast -> authFields
                    else -> authFields + mapOf("base_url" to base, "model" to modelId)
                }
                if (onSaved != null) onSaved(fields) else model.saveCredentials(provider, fields)
                onDismiss()
                if (onSaved == null) model.checkSelectedEngines()
            }, enabled = enabled && if (onSaved != null) valid else valid || clearAuth) { Text(tr("保存", "Save")) }
        }, dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = onDelete, enabled = enabled) {
                    Text(tr("删除配置", "Delete"), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) }
            }
        })
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
            Text("$title · ${languageName(value)}", Modifier.weight(1f), maxLines = 1,
                softWrap = false, overflow = TextOverflow.Ellipsis); Icon(Icons.Outlined.ExpandMore, null)
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
@Composable private fun phaseLabel(phase: ModelPhase): String = when (phase) {
    ModelPhase.NOT_INSTALLED -> tr("未安装", "Not installed"); ModelPhase.DOWNLOADING -> tr("下载中", "Downloading")
    ModelPhase.VERIFYING -> tr("校验 SHA-256", "Verifying SHA-256"); ModelPhase.EXTRACTING -> tr("安全解压中", "Extracting")
    ModelPhase.VALIDATING -> tr("验证模型中", "Validating model"); ModelPhase.INSTALLED -> tr("已安装", "Installed"); ModelPhase.ERROR -> tr("安装失败", "Installation failed")
}
private fun bytes(value: Long): String = if (value < 1_048_576) "%.0f KB".format(Locale.ROOT, value / 1024.0) else "%.1f MB".format(Locale.ROOT, value / 1_048_576.0)

@Composable
private fun InlineToggle(title: String, first: Pair<String, String>, second: Pair<String, String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Row(Modifier.width(176.dp).fillMaxHeight().clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .30f))) {
            listOf(first, second).forEach { (key, label) ->
                val background by animateColorAsState(if (selected == key) MaterialTheme.colorScheme.primary.copy(alpha = .22f) else Color.Transparent,
                    animationSpec = tween(230), label = "settings-selection")
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).background(background)
                    .clickable { onSelect(key) }, contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                        softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ModelCategories(local: Boolean, enabled: Boolean, tag: String, onLocal: () -> Unit, onCloud: () -> Unit) {
    // Only the panel exterior is rounded; tabs and content meet on square edges.
    BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp)) {
        val tabWidth = maxWidth / 2
        Box(Modifier.offset(x = if (local) tabWidth else 0.dp).width(tabWidth).fillMaxHeight()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = .18f)))
        Row(Modifier.fillMaxSize()) {
            listOf(true to tr("本地模型", "Local models"), false to tr("云端模型", "Cloud models")).forEach { (isLocal, label) ->
                val selected = local == isLocal
                Box(Modifier.weight(1f).fillMaxHeight()
                    .selectable(selected = selected, enabled = enabled, role = Role.Tab,
                        onClick = { if (isLocal) onLocal() else onCloud() })
                    .testTag("$tag-" + if (isLocal) "local-models" else "cloud-models"),
                    contentAlignment = Alignment.Center) {
                    Text(label, color = if (selected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CompactSettingRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
