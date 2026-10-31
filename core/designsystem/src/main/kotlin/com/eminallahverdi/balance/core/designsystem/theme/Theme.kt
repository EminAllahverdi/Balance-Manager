package com.eminallahverdi.balance.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

enum class ThemeMode { DARK, LIGHT, SYSTEM }

object BtShapes {
    val sm = RoundedCornerShape(10.dp)
    val icon = RoundedCornerShape(12.dp)
    val key = RoundedCornerShape(14.dp)
    val md = RoundedCornerShape(18.dp)
    val lg = RoundedCornerShape(28.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val full = RoundedCornerShape(percent = 50)
}

object BtSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object BtTheme {
    val colors: BtColors
        @Composable @ReadOnlyComposable get() = LocalBtColors.current
    val type: BtTypography
        @Composable @ReadOnlyComposable get() = LocalBtTypography.current
}

@Composable
fun BalanceTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkBtColors else LightBtColors
    val type = BtTypography()
    // Material components (dialogs, date picker, text fields) get a scheme derived from our tokens.
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accentFill, onPrimary = colors.accentOn,
            background = colors.bgCanvas, onBackground = colors.textPrimary,
            surface = colors.bgSurface, onSurface = colors.textPrimary,
            surfaceVariant = colors.bgSurfaceRaised, onSurfaceVariant = colors.textSecondary,
            surfaceContainerHigh = colors.bgSurfaceHigh, surfaceContainer = colors.bgSurfaceRaised,
            outline = colors.borderStrong, outlineVariant = colors.borderDefault,
            error = colors.moneyExpense,
        )
    } else {
        lightColorScheme(
            primary = colors.accentText, onPrimary = colors.bgSurface,
            primaryContainer = colors.accentFill, onPrimaryContainer = colors.accentOn,
            background = colors.bgCanvas, onBackground = colors.textPrimary,
            surface = colors.bgSurface, onSurface = colors.textPrimary,
            surfaceVariant = colors.bgSurfaceRaised, onSurfaceVariant = colors.textSecondary,
            surfaceContainerHigh = colors.bgSurfaceHigh, surfaceContainer = colors.bgSurfaceRaised,
            outline = colors.borderStrong, outlineVariant = colors.borderDefault,
            error = colors.moneyExpense,
        )
    }
    val m3Type = Typography(
        bodyLarge = type.body, bodyMedium = type.body, bodySmall = type.caption,
        titleLarge = type.titleLarge, titleMedium = type.titleMedium, titleSmall = type.bodyStrong,
        labelLarge = type.label, labelMedium = type.label, labelSmall = type.caption,
        headlineSmall = type.titleLarge,
    )
    CompositionLocalProvider(LocalBtColors provides colors, LocalBtTypography provides type) {
        MaterialTheme(colorScheme = scheme, typography = m3Type, content = content)
    }
}
