package io.github.vexpaer.openlistener.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import io.github.vexpaer.openlistener.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    val rms by model.level.collectAsState()
    val error by model.serviceError.collectAsState()
    val check by model.engineCheck.collectAsState()
    val retrying by model.retrying.collectAsState()
    var mode by rememberSaveable { mutableStateOf(TranscriptMode.BILINGUAL) }
    val list = rememberLazyListState()
    var following by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val activeId by model.activeSessionId.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val active = state.isSessionActive()
    val visiblePartial = partial?.takeIf { it.partial && it.text.isNotBlank() && segments.none { final -> final.sequence == it.segmentId && final.revision >= it.revision } }
    val count = segments.size + if (visiblePartial != null) 1 else 0
    LaunchedEffect(activeId) { if (activeId != null) following = true }
    LaunchedEffect(list) {
        list.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> { dragging = true; following = false }
                is DragInteraction.Stop, is DragInteraction.Cancel -> dragging = false
            }
        }
    }
    LaunchedEffect(list) {
        snapshotFlow { Triple(list.canScrollForward, list.isScrollInProgress, dragging) }
            .distinctUntilChanged().collect { (canForward, scrolling, touching) ->
                if (!canForward && !scrolling && !touching) following = true
            }
    }
    val userScroll = remember(list) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) following = false
                return Offset.Zero
            }
        }
    }
    // Include content revisions and layout changes: one long partial/translation can grow
    // without adding an item. A tail anchor reaches the bottom of oversized paragraphs.
    LaunchedEffect(segments, visiblePartial, mode, prefs.subtitleFontScale, following, foreground, viewportHeight) {
        if (following && foreground && count > 0 && !dragging) {
            withFrameNanos { }
            if (!list.isScrollInProgress && following && !dragging) list.scrollToItem(count)
        }
    }
    Column(Modifier.fillMaxSize().testTag("listen-page")) {
        if (active || segments.isNotEmpty()) {
            DisplayModes(mode, { mode = it })
        }
        if (error != null) ErrorText(error.orEmpty())
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                count > 0 -> ReadingSurface(backgroundAlpha = .2f) {
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize().nestedScroll(userScroll).onSizeChanged { viewportHeight = it.height }.testTag("live-transcript"),
                        contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        items(segments, key = { "final-${it.sequence}" }) { segment ->
                            TranscriptParagraph(segment, mode, prefs.subtitleFontScale, retrying.contains(segment.sequence),
                                onCopy = { model.copyText(it) }, onRetry = { model.retryTranslation(segment.sessionId, segment.sequence) }, live = true, canRetry = prefs.asrProvider != "doubao_ast")
                        }
                        visiblePartial?.let { event -> item(key = "partial-${event.segmentId}") { PartialParagraph(event, mode, prefs.subtitleFontScale) } }
                        item(key = "live-tail") { Spacer(Modifier.fillMaxWidth().height(1.dp).testTag("live-tail")) }
                    }
                }
                active -> EmptyReading(Icons.Outlined.GraphicEq, tr("等待第一段文字", "Waiting for the first words"),
                    tr("识别到的确认文字会自动保存在本机。", "Confirmed words are saved on this device as you listen."))
                else -> Column(Modifier.fillMaxSize().testTag("listen-empty"), verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.GraphicEq, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(20.dp))
                    Text(tr("听见课堂，\n读懂每一句。", "Listen clearly.\nRead along."), style = MaterialTheme.typography.headlineLarge)
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
                scope.launch { list.animateScrollToItem(count) }
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
                    ListeningButton(tr("结束", "Finish"), Icons.Outlined.Stop, 0f, onClick = model::stopListening,
                        stop = true, tag = "stop-listening")
                }
                else -> ListeningButton(tr("开始聆听", "Start listening"), Icons.Outlined.Mic, 0f, !check.busy, model::startListening)
            }
        }
    }
}

@Composable
private fun ListeningButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, level: Float, enabled: Boolean = true, onClick: () -> Unit, stop: Boolean = false, tag: String = "listen-primary") {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = LocalMotionAllowed.current
    val scale by animateFloatAsState(if (pressed && motion) .92f else 1f, tween(if (motion) 110 else 0), label = "mic-press")
    val signal by animateFloatAsState(level.coerceIn(0f, 1f), tween(if (motion) 100 else 0), label = "real-rms")
    val color = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            if (signal > .001f) Canvas(Modifier.fillMaxSize()) {
                drawCircle(color.copy(alpha = .22f + signal * .3f), radius = size.minDimension * (.42f + .06f * signal), style = Stroke((1.5f + signal * 2f).dp.toPx()))
            }
            val palette = MaterialTheme.colorScheme
            val top = if (stop) palette.surface else palette.primary.copy(alpha = .88f)
            val bottom = if (stop) palette.surfaceVariant else palette.primary
            Button(onClick = onClick, enabled = enabled, shape = CircleShape, interactionSource = interaction,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
                    contentColor = if (stop) palette.onSurface else palette.onPrimary,
                    disabledContainerColor = palette.surfaceVariant),
                contentPadding = PaddingValues(0.dp), modifier = Modifier.size(80.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationY = if (pressed && motion) 2.dp.toPx() else 0f }
                    .shadow(if (pressed) 2.dp else 9.dp, CircleShape, ambientColor = palette.primary.copy(alpha = .25f), spotColor = palette.primary.copy(alpha = .3f))
                    .clip(CircleShape).background(Brush.verticalGradient(listOf(top, bottom)))
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .55f), palette.primary.copy(alpha = .12f))), CircleShape)
                    .testTag(tag)) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(30.dp))
            }
        }
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
private fun TranscriptParagraph(segment: SegmentEntity, mode: TranscriptMode, fontScale: Float, retrying: Boolean, onCopy: (String) -> Unit, onRetry: () -> Unit, live: Boolean = false, canRetry: Boolean = true) {
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onCopy(listOfNotNull(segment.original, segment.translated).joinToString("\n")) }),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (mode != TranscriptMode.TRANSLATION) SelectionContainer {
            ArrivalText(segment.original, live, MaterialTheme.typography.bodyLarge.copy(fontSize = (17 * fontScale).sp, lineHeight = (27 * fontScale).sp))
        }
        if (mode != TranscriptMode.ORIGINAL) {
            if (segment.translated != null) SelectionContainer {
                ArrivalText(segment.translated, live,
                    MaterialTheme.typography.bodyLarge.copy(fontSize = (18 * fontScale).sp, lineHeight = (28 * fontScale).sp),
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.heightIn(min = 28.dp))
            } else {
                val failed = segment.translationStatus.contains("FAIL", true) || segment.translationStatus.contains("ERROR", true)
                Text(if (failed) tr("翻译失败，原文已保存。请检查模型、网络或凭据。", "Translation failed; original text is saved. Check models, network or credentials.")
                    else if (segment.translationStatus == "INCOMPLETE") tr("同传译文未完成", "Translation incomplete") else tr("译文待完成", "Translation pending"), style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.heightIn(min = 28.dp))
                if (canRetry) TextButton(onClick = onRetry, enabled = !retrying) { Text(if (retrying) tr("重译中…", "Translating…") else tr("重新翻译", "Retry translation")) }
            }
        }
    }
}

@Composable
private fun PartialParagraph(event: TranscriptEvent, mode: TranscriptMode, fontScale: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (mode != TranscriptMode.TRANSLATION && event.text.isNotBlank()) ArrivalText(event.text, true,
            MaterialTheme.typography.bodyLarge.copy(fontSize = (17 * fontScale).sp, lineHeight = (27 * fontScale).sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
        if (mode != TranscriptMode.ORIGINAL && !event.translated.isNullOrBlank()) ArrivalText(event.translated, true,
            MaterialTheme.typography.bodyLarge.copy(fontSize = (18 * fontScale).sp, lineHeight = (28 * fontScale).sp),
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun LibraryPage(model: OpenListenerViewModel, onOpen: (String) -> Unit) {
    val sessions by model.searchResults.collectAsState()
    var query by rememberSaveable { mutableStateOf(model.searchQuery.value) }
    val list = rememberLazyListState()
    Column(Modifier.fillMaxSize().testTag("library-page"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = query, onValueChange = { query = it; model.searchQuery.value = it },
            label = { Text(tr("搜索", "Search")) }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = ""; model.searchQuery.value = "" }) { Icon(Icons.Outlined.Close, tr("清除搜索", "Clear search")) } },
            modifier = Modifier.fillMaxWidth().testTag("library-search"), shape = RoundedCornerShape(18.dp))
        if (sessions.isEmpty()) EmptyReading(Icons.Outlined.BookmarkBorder,
            if (query.isBlank()) tr("还没有记录", "No records yet") else tr("没有匹配的记录", "No matching records"),
            if (query.isBlank()) ""
            else tr("试试其他标题或正文关键词。", "Try another title or phrase."), modifier = Modifier.weight(1f).testTag("library-empty"))
        else LazyColumn(state = list, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(sessions, key = { it.session.id }) { entry ->
                val session = entry.session
                Surface(onClick = { onOpen(session.id) }, color = MaterialTheme.colorScheme.surface.copy(alpha = .20f), contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text((if (session.audioPath != null) "🎧 " else "") + session.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(entry.preview.ifBlank { tr("暂无转录内容", "No transcript yet") }.lineSequence().joinToString(" "),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("session-preview-${session.id}"))
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
    var shareMenu by rememberSaveable(id) { mutableStateOf(false) }
    var title by rememberSaveable(id) { mutableStateOf("") }
    val list = rememberLazyListState()
    val shareLabel = tr("分享与导出", "Share and export")
    Column(Modifier.fillMaxSize().testTag("session-detail")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("返回记录", "Back to Library")) }
            Text(tr("文字记录", "Text record"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { shareMenu = true }, enabled = session != null, modifier = Modifier.testTag("record-share").semantics { contentDescription = shareLabel }) { Icon(painterResource(R.drawable.ic_share_upload), null, Modifier.size(24.dp)) }
                DropdownMenu(shareMenu, { shareMenu = false }, modifier = Modifier.testTag("record-share-menu")) {
                    DropdownMenuItem(text = { Text(tr("复制全文", "Copy all")) }, onClick = { shareMenu = false; model.copySession(id) })
                    DocumentFormat.entries.forEach { format ->
                        DropdownMenuItem(text = { Text("${tr("导出", "Save")} ${format.extension.uppercase()}") }, onClick = { shareMenu = false; model.requestExport(id, format) })
                        DropdownMenuItem(text = { Text("${tr("分享", "Share")} ${format.extension.uppercase()}") }, onClick = { shareMenu = false; model.shareSession(id, format) })
                    }
                }
            }
            IconButton(onClick = { title = session?.title.orEmpty(); rename = true }, enabled = session != null) { Icon(Icons.Outlined.Edit, tr("修改标题", "Rename")) }
            IconButton(onClick = { delete = true }, enabled = session != null) { Icon(Icons.Outlined.DeleteOutline, tr("删除记录", "Delete record")) }
        }
        val current = session
        if (current == null) {
            EmptyReading(Icons.Outlined.Description, tr("正在读取记录", "Loading record"), tr("文字保存在此设备。", "Text is stored on this device."))
        } else ReadingSurface {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("record-reading-area"), verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(18.dp)) {
            item {
                Text(current.title, style = MaterialTheme.typography.headlineSmall)
                Text("${date(current.startedAt)} · ${formatTime(current.durationMs)} · ${sessionStatus(current.state)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                DisplayModes(mode, { mode = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (current.audioPath != null && current.state !in listOf("PREPARING", "RECORDING", "PAUSED", "STOPPING")) RecordingPlayback(current.audioPath)

                }
            }
            if (segments.isEmpty()) item { Text(tr("这场聆听没有已确认的文字。", "This session has no confirmed text."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(segments, key = { it.sequence }) { segment ->
                TranscriptParagraph(segment, mode, prefs.subtitleFontScale, retrying.contains(segment.sequence), model::copyText,
                    onRetry = { model.retryTranslation(id, segment.sequence) }, canRetry = current.asrProvider != "doubao_ast")
            }
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
internal val LocalConnectedModelPanel = staticCompositionLocalOf { false }

@Composable internal fun ReadingSurface(backgroundAlpha: Float = .20f, content: @Composable () -> Unit) {
    Surface(shape = if (LocalConnectedModelPanel.current) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(20.dp), color = if (LocalConnectedModelPanel.current) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha),
        contentColor = MaterialTheme.colorScheme.onSurface, content = content)
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

@Composable
private fun RecordingPlayback(path: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var player by remember(path) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var playing by remember(path) { mutableStateOf(false) }
    var loading by remember(path) { mutableStateOf(false) }
    var failed by remember(path) { mutableStateOf(false) }
    DisposableEffect(path, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                player?.release(); player = null; playing = false; loading = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player?.release(); player = null }
    }
    TextButton(enabled = !loading, modifier = Modifier.testTag("play-recording"), onClick = {
        failed = false
        val current = player
        if (current != null) {
            if (playing) current.pause() else current.start()
            playing = !playing
        } else try {
            loading = true
            val audio = android.media.MediaPlayer()
            player = audio
            audio.setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            audio.setDataSource(context, android.net.Uri.fromFile(java.io.File(path)))
            audio.setOnPreparedListener { loading = false; playing = true; it.start() }
            audio.setOnCompletionListener { playing = false; it.seekTo(0) }
            audio.setOnErrorListener { media, _, _ -> media.release(); player = null; playing = false; loading = false; failed = true; true }
            audio.prepareAsync()
        } catch (_: Exception) { player?.release(); player = null; loading = false; failed = true }
    }) {
        Text(if (loading) tr("🎧 加载中…", "🎧 Loading…") else if (playing) tr("⏸ 暂停录音", "⏸ Pause recording") else tr("🎧 播放录音", "🎧 Play recording"))
    }
    if (failed) Text(tr("无法播放此录音", "Unable to play recording"), color = MaterialTheme.colorScheme.error)
}

/** Each newly arrived character fades over 90 ms; an unchanged prefix stays opaque. */
@Composable
internal fun ArrivalText(value: String, live: Boolean, style: TextStyle, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val motion = live && LocalMotionAllowed.current
    var previous by remember { mutableStateOf("") }
    val prefix = remember(value) { previous.commonPrefixWith(value).length }
    var started by remember(value) { mutableStateOf(false) }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(value, motion) {
        previous = value
        if (motion) {
            clock.snapTo(0f)
            started = true
            val duration = 90 + minOf((value.length - prefix).coerceAtLeast(0), 30) * 12
            clock.animateTo(duration.toFloat(), tween(duration, easing = LinearEasing))
        }
    }
    val text = if (!motion) androidx.compose.ui.text.AnnotatedString(value) else buildAnnotatedString {
        append(value)
        for (index in prefix until value.length) {
            val opacity = (((if (started) clock.value else 0f) - minOf(index - prefix, 30) * 12) / 90f).coerceIn(0f, 1f)
            addStyle(SpanStyle(color = color.copy(alpha = color.alpha * opacity)), index, index + 1)
        }
    }
    Text(text, color = color, style = style, modifier = modifier)
}
