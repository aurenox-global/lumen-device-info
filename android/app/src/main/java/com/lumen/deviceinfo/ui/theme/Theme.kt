package com.lumen.deviceinfo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val DarkColors = darkColorScheme(
    primary = PrimaryAquaDark,
    onPrimary = Color(0xFF00251E),
    primaryContainer = PrimaryDeepDark,
    onPrimaryContainer = Color(0xFFC8FFF3),
    secondary = SecondaryBlueDark,
    onSecondary = Color(0xFF001B3D),
    tertiary = LavenderDark,
    onTertiary = Color(0xFF1D0A45),
    background = BgDark,
    onBackground = TextDark,
    surface = SurfaceDark,
    onSurface = TextDark,
    surfaceVariant = Color(0xFF141B21),
    onSurfaceVariant = TextSecondaryDark,
    outline = GlassBorderDark,
    outlineVariant = Color(0xFF1C262E),
    error = Danger,
    onError = Color(0xFF2B0A0A)
)

private val LightColors = lightColorScheme(
    primary = PrimaryAquaLight,
    onPrimary = Color(0xFF00251E),
    primaryContainer = Color(0xFF9DF5E5),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = SecondaryBlueLight,
    onSecondary = Color(0xFFFFFFFF),
    tertiary = LavenderLight,
    onTertiary = Color(0xFFFFFFFF),
    background = BgLight,
    onBackground = TextLight,
    surface = SurfaceLight,
    onSurface = TextLight,
    surfaceVariant = Color(0xFFE8EFF2),
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFD6E0E5),
    outlineVariant = Color(0xFFDEE8EC),
    error = Danger,
    onError = Color(0xFFFFFFFF)
)

@Composable
fun LumenTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (dark) DarkColors else LightColors
    val lumenColors = if (dark) LumenColorsDark else LumenColorsLight
    CompositionLocalProvider(LocalLumenColors provides lumenColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LumenTypography,
            content = content
        )
    }
}
