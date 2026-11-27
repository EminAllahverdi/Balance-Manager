package com.eminallahverdi.balance.feature.settings

import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.backup.AccountantCopy
import com.eminallahverdi.balance.core.backup.DriveAccess
import com.eminallahverdi.balance.core.backup.DriveAuthorization
import com.eminallahverdi.balance.core.datastore.AccountantState
import com.eminallahverdi.balance.core.datastore.InstallPreferences
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.ACCOUNTANT_UNLOCK_TAPS
import com.eminallahverdi.balance.core.model.AccountantLock
import com.eminallahverdi.balance.core.model.accountantTriesAllowed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What this screen holds of the accountant copy, beyond what's saved: the PIN box, Google's screen, and a send going. */
data class AccountantUiState(
    val saved: AccountantState = AccountantState(),
    /** The nightly backup is on: the copy only ever goes with one. */
    val backupOn: Boolean = false,
    val asking: Boolean = false,
    val typed: String = "",
    val sending: Boolean = false,
    val consent: IntentSender? = null,
    /** Set when a round of tries is used up: the screen sends the user back out. */
    val sentAway: Boolean = false,
)

/**
 * The owner's copy for their accountant. Seven taps on the version number ask for its PIN; a wrong round closes Settings,
 * and every round after the first allows one try. Once it's open on this phone it stays open, and the copy goes with
 * each backup while it's on.
 */
@HiltViewModel
class AccountantViewModel @Inject constructor(
    private val install: InstallPreferences,
    prefs: UserPreferencesRepository,
    private val lock: AccountantLock,
    private val authorization: DriveAuthorization,
    private val copy: AccountantCopy,
) : ViewModel() {
    private val session = MutableStateFlow(AccountantUiState())
    private var taps = 0
    private var triesLeft = 0

    val state: StateFlow<AccountantUiState> = combine(install.accountant, prefs.preferences, session) { saved, p, s ->
        s.copy(saved = saved, backupOn = p.driveBackupEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountantUiState())

    /** Another tap on the version number. Nothing shows until the seventh, and nothing at all once it's open. */
    fun tapVersion() {
        if (session.value.asking) return
        taps++
        if (taps < ACCOUNTANT_UNLOCK_TAPS) return
        taps = 0
        viewModelScope.launch {
            val saved = install.accountant.first()
            if (saved.unlocked) return@launch
            triesLeft = accountantTriesAllowed(saved.failedRounds)
            session.update { it.copy(asking = true, typed = "") }
        }
    }

    fun type(text: String) = session.update { it.copy(typed = text) }

    /** OK. Wrong says nothing of how many tries are left; the last wrong one ends the round and leaves Settings. */
    fun submit() {
        val typed = session.value.typed
        viewModelScope.launch {
            if (lock.accepts(typed)) {
                install.setAccountantUnlocked()
                session.update { it.copy(asking = false, typed = "") }
                return@launch
            }
            triesLeft--
            if (triesLeft > 0) {
                session.update { it.copy(typed = "") }
            } else {
                install.addAccountantFailedRound()
                session.update { it.copy(asking = false, typed = "", sentAway = true) }
            }
        }
    }

    /** Closing the box without the PIN uses the round up too, so cancelling can't be used to try again for free. */
    fun cancel() {
        viewModelScope.launch {
            install.addAccountantFailedRound()
            session.update { it.copy(asking = false, typed = "", sentAway = true) }
        }
    }

    fun sentAwayHandled() = session.update { it.copy(sentAway = false) }

    /** On asks Google once for the files the app makes, then sends a first copy straight away. */
    fun setOn(on: Boolean) {
        if (!on) {
            viewModelScope.launch { install.setAccountantOn(false) }
            return
        }
        viewModelScope.launch { continueWith(authorization.accountantAccess()) }
    }

    fun consentShown() = session.update { it.copy(consent = null) }

    fun onConsent(result: Intent?) {
        viewModelScope.launch { continueWith(authorization.accessFrom(result)) }
    }

    private suspend fun continueWith(access: DriveAccess) {
        when (access) {
            is DriveAccess.Granted -> {
                install.setAccountantOn(true)
                session.update { it.copy(sending = true) }
                copy.send()
                session.update { it.copy(sending = false) }
            }
            is DriveAccess.NeedsConsent -> session.update { it.copy(consent = access.screen) }
            else -> install.setAccountantFailed()
        }
    }
}
