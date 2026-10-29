package io.github.vexpaer.openlistener.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vexpaer.openlistener.asr.TranscriptEvent
import io.github.vexpaer.openlistener.data.SegmentEntity
import io.github.vexpaer.openlistener.data.SessionEntity
import io.github.vexpaer.openlistener.service.MicrophoneRecognitionService.State
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class TranscriptMode { BILINGUAL, ORIGINAL, TRANSLATION }

@Composable
internal fun ListenPage(model: OpenListenerViewModel, foreground: Boolean, onSettings: () -> Unit) {
    val prefs by model.preferences.collectAsState()
    val state by model.serviceState.collectAsState()
    val segments by model.liveSegments.collectAsState()
    val partial by model.partial.collectAsState()
    val elapsed by model.elapsedMs.collectAsState()
    val rms by model.level.collectAsState()
    val error by model.serviceError.collectAsState()
    val check by model.engineCheck.collectAsState()
    val retrying by model.retrying.collectAsState()
    var mode by rememberSaveable { mutableStateOf(TranscriptMode.BILINGUAL) }
    val list = rememberLazyListState()
    var following by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val active = state.isSessionActive()
    val visiblePartial = partial?.takeIf { it.partial && it.text.isNotBlank() && segments.none { final -> final.sequence == it.segmentId && final.revision >= it.revision } }
    val count = segments.size + if (visiblePartial != null) 1 else 0
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.isNearEnd() }.distinctUntilChanged().collect { (scrolling, near) ->
            if (scrolling) following = near
        }
    }
    LaunchedEffect(count, segments.lastOrNull()?.translated, state) {
        if (following && count > 0 && !list.isScrollInProgress) list.scrollToItem(count - 1)
    }
    Column(Modifier.fillMaxSize().testTag("listen-page")) {
        TextButton(onClick = onSettings, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.Language, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("${languageName(prefs.sourceLanguage)}  →  ${languageName(prefs.targetLanguage)}")
            Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        }
        if (active || segments.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${stateLabel(state)} · ${formatTime(elapsed)}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (prefs.asrProvider == "local") tr("设备端识别", "On-device speech") else tr("云端识别", "Cloud speech"),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DisplayModes(mode, { mode = it })
        }
        if (error != null) ErrorText(error.orEmpty())
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                count > 0 -> ReadingSurface {
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("live-transcript"),
                        contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        items(segments, key = { "final-${it.sequence}" }) { segment ->
                            TranscriptParagraph(segment, mode, prefs.subtitleFontScale, retrying.contains(segment.sequence),
                                onCopy = { model.copyText(it) }, onRetry = { model.retryTranslation(segment.sessionId, segment.sequence) })
                        }
                        visiblePartial?.let { event -> item(key = "partial-${event.segmentId}") { PartialParagraph(event, mode, prefs.subtitleFontScale) } }
                    }
                }
                active -> EmptyReading(Icons.Outlined.GraphicEq, tr("等待第一段文字", "Waiting for the first words"),
                    tr("识别到的确认文字会自动保存在本机。", "Confirmed words are saved on this device as you listen."))
                else -> Column(Modifier.fillMaxSize().testTag("listen-empty"), verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.GraphicEq, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(20.dp))
                    Text(tr("听见课堂，\n读懂每一句。", "Your lecture,\nin both languages."), style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(14.dp))
                    Text(tr("只留下文字，不留下录音。", "Keep the words. Never the audio."), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    if (check.busy) Text(tr("正在检查所选引擎…", "Checking selected engines…"), style = MaterialTheme.typography.bodyMedium)
                    else if (!check.ready) {
                        Text(check.detail ?: tr("开始前，请下载离线模型或连接自己的 API。", "Download an offline model or connect your own API before listening."),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("engine-unavailable"))
                        TextButton(onClick = onSettings, modifier = Modifier.testTag("configure-engines")) {
                            Text(tr("下载模型 / 连接 API", "Models / Connect API"))
                        }
                    }
                }
            }
            if (!following && count > 0) FilledTonalButton(onClick = {
                following = true
                scope.launch { list.animateScrollToItem(count - 1) }
            }, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).testTag("back-to-latest")) {
                Icon(Icons.Outlined.ArrowDownward, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("回到最新", "Latest"))
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state) {
                State.PREPARING, State.STOPPING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth(.5f))
                    Text(if (state == State.PREPARING) tr("正在准备识别与翻译…", "Preparing speech and translation…") else tr("正在保存已确认的文字…", "Saving confirmed text…"),
                        style = MaterialTheme.typography.bodyMedium)
                }
                State.RECORDING, State.PAUSED -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ListeningButton(if (state == State.RECORDING) tr("暂停", "Pause") else tr("继续", "Resume"),
                        if (state == State.RECORDING) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        level = if (foreground && state == State.RECORDING) rms else 0f,
                        onClick = if (state == State.RECORDING) model::pauseListening else model::resumeListening)
                    OutlinedButton(onClick = model::stopListening, modifier = Modifier.heightIn(min = 52.dp).testTag("stop-listening")) {
                        Icon(Icons.Outlined.Stop, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(tr("结束", "Finish"))
                    }
                }
                else -> ListeningButton(tr("开始聆听", "Start listening"), Icons.Outlined.Mic, 0f, !check.busy, model::startListening)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tr("音频从不保存", "Audio is never saved."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ListeningButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, level: Float, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = LocalMotionAllowed.current
    val scale by animateFloatAsState(if (pressed && motion) .94f else 1f, tween(if (motion) 110 else 0), label = "mic-press")
    val signal by animateFloatAsState(level.coerceIn(0f, 1f), tween(if (motion) 100 else 0), label = "real-rms")
    val color = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            if (signal > .001f) Canvas(Modifier.fillMaxSize()) {
                drawCircle(color.copy(alpha = .22f + signal * .3f), radius = size.minDimension * (.42f + .06f * signal), style = Stroke((1.5f + signal * 2f).dp.toPx()))
            }
            Button(onClick = onClick, enabled = enabled, shape = CircleShape, interactionSource = interaction,
                contentPadding = PaddingValues(0.dp), modifier = Modifier.size(80.dp).graphicsLayer { scaleX = scale; scaleY = scale }.testTag("listen-primary")) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(30.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayModes(mode: TranscriptMode, onSelect: (TranscriptMode) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        TranscriptMode.entries.forEach { option ->
            FilterChip(selected = mode == option, onClick = { onSelect(option) }, label = {
                Text(when (option) { TranscriptMode.BILINGUAL -> tr("双语", "Bilingual"); TranscriptMode.ORIGINAL -> tr("原文", "Original"); TranscriptMode.TRANSLATION -> tr("译文", "Translation") })
            })
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TranscriptParagraph(segment: SegmentEntity, mode: TranscriptMode, fontScale: Float, retrying: Boolean, onCopy: (String) -> Unit, onRetry: () -> Unit) {
    val motion = LocalMotionAllowed.current
    var revealed by remember(segment.sequence) { mutableStateOf(false) }
    LaunchedEffect(segment.translated) { if (segment.translated != null) revealed = true }
    val fade by animateFloatAsState(if (revealed) 1f else .3f, tween(if (motion) 200 else 0), label = "translation-arrival")
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onCopy(listOfNotNull(segment.original, segment.translated).joinToString("\n")) }),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(formatTime(segment.startOffsetMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (mode != TranscriptMode.TRANSLATION) SelectionContainer {
            Text(segment.original, style = MaterialTheme.typography.bodyLarge.copy(fontSize = (17 * fontScale).sp, lineHeight = (27 * fontScale).sp))
        }
        if (mode != TranscriptMode.ORIGINAL) {
            if (segment.translated != null) SelectionContainer {
                Text(segment.translated, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = (18 * fontScale).sp, lineHeight = (28 * fontScale).sp),
                    modifier = Modifier.alpha(fade).heightIn(min = 28.dp))
            } else {
                val failed = segment.translationStatus.contains("FAIL", true) || segment.translationStatus.contains("ERROR", true)
                Text(if (failed) tr("翻译失败，原文已保存。请检查模型、网络或凭据。", "Translation failed; original text is saved. Check models, network or credentials.")
                    else tr("译文待完成", "Translation pending"), style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.heightIn(min = 28.dp))
                TextButton(onClick = onRetry, enabled = !retrying) { Text(if (retrying) tr("重译中…", "Translating…") else tr("重新翻译", "Retry translation")) }
            }
        }
    }
}

@Composable
private fun PartialParagraph(event: TranscriptEvent, mode: TranscriptMode, fontScale: Float) {
    if (mode == TranscriptMode.TRANSLATION) return
    val opacity by animateFloatAsState(.68f, tween(if (LocalMotionAllowed.current) 160 else 0), label = "partial-opacity")
    Column(Modifier.alpha(opacity), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${formatTime(event.startOffsetMs)} · ${tr("识别中", "Recognizing")}", style = MaterialTheme.typography.labelSmall)
        Text(event.text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = (17 * fontScale).sp, lineHeight = (27 * fontScale).sp))
    }
}

@Composable
internal fun LibraryPage(model: OpenListenerViewModel, onOpen: (String) -> Unit) {
    val sessions by model.searchResults.collectAsState()
    var query by rememberSaveable { mutableStateOf(model.searchQuery.value) }
    val list = rememberLazyListState()
    Column(Modifier.fillMaxSize().testTag("library-page"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = query, onValueChange = { query = it; model.searchQuery.value = it },
            label = { Text(tr("搜索标题、原文或译文", "Search titles and transcript")) }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = ""; model.searchQuery.value = "" }) { Icon(Icons.Outlined.Close, tr("清除搜索", "Clear search")) } },
            modifier = Modifier.fillMaxWidth().testTag("library-search"), shape = RoundedCornerShape(18.dp))
        if (sessions.isEmpty()) EmptyReading(Icons.Outlined.BookmarkBorder,
            if (query.isBlank()) tr("还没有记录", "No records yet") else tr("没有匹配的记录", "No matching records"),
            if (query.isBlank()) tr("结束聆听后，已确认的文字会留在这里。", "Confirmed words will be here after you finish listening.")
            else tr("试试其他标题或正文关键词。", "Try another title or phrase."), modifier = Modifier.weight(1f).testTag("library-empty"))
        else LazyColumn(state = list, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(sessions, key = { it.id }) { session ->
                Surface(onClick = { onOpen(session.id) }, color = MaterialTheme.colorScheme.surface.copy(alpha = .75f), shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${date(session.startedAt)} · ${formatTime(session.durationMs)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${languageName(session.sourceLang)} → ${languageName(session.targetLang)} · ${sessionStatus(session.state)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionDetailPage(id: String, model: OpenListenerViewModel, onBack: () -> Unit) {
    LaunchedEffect(id) { model.selectSession(id) }
    val session by model.selectedSession.collectAsState()
    val segments by model.detailSegments.collectAsState()
    val retrying by model.retrying.collectAsState()
    val prefs by model.preferences.collectAsState()
    var mode by rememberSaveable { mutableStateOf(TranscriptMode.BILINGUAL) }
    var rename by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable(id) { mutableStateOf("") }
    val list = rememberLazyListState()
    Column(Modifier.fillMaxSize().testTag("session-detail")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("返回记录", "Back to Library")) }
            Text(tr("文字记录", "Text record"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { title = session?.title.orEmpty(); rename = true }, enabled = session != null) { Icon(Icons.Outlined.Edit, tr("修改标题", "Rename")) }
            IconButton(onClick = { delete = true }, enabled = session != null) { Icon(Icons.Outlined.DeleteOutline, tr("删除记录", "Delete record")) }
        }
        val current = session
        if (current == null) {
            EmptyReading(Icons.Outlined.Description, tr("正在读取记录", "Loading record"), tr("文字保存在此设备。", "Text is stored on this device."))
        } else LazyColumn(state = list, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Text(current.title, style = MaterialTheme.typography.headlineSmall)
                Text("${date(current.startedAt)} · ${formatTime(current.durationMs)} · ${sessionStatus(current.state)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                DisplayModes(mode, { mode = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { model.copySession(id) }) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("复制全文", "Copy all")) }
                    DocumentFormat.entries.forEach { format ->
                        TextButton(onClick = { model.requestExport(id, format) }) { Text("${tr("导出", "Save")} ${format.extension.uppercase()}") }
                        TextButton(onClick = { model.shareSession(id, format) }) { Text("${tr("分享", "Share")} ${format.extension.uppercase()}") }
                    }
                }
            }
            if (segments.isEmpty()) item { Text(tr("这场聆听没有已确认的文字。", "This session has no confirmed text."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(segments, key = { it.sequence }) { segment ->
                ReadingSurface { Box(Modifier.padding(18.dp)) {
                    TranscriptParagraph(segment, mode, prefs.subtitleFontScale, retrying.contains(segment.sequence), model::copyText,
                        onRetry = { model.retryTranslation(id, segment.sequence) })
                } }
            }
        }
    }
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text(tr("修改标题", "Rename record")) }, text = {
        OutlinedTextField(title, { title = it.take(160) }, label = { Text(tr("标题", "Title")) }, modifier = Modifier.fillMaxWidth())
    }, confirmButton = { TextButton(onClick = { model.renameSession(id, title); rename = false }, enabled = title.isNotBlank()) { Text(tr("保存", "Save")) } },
        dismissButton = { TextButton(onClick = { rename = false }) { Text(tr("取消", "Cancel")) } })
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text(tr("删除这条记录？", "Delete this record?")) },
        text = { Text(tr("已保存的原文和译文将永久删除。导出的文件不受影响。", "Saved originals and translations will be permanently deleted. Exported documents are unaffected.")) },
        confirmButton = { TextButton(onClick = { model.deleteSession(id); delete = false; onBack() }) { Text(tr("删除", "Delete"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text(tr("保留", "Keep")) } })
}

@Composable private fun sessionStatus(state: String): String = when (state.uppercase()) {
    "FINISHED", "COMPLETED" -> tr("已完成", "Finished"); "INTERRUPTED", "ERROR" -> tr("已中断", "Interrupted")
    "PAUSED" -> tr("已暂停", "Paused"); else -> tr("进行中", "In progress")
}
@Composable internal fun ReadingSurface(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .86f), content = content)
}
@Composable internal fun ErrorText(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
}
@Composable internal fun EmptyReading(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
        Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 18.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
    }
}
private fun LazyListState.isNearEnd(): Boolean = layoutInfo.totalItemsCount == 0 ||
    (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layoutInfo.totalItemsCount - 2
