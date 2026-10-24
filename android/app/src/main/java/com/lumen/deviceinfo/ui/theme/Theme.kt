package com.lumen.deviceinfo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = LumenBackgroundDark,
    secondary = Mint,
    onSecondary = LumenBackgroundDark,
    tertiary = Mint,
    background = LumenBackgroundDark,
    onBackground = LumenTextDark,
    surface = LumenSurfaceDark,
    onSurface = LumenTextDark,
    surfaceVariant = LumenSurface2Dark,
    onSurfaceVariant = LumenTextSecondaryDark,
    outline = LumenBorderDark,
    outlineVariant = LumenBorderDark,
    error = Danger,
    onError = LumenBackgroundDark
)

private val LightColors = lightColorScheme(
    primary = Mint,
    onPrimary = LumenBackgroundDark,
    secondary = Mint,
    onSecondary = LumenBackgroundDark,
    tertiary = Mint,
    background = LumenBackgroundLight,
    onBackground = LumenTextLight,
    surface = LumenSurfaceLight,
    onSurface = LumenTextLight,
    surfaceVariant = LumenSurface2Light,
    onSurfaceVariant = LumenTextSecondaryLight,
    outline = LumenBorderLight,
    outlineVariant = LumenBorderLight,
    error = Danger,
    onError = Color(0xFFFFFFFF)
)

@Composable
fun LumenTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (dark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LumenTypography,
        content = content
    )
}
