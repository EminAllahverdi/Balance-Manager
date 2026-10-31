package com.eminallahverdi.balance.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.feature.catalog.CatalogRoute
import com.eminallahverdi.balance.feature.home.HomeRoute
import kotlinx.serialization.Serializable

@Serializable data object HomeDestination
@Serializable data object HistoryDestination
@Serializable data object InsightsDestination
@Serializable data object AccountsDestination
@Serializable data object SettingsDestination
@Serializable data object CatalogDestination

/** The main shell: the tab content above the bottom bar. [onAdd] opens quick add. */
@Composable
fun BalanceApp(onAdd: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val isSelected = { tab: TopLevelTab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }

    Column(Modifier.fillMaxSize().background(BtTheme.colors.bgCanvas)) {
        NavHost(nav, startDestination = HomeDestination, modifier = Modifier.weight(1f).statusBarsPadding()) {
            composable<HomeDestination> { HomeRoute(onOpenSettings = { nav.navigate(SettingsDestination) { launchSingleTop = true } }) }
            composable<HistoryDestination> { NotBuiltYet("History") }
            composable<InsightsDestination> { NotBuiltYet("Insights") }
            composable<AccountsDestination> { NotBuiltYet("Accounts") }
            composable<SettingsDestination> {
                SettingsPlaceholder(
                    onBack = { nav.popBackStack() },
                    onOpenCatalog = { nav.navigate(CatalogDestination) { launchSingleTop = true } },
                )
            }
            composable<CatalogDestination> { CatalogRoute(onBack = { nav.popBackStack() }) }
        }
        // Settings and its sub-screens are full screen.
        if (destination == null || TopLevelTab.entries.any(isSelected)) {
            BottomBar(
                isSelected = isSelected,
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
}

/** Stands in for a screen until its feature module lands. */
@Composable
private fun NotBuiltYet(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = BtTheme.type.titleMedium, color = BtTheme.colors.textMuted)
    }
}

/** Just the way into categories and people, until feature:settings lands. */
@Composable
private fun SettingsPlaceholder(onBack: () -> Unit, onOpenCatalog: () -> Unit) {
    val c = BtTheme.colors
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Text("Settings", style = BtTheme.type.titleLarge, color = c.textPrimary)
        }
        BtCard {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenCatalog).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Rounded.Category, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
                Text("Categories & people", style = BtTheme.type.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}
