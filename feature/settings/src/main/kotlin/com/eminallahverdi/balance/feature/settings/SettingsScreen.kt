package com.eminallahverdi.balance.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.datastore.ThemePreference
import com.eminallahverdi.balance.core.datastore.WidgetBalanceMode
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.ChoiceSheet
import com.eminallahverdi.balance.core.designsystem.component.OptionRow
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.SegmentedToggle
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme

@Composable
fun SettingsRoute(onBack: () -> Unit, onOpenCatalog: () -> Unit, onOpenRecurring: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val themes = ThemePreference.entries
    Column(Modifier.fillMaxSize().background(BtTheme.colors.bgCanvas).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Text("Settings", style = BtTheme.type.titleLarge, color = BtTheme.colors.textPrimary)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("General") {
                AccountRow(state, viewModel)
                Divider()
                SettingRow(Icons.Rounded.Palette, "Theme") {
                    SegmentedToggle(
                        options = listOf("Dark", "Light", "System"),
                        selected = themes.indexOf(state.preferences.theme),
                        onSelect = { viewModel.setTheme(themes[it]) },
                    )
                }
                Divider()
                SettingRow(
                    icon = Icons.Rounded.Category,
                    title = "Categories & people",
                    caption = "${state.categoryCount} categories · ${state.peopleCount} people",
                    onClick = onOpenCatalog,
                ) { Chevron() }
            }
            Section("Privacy") {
                WidgetBalanceRow(state, viewModel)
                Divider()
                SettingRow(
                    icon = Icons.Rounded.BlurOn,
                    title = "Hide balance and income",
                    caption = "Tap the eye on Home to show them",
                    onClick = { viewModel.setHideAmounts(!state.preferences.hideAmountsInApp) },
                ) {
                    Switch(
                        checked = state.preferences.hideAmountsInApp,
                        onCheckedChange = viewModel::setHideAmounts,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = BtTheme.colors.accentFill,
                            checkedThumbColor = BtTheme.colors.accentOn,
                            uncheckedTrackColor = BtTheme.colors.bgSurfaceHigh,
                            uncheckedThumbColor = BtTheme.colors.textMuted,
                        ),
                    )
                }
            }
            Section("Reminders") {
                SettingRow(
                    icon = Icons.Rounded.EventRepeat,
                    title = "Recurring payments",
                    caption = state.recurringSummary,
                    onClick = onOpenRecurring,
                ) { Chevron() }
            }
        }
    }
}

@Composable
private fun AccountRow(state: SettingsUiState, vm: SettingsViewModel) {
    var open by remember { mutableStateOf(false) }
    SettingRow(
        icon = Icons.Rounded.AccountBalanceWallet,
        title = "Default expense account",
        caption = "Picked first when you add an expense",
        onClick = { open = true },
    ) {
        Text(state.defaultAccount?.name.orEmpty(), style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
        Chevron()
    }
    if (open) {
        ChoiceSheet("Default expense account", onClose = { open = false }) { choose ->
            state.accounts.forEach { account ->
                OptionRow(
                    text = account.name,
                    selected = account.id == state.defaultAccount?.id,
                    onClick = { choose { vm.setDefaultAccount(account.id) } },
                    leading = { CategoryTile(account.icon, account.color, size = 32.dp, iconSize = 18.dp, corner = 10.dp) },
                )
            }
        }
    }
}

@Composable
private fun WidgetBalanceRow(state: SettingsUiState, vm: SettingsViewModel) {
    var open by remember { mutableStateOf(false) }
    val preferences = state.preferences
    SettingRow(
        icon = Icons.Rounded.VisibilityOff,
        title = "Widget balance",
        caption = "How the home-screen balance is shown",
        onClick = { open = true },
    ) {
        Text(preferences.widgetBalanceMode.label(preferences.widgetRevealSeconds), style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
        Chevron()
    }
    if (open) {
        ChoiceSheet("Widget balance", onClose = { open = false }) { choose ->
            WidgetBalanceMode.entries.forEach { mode ->
                OptionRow(
                    text = mode.label(preferences.widgetRevealSeconds),
                    selected = mode == preferences.widgetBalanceMode,
                    onClick = { choose { vm.setWidgetBalanceMode(mode) } },
                    caption = mode.explanation,
                )
            }
        }
    }
}

private fun WidgetBalanceMode.label(revealSeconds: Int): String = when (this) {
    WidgetBalanceMode.ALWAYS_SHOW -> "Always show"
    WidgetBalanceMode.ALWAYS_HIDE -> "Always hide"
    WidgetBalanceMode.TAP_TO_SHOW -> "Tap to show · $revealSeconds s"
    WidgetBalanceMode.FINGERPRINT -> "Fingerprint to show"
}

private val WidgetBalanceMode.explanation: String
    get() = when (this) {
        WidgetBalanceMode.ALWAYS_SHOW -> "Balance is always visible."
        WidgetBalanceMode.ALWAYS_HIDE -> "Never shown on the home screen."
        WidgetBalanceMode.TAP_TO_SHOW -> "Tap to show, then it hides again."
        WidgetBalanceMode.FINGERPRINT -> "Touch opens a small fingerprint prompt."
    }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline(title, Modifier.padding(horizontal = 4.dp))
        BtCard { Column { content() } }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    val c = BtTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = BtTheme.type.bodyStrong, color = c.textPrimary)
            if (caption != null) Text(caption, style = BtTheme.type.caption, color = c.textSecondary)
        }
        trailing()
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.Rounded.ChevronRight, null, tint = BtTheme.colors.textMuted, modifier = Modifier.size(20.dp))
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(BtTheme.colors.borderDefault))
}
