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
import kotlinx.coroutines.delay
import java.util.Calendar
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
        val selected = when { route.startsWith("library") || route.startsWith("detail") -> 1; route == "settings" -> 2; else -> 0 }
        val notice by viewModel.notice.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        var entered by rememberSaveable { mutableStateOf(false) }
        val entrance by animateFloatAsState(if (entered || !motion) 1f else .4f, tween(if (motion) 550 else 0), label = "first-open")
        LaunchedEffect(Unit) { entered = true }
        LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }
        LaunchedEffect(preferences.asrProvider, preferences.translationProvider, preferences.sourceLanguage, preferences.targetLanguage) {
            viewModel.checkSelectedEngines()
        }
        val navigateTop: (String) -> Unit = { destination ->
            nav.navigate(destination) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        Box(Modifier.fillMaxSize()) {
            AmbientSky(motion, foreground)
            Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, snackbarHost = { SnackbarHost(snackbar) },
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).widthIn(max = 840.dp).align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp).alpha(entrance)) {
                    val routes = listOf("listen", "library", "settings")
                    TabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .55f),
                        contentColor = MaterialTheme.colorScheme.onSurface, divider = {},
                        indicator = { positions ->
                            Box(Modifier.tabIndicatorOffset(positions[selected]).padding(4.dp).fillMaxHeight()
                                .clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)))
                        }, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp).clip(RoundedCornerShape(percent = 50))) {
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
                    NavHost(navController = nav, startDestination = "listen", modifier = Modifier.weight(1f),
                        enterTransition = { fadeIn(tween(if (motion) 220 else 0)) },
                        exitTransition = { fadeOut(tween(if (motion) 160 else 0)) },
                        popEnterTransition = { fadeIn(tween(if (motion) 220 else 0)) },
                        popExitTransition = { fadeOut(tween(if (motion) 160 else 0)) }) {
                        composable("listen") { ListenPage(viewModel, foreground, onSettings = { navigateTop("settings") }) }
                        composable("library") { LibraryPage(viewModel) { id -> nav.navigate("detail/$id") } }
                        composable("settings") { SettingsPage(viewModel) }
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

@Composable
private fun AmbientSky(motion: Boolean, foreground: Boolean) {
    val dark = LocalDarkAppearance.current
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(foreground) {
        hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (foreground) while (true) { delay(60_000); hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    }
    val palette = when {
        dark -> when (hour) {
            in 5..9 -> listOf(Color(0xFF252C49), Color(0xFF41445B), Color(0xFF564754))
            in 10..16 -> listOf(Color(0xFF111B37), Color(0xFF253456), Color(0xFF3F405A))
            in 17..20 -> listOf(Color(0xFF232543), Color(0xFF45405B), Color(0xFF5B414C))
            else -> listOf(Color(0xFF10182F), Color(0xFF1B2843), Color(0xFF30354A))
        }
        else -> when (hour) {
            in 5..9 -> listOf(Color(0xFFC7D5E1), Color(0xFFE9E5E8), Color(0xFFF6E3D7))
            in 10..16 -> listOf(Color(0xFFB7CDDC), Color(0xFFE0E8ED), Color(0xFFF8EEE8))
            in 17..20 -> listOf(Color(0xFFC9CBDC), Color(0xFFE8D8DD), Color(0xFFF4DCCB))
            else -> listOf(Color(0xFFB8C4D7), Color(0xFFD5DEEA), Color(0xFFE8E3EB))
        }
    }
    val drift = remember { Animatable(0f) }
    LaunchedEffect(motion) {
        if (motion) while (true) { drift.animateTo(1f, tween(60_000, easing = LinearEasing)); drift.animateTo(0f, tween(60_000, easing = LinearEasing)) }
    }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(palette))
        val position = Offset(size.width * (.65f + drift.value * .12f), size.height * .14f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) .035f else .18f), Color.Transparent), position, size.width * .85f),
            size.width * .85f, position)
        val lower = Offset(size.width * (.1f + drift.value * .1f), size.height * .8f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFF1B7A5).copy(alpha = if (dark) .035f else .12f), Color.Transparent), lower, size.width * .75f),
            size.width * .75f, lower)
    }
}

internal fun formatTime(ms: Long): String = if (ms >= 3_600_000) "%d:%02d:%02d".format(Locale.ROOT, ms / 3_600_000, ms / 60_000 % 60, ms / 1_000 % 60)
    else "%02d:%02d".format(Locale.ROOT, ms.coerceAtLeast(0) / 60_000, ms.coerceAtLeast(0) / 1_000 % 60)
internal fun date(ms: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(ms))
@Composable internal fun languageName(code: String): String = when (code.lowercase()) {
    "en" -> tr("英语", "English"); "zh", "zh-cn" -> tr("简体中文", "Chinese"); "ja" -> tr("日语", "Japanese"); "ko" -> tr("韩语", "Korean")
    "fr" -> tr("法语", "French"); "de" -> tr("德语", "German"); "es" -> tr("西班牙语", "Spanish"); else -> code
}
