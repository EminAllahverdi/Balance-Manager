package com.eminallahverdi.balance.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.common.askToUnlock
import com.eminallahverdi.balance.core.common.canUnlockDevice
import com.eminallahverdi.balance.core.datastore.ThemePreference
import com.eminallahverdi.balance.core.datastore.WidgetBalanceMode
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.BtSwitch
import com.eminallahverdi.balance.core.designsystem.component.BtTimePickerSheet
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.ChoiceSheet
import com.eminallahverdi.balance.core.designsystem.component.OptionRow
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.component.SegmentedToggle
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.importer.moneymanager.ImportPreview
import java.time.LocalDate

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenConfirm: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingTime by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        SettingsList(state, viewModel, onBack, onOpenCatalog, onOpenRecurring, onOpenConfirm, onPickTime = { pickingTime = true })
        BtTimePickerSheet(
            visible = pickingTime,
            title = "Reminder time",
            minuteOfDay = state.preferences.eveningReminderMinute,
            onPick = viewModel::setEveningReminderMinute,
            onClose = { pickingTime = false },
        )
    }
}

@Composable
private fun SettingsList(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenConfirm: () -> Unit,
    onPickTime: () -> Unit,
) {
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
                AppLockRow(state, viewModel)
                Divider()
                WidgetBalanceRow(state, viewModel)
                Divider()
                SettingRow(
                    icon = Icons.Rounded.BlurOn,
                    title = "Hide balance and income",
                    caption = "Tap the eye on Home to show them",
                    onClick = { viewModel.setHideAmounts(!state.preferences.hideAmountsInApp) },
                ) { BtSwitch(state.preferences.hideAmountsInApp, viewModel::setHideAmounts) }
            }
            Section("Reminders") {
                BankNotificationRows(state, viewModel, onOpenConfirm)
                Divider()
                EveningReminderRows(state, viewModel, onPickTime)
                Divider()
                SettingRow(
                    icon = Icons.Rounded.EventRepeat,
                    title = "Recurring payments",
                    caption = state.recurringSummary,
                    onClick = onOpenRecurring,
                ) { Chevron() }
            }
            BackupSection()
            Section("Data") {
                ExportRow(viewModel)
                Divider()
                ImportRow(viewModel)
            }
            VersionLine()
        }
    }
}

/** Which app and build this is, so it's plain which one is installed when two sit side by side. */
@Composable
private fun VersionLine() {
    val context = LocalContext.current
    val version = remember { context.appVersion() }
    Text(version, style = BtTheme.type.caption, color = BtTheme.colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

private fun Context.appVersion(): String {
    val info = packageManager.getPackageInfo(packageName, 0)
    return "${applicationInfo.loadLabel(packageManager)} ${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
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

/** Changing this asks for the fingerprint or PIN first, so no one can turn it off on an unlocked phone, or lock themselves out. */
@Composable
private fun AppLockRow(state: SettingsUiState, vm: SettingsViewModel) {
    val activity = LocalContext.current as FragmentActivity
    val on = state.preferences.appLockEnabled
    var needsScreenLock by remember { mutableStateOf(false) }
    val change = { enabled: Boolean ->
        when {
            activity.canUnlockDevice() -> activity.askToUnlock(if (enabled) "Turn on app lock" else "Turn off app lock", onUnlocked = { vm.setAppLock(enabled) })
            enabled -> needsScreenLock = true
            else -> vm.setAppLock(false)
        }
    }
    SettingRow(
        icon = Icons.Rounded.Fingerprint,
        title = "App lock",
        caption = "Fingerprint or your phone's PIN when opening the app",
        onClick = { change(!on) },
    ) { BtSwitch(on, change) }
    if (needsScreenLock) {
        AlertDialog(
            onDismissRequest = { needsScreenLock = false },
            title = { Text("Set a screen lock first") },
            text = { Text("App lock uses your phone's fingerprint or PIN. Set one up in your phone's settings, then turn this on.") },
            confirmButton = { TextButton(onClick = { needsScreenLock = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun ExportRow(vm: SettingsViewModel) {
    val message by vm.exportMessage.collectAsStateWithLifecycle()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { file -> file?.let(vm::exportCsv) }
    SettingRow(
        icon = Icons.Rounded.Download,
        title = "Export CSV",
        caption = message ?: "Every entry, as a spreadsheet",
        onClick = { pickFile.launch("balance-${LocalDate.now()}.csv") },
    ) { Chevron() }
}

/** A backup, an optional mapping file, and a look at what would change before anything does. */
@Composable
private fun ImportRow(vm: SettingsViewModel) {
    val state by vm.importState.collectAsStateWithLifecycle()
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file -> file?.let(vm::chooseBackup) }
    val pickMapping = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file -> file?.let(vm::chooseMapping) }
    SettingRow(
        icon = Icons.Rounded.Upload,
        title = "Import from Money Manager",
        caption = state.message ?: "Carry on from a backup of your old app",
        onClick = { pickBackup.launch(arrayOf("*/*")) },
    ) { Chevron() }
    val preview = state.preview ?: return
    ChoiceSheet("Import from Money Manager", onClose = vm::closeImport) { choose ->
        ImportSummary(preview)
        OptionRow(
            text = "Mapping file",
            selected = state.hasMapping,
            onClick = { pickMapping.launch(arrayOf("*/*")) },
            caption = "Optional: how your categories and tags become these",
        )
        Text("This replaces the accounts, entries, loans and reminders now in the app.", style = BtTheme.type.caption, color = BtTheme.colors.statusWarning)
        PrimaryButton("Replace and import", onClick = { choose(vm::runImport) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ImportSummary(preview: ImportPreview) {
    val c = BtTheme.colors
    val plan = preview.plan
    val years = plan.entries.map { it.date.year }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (years.isNotEmpty()) Text("${plan.entries.size} entries, ${years.min()} to ${years.max()}", style = BtTheme.type.bodyStrong, color = c.textPrimary)
        Text("Accounts: " + plan.accounts.joinToString(", ") { it.name }, style = BtTheme.type.caption, color = c.textSecondary)
        if (preview.newCategories.isNotEmpty()) {
            Text("New categories: " + preview.newCategories.joinToString(", "), style = BtTheme.type.caption, color = c.textSecondary)
        }
        if (plan.differences.isEmpty()) {
            Text("Every account's balance matches the backup", style = BtTheme.type.caption, color = c.moneyIncome)
        }
        plan.differences.forEach {
            val entries = MoneyFormat.format(Money(it.fromEntries), signed = true)
            Text("${it.account} would be $entries, not ${MoneyFormat.format(Money(it.inBackup), signed = true)}", style = BtTheme.type.caption, color = c.moneyExpense)
        }
    }
}

/** Reading notifications is Android's most sensitive permission, so it's asked for here with the reason, only when it's turned on. */
@Composable
private fun BankNotificationRows(state: SettingsUiState, vm: SettingsViewModel, onOpenConfirm: () -> Unit) {
    val context = LocalContext.current
    val preferences = state.preferences
    val on = preferences.bankNotificationsEnabled
    var hasAccess by remember { mutableStateOf(context.canReadNotifications()) }
    var explaining by remember { mutableStateOf(false) }
    var pickingApps by remember { mutableStateOf(false) }
    // The switch for this is in Android's settings, so it can change while this screen is away.
    LifecycleResumeEffect(Unit) {
        hasAccess = context.canReadNotifications()
        onPauseOrDispose {}
    }
    val setEnabled = { enabled: Boolean ->
        vm.setBankNotifications(enabled)
        if (enabled && !hasAccess) explaining = true
    }
    val appNames = preferences.bankApps.map(context::appLabel).sorted().joinToString(", ")
    SettingRow(
        icon = Icons.Rounded.NotificationsActive,
        title = "Bank notifications",
        caption = when {
            !on -> "Turn bank payments into ready-made entries"
            !hasAccess -> "Needs notification access"
            appNames.isEmpty() -> "Pick your bank apps"
            else -> "$appNames · ${state.toConfirm} to confirm"
        },
        onClick = { if (on && !hasAccess) explaining = true else setEnabled(!on) },
    ) { BtSwitch(on, setEnabled) }
    if (on) {
        Divider()
        SettingRow(icon = Icons.Rounded.AccountBalance, title = "Bank apps", onClick = { pickingApps = true }) {
            Text("${preferences.bankApps.size}", style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
            Chevron()
        }
        Divider()
        SettingRow(icon = Icons.Rounded.Notifications, title = "To confirm", onClick = onOpenConfirm) {
            Text("${state.toConfirm}", style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
            Chevron()
        }
    }
    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text("Allow notification access") },
            text = {
                Text(
                    "Balance needs Android's notification access to see payments from your bank apps. It reads only the apps " +
                        "you pick, nothing leaves your phone, and entries wait for you to confirm them.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explaining = false
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { explaining = false }) { Text("Not now") } },
        )
    }
    if (pickingApps) {
        // Several can be picked, so choosing leaves the sheet open, and it closes by tapping away.
        ChoiceSheet("Bank apps", onClose = { pickingApps = false }) { _ ->
            Text(
                "Apps appear here once they've sent a notification. Only the ones you tick are read.",
                style = BtTheme.type.caption,
                color = BtTheme.colors.textSecondary,
            )
            // An app with no name here has no launcher icon, so it isn't one the user would recognise.
            preferences.seenApps.filter { it in preferences.bankApps || context.appLabel(it) != it }.sortedBy(context::appLabel).forEach { app ->
                OptionRow(
                    text = context.appLabel(app),
                    selected = app in preferences.bankApps,
                    onClick = { vm.setBankApp(app, app !in preferences.bankApps) },
                )
            }
        }
    }
}

private fun Context.canReadNotifications(): Boolean = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)

private fun Context.appLabel(app: String): String =
    runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(app, 0)).toString() }.getOrDefault(app)

@Composable
private fun EveningReminderRows(state: SettingsUiState, vm: SettingsViewModel, onPickTime: () -> Unit) {
    val preferences = state.preferences
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val setEnabled = { enabled: Boolean ->
        // This is when the reminder starts to matter, so this is when to ask. Android only shows it once.
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.setEveningReminder(enabled)
    }
    SettingRow(
        icon = Icons.Rounded.Bedtime,
        title = "Evening reminder",
        caption = "If nothing was added that day",
        onClick = { setEnabled(!preferences.eveningReminderEnabled) },
    ) { BtSwitch(preferences.eveningReminderEnabled, setEnabled) }
    if (preferences.eveningReminderEnabled) {
        Divider()
        SettingRow(icon = Icons.Rounded.Schedule, title = "Reminder time", onClick = onPickTime) {
            Text(DateLabels.timeOfDay(preferences.eveningReminderMinute), style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
            Chevron()
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
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline(title, Modifier.padding(horizontal = 4.dp))
        BtCard { Column { content() } }
    }
}

@Composable
internal fun SettingRow(
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
internal fun Chevron() {
    Icon(Icons.Rounded.ChevronRight, null, tint = BtTheme.colors.textMuted, modifier = Modifier.size(20.dp))
}

@Composable
internal fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(BtTheme.colors.borderDefault))
}
