package com.eminallahverdi.balance.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

/** Why the last backup didn't happen. [SIGN_IN] is the one only the user can fix. */
enum class BackupProblem { SIGN_IN, FAILED }

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
    /** Off until the user accepts it: the one thing that adds an entry without being asked. */
    val autoConfirmKnownMerchants: Boolean = false,
    /** The language entries are spoken in, as a BCP 47 tag the recogniser takes, like en-US or az-AZ. */
    val voiceLanguage: String = "en-US",
    /** Packages whose notifications are read. */
    val bankApps: Set<String> = emptySet(),
    /** Packages seen sending notifications, to pick [bankApps] from. Only the names, and only while bank notifications are on. */
    val seenApps: Set<String> = emptySet(),
    /** Home's card shows the month's pie chart instead of the balance. */
    val homeChart: Boolean = false,
    val homeChartIncome: Boolean = false,
    val driveBackupEnabled: Boolean = false,
    /** Off, backups wait for Wi-Fi. */
    val backupOnMobileData: Boolean = false,
    /** Epoch millis of the last backup that reached Drive. */
    val lastBackupAt: Long? = null,
    val backupProblem: BackupProblem? = null,
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
        val autoConfirm = booleanPreferencesKey("auto_confirm_known_merchants")
        val voiceLanguage = stringPreferencesKey("voice_language")
        val bankApps = stringSetPreferencesKey("bank_apps")
        val seenApps = stringSetPreferencesKey("seen_apps")
        val homeChart = booleanPreferencesKey("home_chart")
        val homeChartIncome = booleanPreferencesKey("home_chart_income")
        val driveBackup = booleanPreferencesKey("drive_backup")
        val backupMobileData = booleanPreferencesKey("backup_mobile_data")
        val lastBackup = longPreferencesKey("last_backup_at")
        val backupProblem = stringPreferencesKey("backup_problem")
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
            autoConfirmKnownMerchants = p[K.autoConfirm] ?: false,
            voiceLanguage = p[K.voiceLanguage] ?: "en-US",
            bankApps = p[K.bankApps].orEmpty(),
            seenApps = p[K.seenApps].orEmpty(),
            homeChart = p[K.homeChart] ?: false,
            homeChartIncome = p[K.homeChartIncome] ?: false,
            driveBackupEnabled = p[K.driveBackup] ?: false,
            backupOnMobileData = p[K.backupMobileData] ?: false,
            lastBackupAt = p[K.lastBackup],
            backupProblem = p[K.backupProblem]?.let { runCatching { BackupProblem.valueOf(it) }.getOrNull() },
        )
    }

    suspend fun current(): UserPreferences = preferences.first()

    /** [ownerName] is left as it is when null, for an install from before onboarding that never went through it. */
    suspend fun setOnboardingDone(ownerName: String? = null) {
        store.edit {
            it[K.onboarding] = true
            if (ownerName != null) it[K.owner] = ownerName
        }
    }

    suspend fun setTheme(theme: ThemePreference) { store.edit { it[K.theme] = theme.name } }
    suspend fun setDefaultExpenseAccount(id: Long) { store.edit { it[K.defaultAccount] = id } }
    suspend fun setWidgetBalanceMode(mode: WidgetBalanceMode) { store.edit { it[K.widgetMode] = mode.name } }
    suspend fun setAppLock(enabled: Boolean) { store.edit { it[K.appLock] = enabled } }
    suspend fun setHideAmounts(enabled: Boolean) { store.edit { it[K.hideAmounts] = enabled } }
    suspend fun setVoiceLanguage(tag: String) { store.edit { it[K.voiceLanguage] = tag } }
    suspend fun setEveningReminder(enabled: Boolean) { store.edit { it[K.evening] = enabled } }
    suspend fun setEveningReminderMinute(minute: Int) { store.edit { it[K.eveningMinute] = minute } }
    suspend fun setBankNotifications(enabled: Boolean) { store.edit { it[K.bank] = enabled } }
    suspend fun setAutoConfirmKnownMerchants(enabled: Boolean) { store.edit { it[K.autoConfirm] = enabled } }
    suspend fun setBankApp(packageName: String, enabled: Boolean) {
        store.edit { it[K.bankApps] = if (enabled) it[K.bankApps].orEmpty() + packageName else it[K.bankApps].orEmpty() - packageName }
    }
    suspend fun addSeenApps(packages: Set<String>) { store.edit { it[K.seenApps] = it[K.seenApps].orEmpty() + packages } }
    suspend fun setHomeChart(enabled: Boolean) { store.edit { it[K.homeChart] = enabled } }
    suspend fun setHomeChartIncome(income: Boolean) { store.edit { it[K.homeChartIncome] = income } }
    suspend fun setDriveBackup(enabled: Boolean) { store.edit { it[K.driveBackup] = enabled } }
    suspend fun setBackupOnMobileData(enabled: Boolean) { store.edit { it[K.backupMobileData] = enabled } }
    suspend fun setBackupProblem(problem: BackupProblem) { store.edit { it[K.backupProblem] = problem.name } }
    suspend fun setBackedUp(atMillis: Long) {
        store.edit {
            it[K.lastBackup] = atMillis
            it.remove(K.backupProblem)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore
}
