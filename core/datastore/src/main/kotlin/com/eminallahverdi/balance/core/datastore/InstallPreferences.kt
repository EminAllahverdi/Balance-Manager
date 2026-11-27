package com.eminallahverdi.balance.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.installStore: DataStore<Preferences> by preferencesDataStore(name = "install_prefs")

/**
 * Facts about this install rather than the user's data. A backup holds only `user_prefs`, so nothing here comes back with
 * a restore: Android's permissions belong to the install, and a new phone has to be asked again.
 */
@Singleton
class InstallPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val notificationsAskedKey = booleanPreferencesKey("notifications_asked")

    val notificationsAsked: Flow<Boolean> = context.installStore.data.map { it[notificationsAskedKey] ?: false }

    suspend fun setNotificationsAsked() { context.installStore.edit { it[notificationsAskedKey] = true } }

    private val confirmSwipeShownKey = booleanPreferencesKey("confirm_swipe_shown")

    /** Whether To confirm has shown that a payment swipes left to skip, which it does until Got it or a first swipe. */
    val confirmSwipeShown: Flow<Boolean> = context.installStore.data.map { it[confirmSwipeShownKey] ?: false }

    suspend fun setConfirmSwipeShown() { context.installStore.edit { it[confirmSwipeShownKey] = true } }

    private val accountantUnlockedKey = booleanPreferencesKey("accountant_unlocked")
    private val accountantFailedRoundsKey = intPreferencesKey("accountant_failed_rounds")
    private val accountantOnKey = booleanPreferencesKey("accountant_on")
    private val accountantCopiedKey = longPreferencesKey("accountant_copied_at")
    private val accountantFailedKey = booleanPreferencesKey("accountant_failed")

    /**
     * The copy for the owner's accountant: hidden until unlocked with its PIN on this phone, and kept here rather than in
     * the backup, so a restore on another phone asks for the PIN again.
     */
    val accountant: Flow<AccountantState> = context.installStore.data.map {
        AccountantState(
            unlocked = it[accountantUnlockedKey] ?: false,
            failedRounds = it[accountantFailedRoundsKey] ?: 0,
            on = it[accountantOnKey] ?: false,
            copiedAtMillis = it[accountantCopiedKey],
            failed = it[accountantFailedKey] ?: false,
        )
    }

    suspend fun setAccountantUnlocked() { context.installStore.edit { it[accountantUnlockedKey] = true } }

    /** A round of tries used up: the next round has fewer, which keeps guessing slow. */
    suspend fun addAccountantFailedRound() { context.installStore.edit { it[accountantFailedRoundsKey] = (it[accountantFailedRoundsKey] ?: 0) + 1 } }

    suspend fun setAccountantOn(on: Boolean) { context.installStore.edit { it[accountantOnKey] = on } }

    suspend fun setAccountantCopied(atMillis: Long) {
        context.installStore.edit {
            it[accountantCopiedKey] = atMillis
            it[accountantFailedKey] = false
        }
    }

    suspend fun setAccountantFailed() { context.installStore.edit { it[accountantFailedKey] = true } }
}

/** Whether the accountant's copy is unlocked and on, when it last went, and whether the last try failed. */
data class AccountantState(
    val unlocked: Boolean = false,
    val failedRounds: Int = 0,
    val on: Boolean = false,
    val copiedAtMillis: Long? = null,
    val failed: Boolean = false,
)
