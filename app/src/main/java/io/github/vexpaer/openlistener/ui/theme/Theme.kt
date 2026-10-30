package io.github.vexpaer.openlistener.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocalDarkAppearance = staticCompositionLocalOf { false }
val LocalThemeTones = staticCompositionLocalOf { listOf(Color(0xFFDFA58C), Color(0xFFD97757), Color(0xFFF4CFBD)) }

/** OKLCH color harmonization with chroma clamped to the sRGB gamut. */
private fun oklch(l: Double, c: Double, h: Double): Color {
    fun rgb(chroma: Double): DoubleArray {
        val aa = chroma * cos(h); val bb = chroma * sin(h)
        val ll = (l + .3963377774 * aa + .2158037573 * bb).let { it * it * it }
        val mm = (l - .1055613458 * aa - .0638541728 * bb).let { it * it * it }
        val ss = (l - .0894841775 * aa - 1.2914855480 * bb).let { it * it * it }
        return doubleArrayOf(4.0767416621 * ll - 3.3077115913 * mm + .2309699292 * ss,
            -1.2684380046 * ll + 2.6097574011 * mm - .3413193965 * ss,
            -.0041960863 * ll - .7034186147 * mm + 1.7076147010 * ss)
    }
    var chroma = c
    repeat(24) { if (rgb(chroma).all { it in 0.0..1.0 }) return@repeat else chroma *= .88 }
    fun gamma(v: Double) = if (v <= .0031308) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - .055
    val v = rgb(chroma)
    return Color(gamma(v[0]).coerceIn(0.0, 1.0).toFloat(),
        gamma(v[1]).coerceIn(0.0, 1.0).toFloat(), gamma(v[2]).coerceIn(0.0, 1.0).toFloat())
}
fun themeTones(argb: Long, dark: Boolean): List<Color> {
    val color = Color(argb.toInt())
    fun linear(v: Float) = if (v <= .04045f) v / 12.92 else ((v + .055) / 1.055).pow(2.4).toDouble()
    val r = linear(color.red); val g = linear(color.green); val b = linear(color.blue)
    val l0 = .4122214708 * r + .5363325363 * g + .0514459929 * b
    val m0 = .2119034982 * r + .6806995451 * g + .1073969566 * b
    val s0 = .0883024619 * r + .2817188376 * g + .6299787005 * b
    val l = l0.pow(1.0 / 3); val m = m0.pow(1.0 / 3); val s = s0.pow(1.0 / 3)
    val aa = 1.9779984951 * l - 2.4285922050 * m + .4505937099 * s
    val bb = .0259040371 * l + .7827717662 * m - .8086757660 * s
    val h = atan2(bb, aa)
    val chroma = hypot(aa, bb).coerceIn(.045, .18)
    return if (dark) listOf(oklch(.26, chroma * .36, h), oklch(.33, chroma * .48, h), oklch(.22, chroma * .30, h))
        else listOf(oklch(.85, chroma * .55, h), oklch(.77, chroma * .72, h), oklch(.91, chroma * .34, h))
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF406B88), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E9F4), onPrimaryContainer = Color(0xFF182532),
    secondary = Color(0xFF5C6B79), onSecondary = Color.White,
    background = Color(0xFFF3F6F8), onBackground = Color(0xFF182532),
    surface = Color(0xFFF9FBFC), onSurface = Color(0xFF182532),
    surfaceVariant = Color(0xFFE0E8ED), onSurfaceVariant = Color(0xFF40505E),
    outline = Color(0xFF687F8E), outlineVariant = Color(0xFFCAD6DF),
    error = Color(0xFFAB343C), onError = Color.White
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5C8E3), onPrimary = Color(0xFF0B2637),
    primaryContainer = Color(0xFF2B465F), onPrimaryContainer = Color(0xFFE7F2FB),
    secondary = Color(0xFFADB6C3), onSecondary = Color(0xFF1A2732),
    background = Color(0xFF111827), onBackground = Color(0xFFF5F6FA),
    surface = Color(0xFF111827), onSurface = Color(0xFFF5F6FA),
    surfaceVariant = Color(0xFF253456), onSurfaceVariant = Color(0xFFCFD6DF),
    outline = Color(0xFF96A4B9), outlineVariant = Color(0xFF3F5069),
    error = Color(0xFFF18F91), onError = Color(0xFF46151B)
)
private val EditorialTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 40.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 23.sp, lineHeight = 30.sp, letterSpacing = (-.4).sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 23.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp)
)

@Composable
fun OpenListenerTheme(themeMode: String, reducedMotion: Boolean, themeColor: Long, content: @Composable () -> Unit) {
    val dark = when (themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    CompositionLocalProvider(LocalDarkAppearance provides dark, LocalThemeTones provides themeTones(themeColor, dark)) {
        val primary = Color(themeColor.toInt())
        val scheme = if (dark) DarkColors.copy(primary = themeTones(themeColor, false)[1], primaryContainer = themeTones(themeColor, true)[1]) else LightColors.copy(primary = primary, primaryContainer = themeTones(themeColor, false)[0])
        MaterialTheme(colorScheme = scheme, typography = EditorialTypography, content = content)
    }
}
