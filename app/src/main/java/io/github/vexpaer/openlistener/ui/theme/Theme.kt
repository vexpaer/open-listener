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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocalDarkAppearance = staticCompositionLocalOf { false }

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
fun OpenListenerTheme(themeMode: String, reducedMotion: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    CompositionLocalProvider(LocalDarkAppearance provides dark) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = EditorialTypography, content = content)
    }
}
