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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Translate
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.AppLanguage
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.Language
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.common.askToUnlock
import com.eminallahverdi.balance.core.common.canUnlockDevice
import com.eminallahverdi.balance.core.common.findActivity
import com.eminallahverdi.balance.core.datastore.ThemePreference
import com.eminallahverdi.balance.core.datastore.WidgetBalanceMode
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.BtSwitch
import com.eminallahverdi.balance.core.designsystem.component.BtTimePickerSheet
import com.eminallahverdi.balance.core.designsystem.component.ChoiceSheet
import com.eminallahverdi.balance.core.designsystem.component.OptionRow
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.component.SegmentedToggle
import com.eminallahverdi.balance.core.designsystem.component.text
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.importer.moneymanager.ImportPreview

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenConfirm: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingTime by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        SettingsList(state, viewModel, onBack, onOpenCatalog, onOpenConfirm, onPickTime = { pickingTime = true })
        BtTimePickerSheet(
            visible = pickingTime,
            title = stringResource(R.string.settings_reminder_time),
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
            BtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.settings_back), onBack)
            Text(stringResource(R.string.settings_settings), style = BtTheme.type.titleLarge, color = BtTheme.colors.textPrimary)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // The default account is set on Accounts, and the eye on Home hides amounts: neither repeats here.
            Section(stringResource(R.string.settings_general)) {
                SettingRow(Icons.Rounded.Palette, stringResource(R.string.settings_theme)) {
                    SegmentedToggle(
                        options = listOf(stringResource(R.string.settings_dark), stringResource(R.string.settings_light), stringResource(R.string.settings_system)),
                        selected = themes.indexOf(state.preferences.theme),
                        onSelect = { viewModel.setTheme(themes[it]) },
                    )
                }
                Divider()
                LanguageRow()
                Divider()
                SettingRow(
                    icon = Icons.Rounded.Category,
                    title = stringResource(R.string.settings_categories_people),
                    caption = pluralStringResource(R.plurals.settings_categories, state.categoryCount, state.categoryCount) + " · " +
                        pluralStringResource(R.plurals.settings_people, state.peopleCount, state.peopleCount),
                    onClick = onOpenCatalog,
                ) { Chevron() }
            }
            Section(stringResource(R.string.settings_privacy)) {
                AppLockRow(state, viewModel)
                Divider()
                WidgetBalanceRow(state, viewModel)
            }
            Section(stringResource(R.string.settings_reminders)) {
                BankNotificationRows(state, viewModel, onOpenConfirm)
                Divider()
                EveningReminderRows(state, viewModel, onPickTime)
            }
            BackupSection()
            Section(stringResource(R.string.settings_data)) {
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

/** Which language the app speaks. It changes at once, and nothing on screen starts again. */
@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    val language by Language.flow.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    SettingRow(icon = Icons.Rounded.Translate, title = stringResource(R.string.settings_language), onClick = { open = true }) {
        Text(language.label(), style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
        Chevron()
    }
    if (open) {
        ChoiceSheet(stringResource(R.string.settings_language), onClose = { open = false }) { choose ->
            AppLanguage.entries.forEach { option ->
                OptionRow(text = option.label(), selected = option == language, onClick = { choose { Language.set(context, option) } })
            }
        }
    }
}

/** A language by its own name, so it can be found by someone who can't read the one showing now. */
@Composable
private fun AppLanguage.label(): String = when (this) {
    AppLanguage.SYSTEM -> stringResource(R.string.settings_follow_phone)
    AppLanguage.ENGLISH -> "English"
    AppLanguage.AZERBAIJANI -> "Azərbaycanca"
}

@Composable
private fun WidgetBalanceRow(state: SettingsUiState, vm: SettingsViewModel) {
    var open by remember { mutableStateOf(false) }
    val preferences = state.preferences
    SettingRow(
        icon = Icons.Rounded.VisibilityOff,
        title = stringResource(R.string.settings_widget_balance),
        caption = stringResource(R.string.settings_widget_balance_caption),
        onClick = { open = true },
    ) {
        Text(preferences.widgetBalanceMode.label(preferences.widgetRevealSeconds), style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
        Chevron()
    }
    if (open) {
        ChoiceSheet(stringResource(R.string.settings_widget_balance), onClose = { open = false }) { choose ->
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
    val activity = LocalContext.current.findActivity() as FragmentActivity
    val on = state.preferences.appLockEnabled
    val turnOn = stringResource(R.string.settings_turn_on_app_lock)
    val turnOff = stringResource(R.string.settings_turn_off_app_lock)
    var needsScreenLock by remember { mutableStateOf(false) }
    val change = { enabled: Boolean ->
        when {
            activity.canUnlockDevice() -> activity.askToUnlock(if (enabled) turnOn else turnOff, onUnlocked = { vm.setAppLock(enabled) })
            enabled -> needsScreenLock = true
            else -> vm.setAppLock(false)
        }
    }
    SettingRow(
        icon = Icons.Rounded.Fingerprint,
        title = stringResource(R.string.settings_app_lock),
        caption = stringResource(R.string.settings_app_lock_caption),
        onClick = { change(!on) },
    ) { BtSwitch(on, change) }
    if (needsScreenLock) {
        AlertDialog(
            onDismissRequest = { needsScreenLock = false },
            title = { Text(stringResource(R.string.settings_set_screen_lock)) },
            text = { Text(stringResource(R.string.settings_set_screen_lock_text)) },
            confirmButton = { TextButton(onClick = { needsScreenLock = false }) { Text(stringResource(R.string.settings_ok)) } },
        )
    }
}

@Composable
private fun ExportRow(vm: SettingsViewModel) {
    val message by vm.exportMessage.collectAsStateWithLifecycle()
    val export by vm.export.collectAsStateWithLifecycle()
    SettingRow(
        icon = Icons.Rounded.Download,
        title = stringResource(R.string.settings_export_csv),
        caption = message?.text() ?: stringResource(R.string.settings_export_caption),
        onClick = vm::openExport,
    ) { Chevron() }
    export?.let { ExportSheet(it, vm) }
}

/** A backup, an optional mapping file, and a look at what would change before anything does. */
@Composable
private fun ImportRow(vm: SettingsViewModel) {
    val state by vm.importState.collectAsStateWithLifecycle()
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file -> file?.let(vm::chooseBackup) }
    val pickMapping = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file -> file?.let(vm::chooseMapping) }
    SettingRow(
        icon = Icons.Rounded.Upload,
        title = stringResource(R.string.settings_import_mm),
        caption = state.message?.text() ?: stringResource(R.string.settings_import_mm_caption),
        onClick = { pickBackup.launch(arrayOf("*/*")) },
    ) { Chevron() }
    val preview = state.preview ?: return
    ChoiceSheet(stringResource(R.string.settings_import_mm), onClose = vm::closeImport) { choose ->
        ImportSummary(preview)
        OptionRow(
            text = stringResource(R.string.settings_mapping_file),
            selected = state.hasMapping,
            onClick = { pickMapping.launch(arrayOf("*/*")) },
            caption = stringResource(R.string.settings_mapping_caption),
        )
        Text(stringResource(R.string.settings_import_replaces), style = BtTheme.type.caption, color = BtTheme.colors.statusWarning)
        PrimaryButton(stringResource(R.string.settings_replace_and_import), onClick = { choose(vm::runImport) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ImportSummary(preview: ImportPreview) {
    val c = BtTheme.colors
    val plan = preview.plan
    val years = plan.entries.map { it.date.year }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (years.isNotEmpty()) Text(stringResource(R.string.settings_entries_years, pluralStringResource(R.plurals.settings_entries, plan.entries.size, plan.entries.size), years.min(), years.max()), style = BtTheme.type.bodyStrong, color = c.textPrimary)
        Text(stringResource(R.string.settings_accounts_list, plan.accounts.joinToString(", ") { it.name }), style = BtTheme.type.caption, color = c.textSecondary)
        if (preview.newCategories.isNotEmpty()) {
            Text(stringResource(R.string.settings_new_categories, preview.newCategories.joinToString(", ")), style = BtTheme.type.caption, color = c.textSecondary)
        }
        if (plan.differences.isEmpty()) {
            Text(stringResource(R.string.settings_balances_match), style = BtTheme.type.caption, color = c.moneyIncome)
        }
        plan.differences.forEach {
            val entries = MoneyFormat.format(Money(it.fromEntries), signed = true)
            Text(stringResource(R.string.settings_balance_differs, it.account, entries, MoneyFormat.format(Money(it.inBackup), signed = true)), style = BtTheme.type.caption, color = c.moneyExpense)
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
        title = stringResource(R.string.settings_bank_notifications_title),
        caption = when {
            !on -> stringResource(R.string.settings_bank_off)
            !hasAccess -> stringResource(R.string.settings_needs_access)
            appNames.isEmpty() -> stringResource(R.string.settings_pick_bank_apps)
            else -> stringResource(R.string.settings_bank_apps_waiting, appNames, state.toConfirm)
        },
        onClick = { if (on && !hasAccess) explaining = true else setEnabled(!on) },
    ) { BtSwitch(on, setEnabled) }
    if (on) {
        Divider()
        SettingRow(icon = Icons.Rounded.AccountBalance, title = stringResource(R.string.settings_bank_apps), onClick = { pickingApps = true }) {
            Text("${preferences.bankApps.size}", style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
            Chevron()
        }
        Divider()
        SettingRow(icon = Icons.Rounded.Notifications, title = stringResource(R.string.settings_to_confirm), onClick = onOpenConfirm) {
            Text("${state.toConfirm}", style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
            Chevron()
        }
    }
    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text(stringResource(R.string.settings_allow_access)) },
            text = {
                Text(stringResource(R.string.settings_allow_access_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    explaining = false
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text(stringResource(R.string.settings_open_settings)) }
            },
            dismissButton = { TextButton(onClick = { explaining = false }) { Text(stringResource(R.string.settings_not_now)) } },
        )
    }
    if (pickingApps) {
        // Several can be picked, so choosing leaves the sheet open, and it closes by tapping away.
        ChoiceSheet(stringResource(R.string.settings_bank_apps), onClose = { pickingApps = false }) { _ ->
            Text(
                stringResource(R.string.settings_bank_apps_hint),
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
        title = stringResource(R.string.settings_evening_reminder),
        caption = stringResource(R.string.settings_evening_reminder_caption),
        onClick = { setEnabled(!preferences.eveningReminderEnabled) },
    ) { BtSwitch(preferences.eveningReminderEnabled, setEnabled) }
    if (preferences.eveningReminderEnabled) {
        Divider()
        SettingRow(icon = Icons.Rounded.Schedule, title = stringResource(R.string.settings_reminder_time), onClick = onPickTime) {
            Text(DateLabels.timeOfDay(preferences.eveningReminderMinute), style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
            Chevron()
        }
    }
}

@Composable
private fun WidgetBalanceMode.label(revealSeconds: Int): String = when (this) {
    WidgetBalanceMode.ALWAYS_SHOW -> stringResource(R.string.settings_always_show)
    WidgetBalanceMode.ALWAYS_HIDE -> stringResource(R.string.settings_always_hide)
    WidgetBalanceMode.TAP_TO_SHOW -> stringResource(R.string.settings_tap_to_show_seconds, revealSeconds)
    WidgetBalanceMode.FINGERPRINT -> stringResource(R.string.settings_fingerprint_to_show)
}

private val WidgetBalanceMode.explanation: String
    @Composable get() = when (this) {
        WidgetBalanceMode.ALWAYS_SHOW -> stringResource(R.string.settings_always_show_explained)
        WidgetBalanceMode.ALWAYS_HIDE -> stringResource(R.string.settings_always_hide_explained)
        WidgetBalanceMode.TAP_TO_SHOW -> stringResource(R.string.settings_tap_to_show_explained)
        WidgetBalanceMode.FINGERPRINT -> stringResource(R.string.settings_fingerprint_explained)
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
