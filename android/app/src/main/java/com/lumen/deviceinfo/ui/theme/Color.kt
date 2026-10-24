package com.lumen.deviceinfo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta «ColorOS 17 / Living Flow» (ver DESIGN-COLOROS17.md).
 * Tema claro por defecto; oscuro disponible en Ajustes.
 */

// ===== Tema claro =====
val BgLight = Color(0xFFF4F8FA)
val BlobAquaLight = Color(0xFFB9F5E4)
val BlobSkyLight = Color(0xFFC9DDFF)
val BlobLavenderLight = Color(0xFFE8DBFF)
val SurfaceLight = Color(0xFFFFFFFF)
val GlassSurfaceLight = Color(0xFFFFFFFF) // opaco: evita ghosting de texto en render software
val GlassBorderLight = Color(0xFFE2ECF0)
val HeroStartLight = Color(0xFFDDF7F1)
val HeroEndLight = Color(0xFFE0EBFB)
val TextLight = Color(0xFF0C1B22)
val TextSecondaryLight = Color(0xFF5C6B76)
val TextTertiaryLight = Color(0xFF8A99A3)
val PrimaryAquaLight = Color(0xFF00BFA6)
val PrimaryDeepLight = Color(0xFF008F7D)
val SecondaryBlueLight = Color(0xFF3B82F6)
val LavenderLight = Color(0xFF8B5CF6)

// ===== Tema oscuro =====
val BgDark = Color(0xFF070C10)
val BlobAquaDark = Color(0xFF0E5F55)
val BlobSkyDark = Color(0xFF12325F)
val BlobLavenderDark = Color(0xFF3A2A66)
val SurfaceDark = Color(0xFF0D1318)
val GlassSurfaceDark = Color(0xFF0F151B) // opaco: evita ghosting de texto en render software
val GlassBorderDark = Color(0x1AFFFFFF) // 10 %
val HeroStartDark = Color(0xFF12342E)
val HeroEndDark = Color(0xFF142640)
val TextDark = Color(0xFFECF6F8)
val TextSecondaryDark = Color(0xFF93A4AE)
val TextTertiaryDark = Color(0xFF6B7A84)
val PrimaryAquaDark = Color(0xFF4DE0C8)
val PrimaryDeepDark = Color(0xFF3DC0AE)
val SecondaryBlueDark = Color(0xFF6AA1FF)
val LavenderDark = Color(0xFFB69CFF)

// ===== Semánticos (compartidos) =====
val Success = Color(0xFF22C55E)
val Warning = Color(0xFFF59E0B)
val Danger = Color(0xFFEF4444)

/** Colores extra que no caben en el ColorScheme estándar de Material 3. */
@Immutable
data class LumenColors(
    val blobAqua: Color,
    val blobSky: Color,
    val blobLavender: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val textTertiary: Color,
    val primaryDeep: Color,
    val secondary: Color,
    val lavender: Color,
    val warning: Color,
    val success: Color,
    val danger: Color
)

val LumenColorsLight = LumenColors(
    blobAqua = BlobAquaLight,
    blobSky = BlobSkyLight,
    blobLavender = BlobLavenderLight,
    glassSurface = GlassSurfaceLight,
    glassBorder = GlassBorderLight,
    heroStart = HeroStartLight,
    heroEnd = HeroEndLight,
    textTertiary = TextTertiaryLight,
    primaryDeep = PrimaryDeepLight,
    secondary = SecondaryBlueLight,
    lavender = LavenderLight,
    warning = Warning,
    success = Success,
    danger = Danger
)

val LumenColorsDark = LumenColors(
    blobAqua = BlobAquaDark.copy(alpha = 0.22f),
    blobSky = BlobSkyDark.copy(alpha = 0.20f),
    blobLavender = BlobLavenderDark.copy(alpha = 0.18f),
    glassSurface = GlassSurfaceDark,
    glassBorder = GlassBorderDark,
    heroStart = HeroStartDark,
    heroEnd = HeroEndDark,
    textTertiary = TextTertiaryDark,
    primaryDeep = PrimaryDeepDark,
    secondary = SecondaryBlueDark,
    lavender = LavenderDark,
    warning = Warning,
    success = Success,
    danger = Danger
)

val LocalLumenColors = staticCompositionLocalOf { LumenColorsLight }
