package com.eminallahverdi.balance.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.feature.catalog.CatalogRoute
import com.eminallahverdi.balance.feature.history.HistoryRoute
import com.eminallahverdi.balance.feature.home.HomeRoute
import com.eminallahverdi.balance.feature.settings.SettingsRoute
import kotlinx.serialization.Serializable

@Serializable data object HomeDestination
@Serializable data object HistoryDestination
@Serializable data object InsightsDestination
@Serializable data object AccountsDestination
@Serializable data object SettingsDestination
@Serializable data object CatalogDestination

/** The main shell: the tab content above the bottom bar. [onAdd] opens quick add, [onOpenTransaction] the same sheet to edit. */
@Composable
fun BalanceApp(onAdd: () -> Unit, onOpenTransaction: (Long) -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val isSelected = { tab: TopLevelTab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }

    Column(Modifier.fillMaxSize().background(BtTheme.colors.bgCanvas)) {
        NavHost(
            navController = nav,
            startDestination = HomeDestination,
            modifier = Modifier.weight(1f).statusBarsPadding(),
            enterTransition = { slideIntoContainer(forwardDirection()) },
            exitTransition = { slideOutOfContainer(forwardDirection()) },
            popEnterTransition = { slideIntoContainer(SlideDirection.End) },
            popExitTransition = { slideOutOfContainer(SlideDirection.End) },
        ) {
            composable<HomeDestination> { HomeRoute(onOpenSettings = { nav.navigate(SettingsDestination) { launchSingleTop = true } }) }
            composable<HistoryDestination> { HistoryRoute(onOpen = onOpenTransaction) }
            composable<InsightsDestination> { NotBuiltYet("Insights") }
            composable<AccountsDestination> { NotBuiltYet("Accounts") }
            composable<SettingsDestination> {
                SettingsRoute(
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

/** New screens come in from the end, except a tab to the left of the current one. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.forwardDirection(): SlideDirection {
    val from = TopLevelTab.entries.indexOfFirst { initialState.destination.hasRoute(it.route::class) }
    val to = TopLevelTab.entries.indexOfFirst { targetState.destination.hasRoute(it.route::class) }
    return if (to in 0 until from) SlideDirection.End else SlideDirection.Start
}

/** Stands in for a screen until its feature module lands. */
@Composable
private fun NotBuiltYet(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = BtTheme.type.titleMedium, color = BtTheme.colors.textMuted)
    }
}
