package com.eminallahverdi.balance.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.feature.home.HomeRoute
import kotlinx.serialization.Serializable

@Serializable data object HomeDestination
@Serializable data object HistoryDestination
@Serializable data object InsightsDestination
@Serializable data object AccountsDestination
@Serializable data object SettingsDestination

/** The main shell: the tab content above the bottom bar. [onAdd] opens quick add. */
@Composable
fun BalanceApp(onAdd: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination

    Column(Modifier.fillMaxSize().background(BtTheme.colors.bgCanvas)) {
        NavHost(nav, startDestination = HomeDestination, modifier = Modifier.weight(1f).statusBarsPadding()) {
            composable<HomeDestination> { HomeRoute(onOpenSettings = { nav.navigate(SettingsDestination) { launchSingleTop = true } }) }
            composable<HistoryDestination> { NotBuiltYet("History") }
            composable<InsightsDestination> { NotBuiltYet("Insights") }
            composable<AccountsDestination> { NotBuiltYet("Accounts") }
            composable<SettingsDestination> { NotBuiltYet("Settings") }
        }
        BottomBar(
            isSelected = { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true },
            onSelect = { tab ->
                nav.navigate(tab.route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onAdd = onAdd,
        )
    }
}

/** Stands in for a screen until its feature module lands. */
@Composable
private fun NotBuiltYet(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = BtTheme.type.titleMedium, color = BtTheme.colors.textMuted)
    }
}
