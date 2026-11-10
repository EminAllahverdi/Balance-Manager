package com.eminallahverdi.balance.feature.settings

import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.backup.BackupTooNewException
import com.eminallahverdi.balance.core.backup.DriveAccess
import com.eminallahverdi.balance.core.backup.DriveAuthorization
import com.eminallahverdi.balance.core.backup.DriveBackups
import com.eminallahverdi.balance.core.common.UiText
import com.eminallahverdi.balance.core.common.uiText
import com.eminallahverdi.balance.core.datastore.BackupProblem
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.AppEnvironment
import com.eminallahverdi.balance.core.model.DriveBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [message] is what's happening now, or why it didn't. [consent] is Google's screen, waiting to be shown. */
data class BackupUiState(
    val enabled: Boolean = false,
    val useMobileData: Boolean = false,
    val lastBackupAt: Long? = null,
    val problem: BackupProblem? = null,
    val message: UiText? = null,
    val consent: IntentSender? = null,
    /** Open while this isn't null: what can be restored from this account. */
    val backups: List<DriveBackup>? = null,
    /** A restore is staged, and takes over once the app restarts. */
    val restartNeeded: Boolean = false,
    /** Connected for onboarding, which turns nothing on until its last tap: how many backups this account already has. */
    val existingBackups: Int? = null,
)

private enum class DriveTask { TURN_ON, BACK_UP, LIST_BACKUPS, CONNECT }

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val authorization: DriveAuthorization,
    private val backups: DriveBackups,
    environment: AppEnvironment,
) : ViewModel() {
    val showsBackupSource: Boolean = environment == AppEnvironment.DEV
    private val session = MutableStateFlow(BackupUiState())
    private var waitingOnConsent: DriveTask? = null
    private var token: String? = null

    val state: StateFlow<BackupUiState> = combine(prefs.preferences, session) { p, s ->
        s.copy(enabled = p.driveBackupEnabled, useMobileData = p.backupOnMobileData, lastBackupAt = p.lastBackupAt, problem = p.backupProblem)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun setEnabled(enabled: Boolean) {
        if (enabled) start(DriveTask.TURN_ON) else viewModelScope.launch { prefs.setDriveBackup(false) }
    }

    fun setMobileData(allowed: Boolean) { viewModelScope.launch { prefs.setBackupOnMobileData(allowed) } }
    fun backUpNow() = start(DriveTask.BACK_UP)
    fun openRestore() = start(DriveTask.LIST_BACKUPS)
    fun closeRestore() = session.update { it.copy(backups = null) }

    /** Asks Google for Drive and counts what's there, without turning backups on. */
    fun connect() = start(DriveTask.CONNECT)
    fun connectionUsed() = session.update { it.copy(existingBackups = null) }
    fun consentShown() = session.update { it.copy(consent = null) }

    fun onConsent(result: Intent?) {
        val task = waitingOnConsent ?: return
        waitingOnConsent = null
        viewModelScope.launch { continueWith(task, authorization.accessFrom(result)) }
    }

    fun consentCancelled() {
        waitingOnConsent = null
        session.update { it.copy(message = null) }
    }

    fun restore(backup: DriveBackup) {
        val access = token ?: return
        session.update { it.copy(message = uiText(R.string.settings_restoring)) }
        viewModelScope.launch {
            runCatching { backups.stageRestore(access, backup) }
                .onSuccess { session.update { it.copy(restartNeeded = true) } }
                .onFailure { failure ->
                    val message = uiText(if (failure is BackupTooNewException) R.string.settings_update_to_restore else R.string.settings_couldnt_restore)
                    session.update { it.copy(message = message) }
                }
        }
    }

    private fun start(task: DriveTask) {
        session.update { it.copy(message = uiText(R.string.settings_connecting_google)) }
        viewModelScope.launch { continueWith(task, authorization.access()) }
    }

    private suspend fun continueWith(task: DriveTask, access: DriveAccess) {
        when (access) {
            is DriveAccess.Granted -> {
                token = access.token
                run(task, access.token)
            }
            is DriveAccess.NeedsConsent -> {
                waitingOnConsent = task
                session.update { it.copy(consent = access.screen) }
            }
            DriveAccess.NotSetUp -> session.update { it.copy(message = uiText(R.string.settings_drive_not_set_up)) }
            DriveAccess.Unavailable -> session.update { it.copy(message = uiText(R.string.settings_couldnt_reach_google)) }
        }
    }

    private suspend fun run(task: DriveTask, access: String) {
        when (task) {
            DriveTask.TURN_ON -> {
                prefs.setDriveBackup(true)
                sendBackup()
            }
            DriveTask.BACK_UP -> sendBackup()
            DriveTask.LIST_BACKUPS -> runCatching { backups.list(access) }
                .onSuccess { found -> session.update { it.copy(message = null, backups = found) } }
                .onFailure { session.update { it.copy(message = uiText(R.string.settings_couldnt_reach_drive)) } }
            DriveTask.CONNECT -> runCatching { backups.list(access) }
                .onSuccess { found -> session.update { it.copy(message = null, existingBackups = found.size) } }
                .onFailure { session.update { it.copy(message = uiText(R.string.settings_couldnt_reach_drive)) } }
        }
    }

    private suspend fun sendBackup() {
        session.update { it.copy(message = uiText(R.string.settings_backing_up)) }
        val sent = backups.backUp()
        session.update { it.copy(message = if (sent) null else uiText(R.string.settings_backup_failed)) }
    }
}
