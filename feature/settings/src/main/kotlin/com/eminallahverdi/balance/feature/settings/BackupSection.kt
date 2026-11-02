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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.datastore.BackupProblem
import com.eminallahverdi.balance.core.designsystem.component.BtSwitch
import com.eminallahverdi.balance.core.designsystem.component.ChoiceSheet
import com.eminallahverdi.balance.core.designsystem.component.OptionRow
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.AppEnvironment
import com.eminallahverdi.balance.core.model.DriveBackup
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/** Backs up every night to a hidden folder in the user's own Google Drive. Restoring is here too, for after a reinstall. */
@Composable
internal fun BackupSection(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<DriveBackup?>(null) }
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

    Section("Backup") {
        SettingRow(icon = Icons.Rounded.Backup, title = "Google Drive backup", caption = state.status()) {
            BtSwitch(state.enabled, viewModel::setEnabled)
        }
        if (state.enabled) {
            Divider()
            SettingRow(
                icon = Icons.Rounded.SignalCellularAlt,
                title = "Use mobile data",
                caption = if (state.useMobileData) "Backs up on any connection" else "Backups wait for Wi-Fi",
            ) { BtSwitch(state.useMobileData, viewModel::setMobileData) }
            Divider()
            SettingRow(icon = Icons.Rounded.CloudUpload, title = "Back up now", onClick = viewModel::backUpNow) { Chevron() }
        }
        Divider()
        SettingRow(
            icon = Icons.Rounded.CloudDownload,
            title = "Restore from Google Drive",
            caption = "After a reinstall, or on a new phone",
            onClick = viewModel::openRestore,
        ) { Chevron() }
    }

    state.backups?.let { backups ->
        ChoiceSheet("Restore from Google Drive", onClose = viewModel::closeRestore) { choose ->
            if (backups.isEmpty()) Text("No backups in this Google account yet", style = BtTheme.type.body, color = BtTheme.colors.textMuted)
            backups.forEach { backup ->
                OptionRow(
                    text = backupTime(backup.createdAt.toEpochMilli()).replaceFirstChar { it.uppercase() },
                    selected = false,
                    onClick = { choose { confirming = backup } },
                    caption = backupCaption(backup, viewModel.showsBackupSource),
                )
            }
        }
    }
    confirming?.let { backup ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Restore this backup?") },
            text = { Text("Everything in the app now is replaced with the backup from ${backupTime(backup.createdAt.toEpochMilli())}, and the app restarts.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    viewModel.restore(backup)
                }) { Text("Replace and restart") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
        )
    }
}

private fun BackupUiState.status(): String = when {
    message != null -> message
    !enabled -> "Every night, to your own Google account"
    problem == BackupProblem.SIGN_IN -> "Turn this off and on to sign in again"
    lastBackupAt == null -> "Not backed up yet"
    problem == BackupProblem.FAILED -> "Last try failed. Last backed up ${backupTime(lastBackupAt)}"
    else -> "Last backed up ${backupTime(lastBackupAt)}"
}

/** Dev also offers real backups, so it says which is which. Prod only ever has its own. */
private fun backupCaption(backup: DriveBackup, showSource: Boolean): String? {
    val source = if (showSource) (if (backup.environment == AppEnvironment.PROD) "Real data" else "Test data") else null
    return listOfNotNull(backup.entries?.let { "$it entries" }, source).joinToString(" · ").ifEmpty { null }
}

private fun backupTime(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val day = when (time.toLocalDate()) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> DAY.format(time)
    }
    return "$day at ${TIME.format(time)}"
}

/** The cleanest way for everything holding the old database to let go of it. */
private fun Context.restartApp() {
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
    startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}
