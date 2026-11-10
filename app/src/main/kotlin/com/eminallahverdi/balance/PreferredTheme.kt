package com.eminallahverdi.balance

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.datastore.ThemePreference
import com.eminallahverdi.balance.core.datastore.UserPreferences
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.designsystem.theme.BalanceTheme
import com.eminallahverdi.balance.core.designsystem.theme.ThemeMode

/** [BalanceTheme] in the mode chosen in Settings, with system bar icons that stay readable on it, in the language chosen there. */
@Composable
fun PreferredTheme(prefs: UserPreferencesRepository, content: @Composable () -> Unit) {
    val preferences by prefs.preferences.collectAsStateWithLifecycle(UserPreferences())
    val mode = when (preferences.theme) {
        ThemePreference.DARK -> ThemeMode.DARK
        ThemePreference.LIGHT -> ThemeMode.LIGHT
        ThemePreference.SYSTEM -> ThemeMode.SYSTEM
    }
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    BalanceTheme(mode) { LocalizedContent(content) }
}
