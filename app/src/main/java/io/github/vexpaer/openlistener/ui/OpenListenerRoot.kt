package io.github.vexpaer.openlistener.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sqrt
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.vexpaer.openlistener.service.MicrophoneRecognitionService.State
import io.github.vexpaer.openlistener.ui.theme.LocalDarkAppearance
import java.util.Locale

internal val LocalChinese = staticCompositionLocalOf { true }
internal val LocalMotionAllowed = staticCompositionLocalOf { false }
@Composable internal fun tr(zh: String, en: String): String = if (LocalChinese.current) zh else en

@Composable
fun OpenListenerRoot(viewModel: OpenListenerViewModel, foreground: Boolean) {
    val preferences by viewModel.preferences.collectAsState()
    val configuration = LocalConfiguration.current
    val chinese = when (preferences.appLanguage) {
        "zh" -> true; "en" -> false; else -> configuration.locales[0].language == "zh"
    }
    val environment = rememberMotionEnvironment()
    val motion = foreground && !preferences.reducedMotion && !environment.lowPower && environment.animatorScale > 0f
    CompositionLocalProvider(LocalChinese provides chinese, LocalMotionAllowed provides motion) {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route.orEmpty()
        val pager = rememberPagerState { 3 }
        val scope = rememberCoroutineScope()
        val selected = if (route.startsWith("detail")) 1 else pager.currentPage
        val notice by viewModel.notice.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        var entered by rememberSaveable { mutableStateOf(false) }
        val entrance by animateFloatAsState(if (entered || !motion) 1f else .4f, tween(if (motion) 550 else 0), label = "first-open")
        LaunchedEffect(Unit) { entered = true }
        LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }
        LaunchedEffect(preferences.asrProvider, preferences.localAsrModel, preferences.translationProvider, preferences.sourceLanguage, preferences.targetLanguage) {
            viewModel.checkSelectedEngines()
        }
        val navigateTop: (String) -> Unit = { destination ->
            if (route != "home") nav.popBackStack("home", false)
            scope.launch { pager.animateScrollToPage(listOf("listen", "library", "settings").indexOf(destination)) }
        }
        Box(Modifier.fillMaxSize()) {
            AmbientSky(motion, foreground)
            Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, snackbarHost = { SnackbarHost(snackbar) },
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).widthIn(max = 840.dp).align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp).alpha(entrance)) {
                    val routes = listOf("listen", "library", "settings")
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp).clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = .55f))) {
                    RecordingBars(viewModel, foreground, motion)
                    TabRow(selectedTabIndex = selected, containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface, divider = {},
                        indicator = { positions ->
                            Box(Modifier.tabIndicatorOffset(positions[selected]).padding(4.dp).fillMaxHeight()
                                .clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)))
                        }, modifier = Modifier.fillMaxWidth()) {
                        listOf(tr("录音", "Listen"), tr("记录", "Library"), tr("设置", "Settings")).forEachIndexed { index, label ->
                            // The moving pill is the only selection/press background.
                            Box(Modifier.heightIn(min = 52.dp).testTag("tab-${routes[index]}")
                                .selectable(selected = selected == index, role = Role.Tab,
                                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                                    onClick = { navigateTop(routes[index]) }), contentAlignment = Alignment.Center) {
                                Text(label, fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                    }
                    NavHost(navController = nav, startDestination = "home", modifier = Modifier.weight(1f),
                        enterTransition = { fadeIn(tween(if (motion) 220 else 0)) },
                        exitTransition = { fadeOut(tween(if (motion) 160 else 0)) },
                        popEnterTransition = { fadeIn(tween(if (motion) 220 else 0)) },
                        popExitTransition = { fadeOut(tween(if (motion) 160 else 0)) }) {
                        composable("home") {
                            HorizontalPager(state = pager, beyondViewportPageCount = 2, pageSpacing = 12.dp,
                                modifier = Modifier.fillMaxSize().testTag("main-pager")) { page ->
                                when (page) {
                                    0 -> ListenPage(viewModel, foreground && pager.currentPage == 0, onSettings = { navigateTop("settings") })
                                    1 -> LibraryPage(viewModel) { id -> nav.navigate("detail/$id") }
                                    2 -> SettingsPage(viewModel, active = pager.currentPage == 2)
                                }
                            }
                        }
                        composable("detail/{id}") { entry ->
                            val id = entry.arguments?.getString("id").orEmpty()
                            SessionDetailPage(id, viewModel, onBack = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun stateLabel(state: State): String = when (state) {
    State.RECORDING -> tr("聆听中", "LIVE")
    State.PAUSED -> tr("已暂停", "Paused")
    State.PREPARING -> tr("准备中", "Preparing")
    State.STOPPING -> tr("保存中", "Saving")
    State.ERROR -> tr("出错", "Error")
    State.INTERRUPTED -> tr("已中断", "Interrupted")
    else -> tr("就绪", "Ready")
}

private data class MotionEnvironment(val lowPower: Boolean = false, val animatorScale: Float = 1f)

@Composable
private fun rememberMotionEnvironment(): MotionEnvironment {
    val context = LocalContext.current
    var environment by remember { mutableStateOf(MotionEnvironment()) }
    DisposableEffect(context) {
        fun refresh() {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val power = context.getSystemService(PowerManager::class.java)
            environment = MotionEnvironment(power.isPowerSaveMode || level.toFloat() / scale.coerceAtLeast(1) <= .15f,
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
        }
        val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) = refresh() }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) = refresh() }
        context.registerReceiver(receiver, IntentFilter().apply { addAction(Intent.ACTION_BATTERY_CHANGED); addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) })
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        refresh()
        onDispose { context.unregisterReceiver(receiver); context.contentResolver.unregisterContentObserver(observer) }
    }
    return environment
}

/** Soft blue light and fine grain, drawn natively without a full-screen blur pass. */
@Composable
private fun AmbientSky(motion: Boolean, foreground: Boolean) {
    val dark = LocalDarkAppearance.current
    val phase = remember { Animatable(0f) }
    val grain = remember {
        val random = Random(214)
        val pixels = IntArray(384 * 512) {
            (random.nextInt(5, 17) shl 24) or if (random.nextBoolean()) 0xFFFFFF else 0
        }
        android.graphics.Bitmap.createBitmap(pixels, 384, 512, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    LaunchedEffect(motion, foreground) {
        if (motion && foreground) while (true) {
            phase.animateTo((2 * PI).toFloat(), tween(160_000, easing = LinearEasing))
            phase.snapTo(0f) // Both ends describe the same sin/cos positions.
        }
    }
    Canvas(Modifier.fillMaxSize().testTag("ambient-sky")) {
        val t = phase.value // Read animation state during drawing, without recomposing the UI.
        drawRect(Brush.linearGradient(
            if (dark) listOf(Color(0xFF15253C), Color(0xFF243F5D), Color(0xFF101E33))
            else listOf(Color(0xFFAACBE9), Color(0xFF85B2D8), Color(0xFFC5DFEE)),
            start = Offset.Zero, end = Offset(size.width, size.height)))
        fun haze(x: Float, y: Float, rx: Float, ry: Float, color: Color) {
            withTransform({
                translate(size.width * x, size.height * y)
                scale(rx * size.width, ry * size.height, pivot = Offset.Zero)
            }) {
                drawCircle(Brush.radialGradient(
                    0f to color, .28f to color.copy(alpha = color.alpha * .82f),
                    .62f to color.copy(alpha = color.alpha * .30f), 1f to Color.Transparent,
                    center = Offset.Zero, radius = 1f), radius = 1f, center = Offset.Zero)
            }
        }
        val white = if (dark) Color(0xFFAACEE9).copy(alpha = .13f) else Color(0xFFF7FBFF).copy(alpha = .70f)
        val blue = if (dark) Color(0xFF5B88B8).copy(alpha = .13f) else Color(0xFF6197C7).copy(alpha = .25f)
        haze(.18f + .09f * sin(t), .13f + .05f * cos(t), .95f, .26f, white)
        haze(.66f + .08f * cos(t + 1f), .32f + .06f * sin(t), .66f, .39f, blue)
        haze(.30f + .10f * cos(t + 2f), .56f + .07f * sin(t + 1f), .85f, .30f, white)
        haze(.85f + .06f * sin(t + 3f), .78f + .08f * cos(t), .70f, .36f, white.copy(alpha = white.alpha * .85f))
        haze(.05f + .08f * cos(t), .95f + .07f * sin(t + 2f), .85f, .29f, blue)
        drawImage(grain, dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            alpha = if (dark) .32f else .55f, filterQuality = FilterQuality.Low)
    }
}

internal fun formatTime(ms: Long): String = if (ms >= 3_600_000) "%d:%02d:%02d".format(Locale.ROOT, ms / 3_600_000, ms / 60_000 % 60, ms / 1_000 % 60)
    else "%02d:%02d".format(Locale.ROOT, ms.coerceAtLeast(0) / 60_000, ms.coerceAtLeast(0) / 1_000 % 60)
internal fun date(ms: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(ms))
@Composable internal fun languageName(code: String): String = when (code.lowercase()) {
    "en" -> tr("英语", "English"); "zh", "zh-cn" -> tr("简体中文", "Chinese"); "ja" -> tr("日语", "Japanese"); "ko" -> tr("韩语", "Korean")
    "fr" -> tr("法语", "French"); "de" -> tr("德语", "German"); "es" -> tr("西班牙语", "Spanish"); else -> code
}

@Composable
private fun BoxScope.RecordingBars(model: OpenListenerViewModel, foreground: Boolean, motion: Boolean) {
    val state by model.serviceState.collectAsState()
    val level by model.level.collectAsState()
    val latest by rememberUpdatedState(level)
    var samples by remember { mutableStateOf(List(48) { 0f }) }
    val active = state == State.RECORDING && foreground
    LaunchedEffect(active, motion) {
        samples = List(48) { 0f }
        if (active && motion) while (isActive) {
            samples = samples.drop(1) + (sqrt(latest.coerceAtLeast(0f)) * 3f).coerceIn(0f, 1f)
            delay(75)
        }
    }
    val color = MaterialTheme.colorScheme.primary.copy(alpha = .15f)
    if (active) Canvas(Modifier.matchParentSize().testTag("recording-bars")) {
        val step = size.width / samples.size
        samples.forEachIndexed { index, sample ->
            val value = if (motion) sample else (sqrt(level.coerceAtLeast(0f)) * 3f).coerceIn(0f, 1f)
            val height = size.height * .82f * value
            if (height > .5f) drawRoundRect(color, topLeft = Offset(index * step + step * .20f, size.height - height),
                size = Size(step * .60f, height), cornerRadius = CornerRadius(step * .3f))
        }
    }
}
