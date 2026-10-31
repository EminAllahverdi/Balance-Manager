package com.eminallahverdi.balance.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemePreference { DARK, LIGHT, SYSTEM }

/** How the balance appears on the home-screen widget. */
enum class WidgetBalanceMode { ALWAYS_SHOW, ALWAYS_HIDE, TAP_TO_SHOW, FINGERPRINT }

data class UserPreferences(
    val onboardingDone: Boolean = false,
    val ownerName: String = "",
    val theme: ThemePreference = ThemePreference.DARK,
    val defaultExpenseAccountId: Long? = null,
    val widgetBalanceMode: WidgetBalanceMode = WidgetBalanceMode.FINGERPRINT,
    val widgetRevealSeconds: Int = 5,
    val appLockEnabled: Boolean = false,
    val hideAmountsInApp: Boolean = false,
    val eveningReminderEnabled: Boolean = true,
    /** Minutes after midnight, default 21:00. */
    val eveningReminderMinute: Int = 21 * 60,
    val bankNotificationsEnabled: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(private val store: DataStore<Preferences>) {

    private object K {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val owner = stringPreferencesKey("owner_name")
        val theme = stringPreferencesKey("theme")
        val defaultAccount = longPreferencesKey("default_expense_account")
        val widgetMode = stringPreferencesKey("widget_balance_mode")
        val widgetSeconds = intPreferencesKey("widget_reveal_seconds")
        val appLock = booleanPreferencesKey("app_lock")
        val hideAmounts = booleanPreferencesKey("hide_amounts")
        val evening = booleanPreferencesKey("evening_reminder")
        val eveningMinute = intPreferencesKey("evening_reminder_minute")
        val bank = booleanPreferencesKey("bank_notifications")
    }

    val preferences: Flow<UserPreferences> = store.data.map { p ->
        UserPreferences(
            onboardingDone = p[K.onboarding] ?: false,
            ownerName = p[K.owner] ?: "",
            theme = p[K.theme]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() } ?: ThemePreference.DARK,
            defaultExpenseAccountId = p[K.defaultAccount],
            widgetBalanceMode = p[K.widgetMode]?.let { runCatching { WidgetBalanceMode.valueOf(it) }.getOrNull() } ?: WidgetBalanceMode.FINGERPRINT,
            widgetRevealSeconds = p[K.widgetSeconds] ?: 5,
            appLockEnabled = p[K.appLock] ?: false,
            hideAmountsInApp = p[K.hideAmounts] ?: false,
            eveningReminderEnabled = p[K.evening] ?: true,
            eveningReminderMinute = p[K.eveningMinute] ?: (21 * 60),
            bankNotificationsEnabled = p[K.bank] ?: false,
        )
    }

    suspend fun current(): UserPreferences = preferences.first()

    suspend fun setOnboardingDone(ownerName: String) = store.edit { it[K.onboarding] = true; it[K.owner] = ownerName }
    suspend fun setTheme(theme: ThemePreference) = store.edit { it[K.theme] = theme.name }
    suspend fun setDefaultExpenseAccount(id: Long) = store.edit { it[K.defaultAccount] = id }
    suspend fun setWidgetBalanceMode(mode: WidgetBalanceMode) = store.edit { it[K.widgetMode] = mode.name }
    suspend fun setAppLock(enabled: Boolean) = store.edit { it[K.appLock] = enabled }
    suspend fun setHideAmounts(enabled: Boolean) = store.edit { it[K.hideAmounts] = enabled }
    suspend fun setEveningReminder(enabled: Boolean) = store.edit { it[K.evening] = enabled }
    suspend fun setBankNotifications(enabled: Boolean) = store.edit { it[K.bank] = enabled }
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore
}
