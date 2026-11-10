package com.eminallahverdi.balance.feature.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.datastore.BackupProblem
import com.eminallahverdi.balance.core.designsystem.component.BtSwitch
import com.eminallahverdi.balance.core.designsystem.component.ChoiceSheet
import com.eminallahverdi.balance.core.designsystem.component.OptionRow
import com.eminallahverdi.balance.core.designsystem.component.text
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.AppEnvironment
import com.eminallahverdi.balance.core.model.DriveBackup
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Backs up every night to a hidden folder in the user's own Google Drive. Restoring is here too, for after a reinstall. */
@Composable
internal fun BackupSection(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<DriveBackup?>(null) }
    DriveRestoreEffects(state, viewModel)

    Section(stringResource(R.string.settings_backup)) {
        SettingRow(icon = Icons.Rounded.Backup, title = stringResource(R.string.settings_drive_backup), caption = state.status()) {
            BtSwitch(state.enabled, viewModel::setEnabled)
        }
        if (state.enabled) {
            Divider()
            SettingRow(
                icon = Icons.Rounded.SignalCellularAlt,
                title = stringResource(R.string.settings_use_mobile_data),
                caption = if (state.useMobileData) stringResource(R.string.settings_mobile_on) else stringResource(R.string.settings_mobile_off),
            ) { BtSwitch(state.useMobileData, viewModel::setMobileData) }
            Divider()
            SettingRow(icon = Icons.Rounded.CloudUpload, title = stringResource(R.string.settings_back_up_now), onClick = viewModel::backUpNow) { Chevron() }
        }
        Divider()
        SettingRow(
            icon = Icons.Rounded.CloudDownload,
            title = stringResource(R.string.settings_restore_drive),
            caption = stringResource(R.string.settings_restore_caption),
            onClick = viewModel::openRestore,
        ) { Chevron() }
    }

    DriveRestoreSheet(state, viewModel, onChoose = { confirming = it })
    confirming?.let { backup ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.settings_restore_question)) },
            text = { Text(stringResource(R.string.settings_restore_text, backupTime(backup.createdAt.toEpochMilli()))) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    viewModel.restore(backup)
                }) { Text(stringResource(R.string.settings_replace_restart)) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

/** Google's consent screen when one is waiting, and the restart that lets a staged restore take over. */
@Composable
fun DriveRestoreEffects(state: BackupUiState, viewModel: BackupViewModel) {
    val context = LocalContext.current
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onConsent(result.data) else viewModel.consentCancelled()
    }
    LaunchedEffect(state.consent) {
        state.consent?.let {
            viewModel.consentShown()
            consent.launch(IntentSenderRequest.Builder(it).build())
        }
    }
    LaunchedEffect(state.restartNeeded) { if (state.restartNeeded) context.restartApp() }
}

/** What can be restored, while [BackupUiState.backups] holds it. [onChoose] decides whether a choice needs confirming. */
@Composable
fun DriveRestoreSheet(state: BackupUiState, viewModel: BackupViewModel, onChoose: (DriveBackup) -> Unit) {
    val backups = state.backups ?: return
    ChoiceSheet(stringResource(R.string.settings_restore_drive), onClose = viewModel::closeRestore) { choose ->
        if (backups.isEmpty()) Text(stringResource(R.string.settings_no_backups), style = BtTheme.type.body, color = BtTheme.colors.textMuted)
        backups.forEach { backup ->
            OptionRow(
                text = backupTime(backup.createdAt.toEpochMilli()).replaceFirstChar { it.uppercase() },
                selected = false,
                onClick = { choose { onChoose(backup) } },
                caption = backupCaption(backup, viewModel.showsBackupSource),
            )
        }
    }
}

@Composable
private fun BackupUiState.status(): String = when {
    message != null -> message.text()
    !enabled -> stringResource(R.string.settings_backup_off)
    problem == BackupProblem.SIGN_IN -> stringResource(R.string.settings_sign_in_again)
    lastBackupAt == null -> stringResource(R.string.settings_not_backed_up)
    problem == BackupProblem.FAILED -> stringResource(R.string.settings_last_failed, backupTime(lastBackupAt))
    else -> stringResource(R.string.settings_last_backed_up, backupTime(lastBackupAt))
}

/** Dev also offers real backups, so it says which is which. Prod only ever has its own. */
@Composable
private fun backupCaption(backup: DriveBackup, showSource: Boolean): String? {
    val source = if (showSource) (if (backup.environment == AppEnvironment.PROD) stringResource(R.string.settings_real_data) else stringResource(R.string.settings_test_data)) else null
    return listOfNotNull(backup.entries?.let { pluralStringResource(R.plurals.settings_entries, it, it) }, source).joinToString(" · ").ifEmpty { null }
}

@Composable
private fun backupTime(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val day = when (time.toLocalDate()) {
        today -> stringResource(R.string.settings_today_lower)
        today.minusDays(1) -> stringResource(R.string.settings_yesterday_lower)
        else -> DateLabels.dayMonth(time.toLocalDate())
    }
    return stringResource(R.string.settings_backup_time, day, TIME.format(time))
}

/** The cleanest way for everything holding the old database to let go of it. */
private fun Context.restartApp() {
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
    startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}
