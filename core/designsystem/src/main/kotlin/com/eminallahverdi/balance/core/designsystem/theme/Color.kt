package com.eminallahverdi.balance.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs

/** How strongly a tile shows its hue behind the icon. */
const val TILE_TINT = 0.16f

/** The 3:1 that icons need to read on their background. */
private const val GLYPH_CONTRAST = 3f
private const val LIGHTNESS_STEP = 0.01f

/**
 * Semantic colour tokens. Names and values mirror docs/design/tokens.json (the design system artifact).
 * Never use raw hex in features: read BtTheme.colors.
 */
@Immutable
data class BtColors(
    val bgCanvas: Color,
    val bgSurface: Color,
    val bgSurfaceRaised: Color,
    val bgSurfaceHigh: Color,
    val borderDefault: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentFill: Color,
    val accentOn: Color,
    val accentText: Color,
    val moneyExpense: Color,
    val moneyIncome: Color,
    val statusWarning: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val DarkBtColors = BtColors(
    bgCanvas = Color(0xFF0B0D10),
    bgSurface = Color(0xFF121519),
    bgSurfaceRaised = Color(0xFF181C21),
    bgSurfaceHigh = Color(0xFF1F242B),
    borderDefault = Color(0xFF1F242B),
    borderStrong = Color(0xFF2A3038),
    textPrimary = Color(0xFFF2F4F7),
    textSecondary = Color(0xFFAEB6C1),
    textMuted = Color(0xFF8A94A3),
    accentFill = Color(0xFFC8F169),
    accentOn = Color(0xFF0B0D10),
    accentText = Color(0xFFC8F169),
    moneyExpense = Color(0xFFFF7A66),
    moneyIncome = Color(0xFF4ADE9B),
    statusWarning = Color(0xFFF5B544),
    scrim = Color(0x9E000000),
    isDark = true,
)

val LightBtColors = BtColors(
    bgCanvas = Color(0xFFF7F8F5),
    bgSurface = Color(0xFFFFFFFF),
    bgSurfaceRaised = Color(0xFFF1F2EE),
    bgSurfaceHigh = Color(0xFFE3E6E1),
    borderDefault = Color(0xFFE3E6E1),
    borderStrong = Color(0xFFAEB6C1),
    textPrimary = Color(0xFF0B0D10),
    textSecondary = Color(0xFF5C6570),
    textMuted = Color(0xFF5C6570),
    accentFill = Color(0xFFC8F169),
    accentOn = Color(0xFF0B0D10),
    accentText = Color(0xFF4A7A00),
    moneyExpense = Color(0xFFC2412D),
    moneyIncome = Color(0xFF11845B),
    statusWarning = Color(0xFFA86A00),
    scrim = Color(0x730B0D10),
    isDark = false,
)

/**
 * The icon on a tile of this hue. Pale hues like Transport's yellow fade on a light surface, so there they lose lightness
 * until they stand out, and stay the same hue. Dark surfaces take them as they are.
 */
fun BtColors.tileGlyph(hue: Color): Color {
    if (isDark) return hue
    val tile = hue.copy(alpha = TILE_TINT).compositeOver(bgSurface)
    val max = maxOf(hue.red, hue.green, hue.blue)
    val min = minOf(hue.red, hue.green, hue.blue)
    val chroma = max - min
    val lightness = (max + min) / 2
    val saturation = if (chroma == 0f) 0f else (chroma / (1 - abs(2 * lightness - 1))).coerceAtMost(1f)
    val degrees = when {
        chroma == 0f -> 0f
        max == hue.red -> ((hue.green - hue.blue) / chroma).mod(6f) * 60
        max == hue.green -> ((hue.blue - hue.red) / chroma + 2) * 60
        else -> ((hue.red - hue.green) / chroma + 4) * 60
    }
    if (contrast(hue, tile) >= GLYPH_CONTRAST) return hue
    return generateSequence(lightness) { it - LIGHTNESS_STEP }
        .takeWhile { it > 0f }
        .map { Color.hsl(degrees, saturation, it) }
        .firstOrNull { contrast(it, tile) >= GLYPH_CONTRAST } ?: hue
}

private fun contrast(a: Color, b: Color): Float {
    val lighter = maxOf(a.luminance(), b.luminance())
    val darker = minOf(a.luminance(), b.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** Default colours offered when creating a person. Users can pick any of these. */
val PersonPalette = listOf(
    0xFF5B9DFF, 0xFFFF7AB6, 0xFFFFB547, 0xFFA78BFA, 0xFF8A94A3,
    0xFF4ADE9B, 0xFFFF7A66, 0xFF6FC7D9, 0xFFF7A072, 0xFFC79BF2,
)

/** Default hues offered for categories (icon tile = hue at 16 % behind the glyph in full hue). */
val CategoryPalette = listOf(
    0xFF5DCAA5, 0xFFFF7A66, 0xFF8AB4F8, 0xFFB39DFF, 0xFFF5B544, 0xFFFFD166,
    0xFFFF9F6E, 0xFF7FD1F7, 0xFFFFB547, 0xFF9AD0A8, 0xFFF49AC2, 0xFFC79BF2,
    0xFF6FC7D9, 0xFFF7A072, 0xFFB8C0CC, 0xFF8A94A3,
)

internal val LocalBtColors = staticCompositionLocalOf { DarkBtColors }
