package io.github.vexpaer.openlistener.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
internal fun ThemeColorPicker(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val initialHsv = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) } }
    var hue by rememberSaveable { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(initialHsv[2]) }
    val selected = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)).toLong() and 0xFFFFFFFFL
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(tr("主题颜色", "Accent color")) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val description = tr("选色盘", "Color wheel")
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f).testTag("theme-color-wheel")
                    .semantics { contentDescription = description }
                    .pointerInput(Unit) {
                        fun select(point: Offset) {
                            val radius = min(size.width, size.height) / 2f
                            val delta = point - Offset(size.width / 2f, size.height / 2f)
                            hue = ((atan2(delta.y, delta.x) * 180f / PI.toFloat()) + 360f) % 360f
                            saturation = (delta.getDistance() / radius).coerceIn(0f, 1f)
                        }
                        detectTapGestures { select(it) }
                    }
                    .pointerInput(Unit) {
                        fun select(point: Offset) {
                            val radius = min(size.width, size.height) / 2f
                            val delta = point - Offset(size.width / 2f, size.height / 2f)
                            hue = ((atan2(delta.y, delta.x) * 180f / PI.toFloat()) + 360f) % 360f
                            saturation = (delta.getDistance() / radius).coerceIn(0f, 1f)
                        }
                        detectDragGestures(onDragStart = { select(it) }) { change, _ ->
                            change.consume(); select(change.position)
                        }
                    }) {
                    val radius = size.minDimension / 2f
                    drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                    drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), radius = radius))
                    drawCircle(Color.Black.copy(alpha = 1f - brightness))
                    val angle = hue * PI.toFloat() / 180f
                    val handle = center + Offset(cos(angle), sin(angle)) * (saturation * (radius - 5.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = .65f), 8.dp.toPx(), handle, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White, 8.dp.toPx(), handle, style = Stroke(1.5.dp.toPx()))
                }
                Text(tr("亮度", "Brightness"), Modifier.align(Alignment.Start))
                Slider(brightness, { brightness = it }, modifier = Modifier.testTag("theme-color-brightness"))
                Surface(color = Color(selected.toInt()), shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(36.dp).testTag("theme-color-preview")) {}
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }, modifier = Modifier.testTag("theme-color-apply")) { Text(tr("应用", "Apply")) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("theme-color-cancel")) { Text(tr("取消", "Cancel")) } })
}
