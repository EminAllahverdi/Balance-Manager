package com.eminallahverdi.balance.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.common.IoDispatcher
import com.eminallahverdi.balance.core.data.BankAlertRepository
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.ThemePreference
import com.eminallahverdi.balance.core.datastore.UserPreferences
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.datastore.WidgetBalanceMode
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.transactionsToCsv
import com.eminallahverdi.balance.importer.moneymanager.ImportPreview
import com.eminallahverdi.balance.importer.moneymanager.MoneyManagerImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val accounts: List<Account> = emptyList(),
    val categoryCount: Int = 0,
    val peopleCount: Int = 0,
    val recurring: List<RecurringPayment> = emptyList(),
    val toConfirm: Int = 0,
) {
    /** Quick add falls back to the first account until one is chosen, so show that here too. */
    val defaultAccount: Account? get() = accounts.firstOrNull { it.id == preferences.defaultExpenseAccountId } ?: accounts.firstOrNull()

    val recurringSummary: String
        get() = if (recurring.isEmpty()) "None yet" else recurring.take(SUMMARY_NAMES).joinToString(", ") { it.name } + " · ${recurring.size}"

    private companion object {
        const val SUMMARY_NAMES = 3
    }
}

/** [preview] opens the sheet. [message] is what the row says about the last attempt. */
data class ImportUiState(
    val preview: ImportPreview? = null,
    val hasMapping: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    catalog: CatalogRepository,
    recurring: RecurringRepository,
    bankAlerts: BankAlertRepository,
    private val transactions: TransactionRepository,
    private val prefs: UserPreferencesRepository,
    private val importer: MoneyManagerImporter,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val exportResult = MutableStateFlow<String?>(null)
    private val importing = MutableStateFlow(ImportUiState())
    private var backupFile: Uri? = null
    private var mappingFile: Uri? = null

    /** What the last export came to, for its row. */
    val exportMessage: StateFlow<String?> = exportResult
    val importState: StateFlow<ImportUiState> = importing

    val state: StateFlow<SettingsUiState> = combine(
        combine(prefs.preferences, bankAlerts.waitingCount) { p, waiting -> p to waiting },
        catalog.accounts,
        catalog.categories,
        catalog.people,
        recurring.payments,
    ) { (p, waiting), accounts, categories, people, payments ->
        SettingsUiState(
            toConfirm = waiting,
            preferences = p,
            accounts = accounts.filterNot { it.isArchived },
            categoryCount = categories.count { it.parentId == null && !it.isArchived },
            peopleCount = people.count { !it.isArchived },
            recurring = payments.filterNot { it.isFinished },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDefaultAccount(id: Long) { viewModelScope.launch { prefs.setDefaultExpenseAccount(id) } }
    fun setTheme(theme: ThemePreference) { viewModelScope.launch { prefs.setTheme(theme) } }
    fun setHideAmounts(hide: Boolean) { viewModelScope.launch { prefs.setHideAmounts(hide) } }
    fun setWidgetBalanceMode(mode: WidgetBalanceMode) { viewModelScope.launch { prefs.setWidgetBalanceMode(mode) } }
    fun setAppLock(enabled: Boolean) { viewModelScope.launch { prefs.setAppLock(enabled) } }

    /** Writes every entry to the file the user picked. The byte order mark tells Excel it's UTF-8, or ə and ş come out wrong. */
    fun exportCsv(file: Uri) {
        viewModelScope.launch(io) {
            val entries = transactions.observeDetails(DateRange.ALL_TIME).first()
            val saved = runCatching {
                context.contentResolver.openOutputStream(file)?.use { it.write((Char(0xFEFF) + transactionsToCsv(entries)).toByteArray()) } != null
            }.getOrDefault(false)
            exportResult.value = when {
                !saved -> "Couldn't save the file"
                entries.size == 1 -> "1 entry saved"
                else -> "${entries.size} entries saved"
            }
        }
    }

    fun chooseBackup(file: Uri) {
        backupFile = file
        mappingFile = null
        loadPreview()
    }

    fun chooseMapping(file: Uri) {
        mappingFile = file
        loadPreview()
    }

    fun closeImport() = importing.update { it.copy(preview = null) }

    private fun loadPreview() {
        val backup = backupFile ?: return
        importing.update { it.copy(message = "Reading the backup…") }
        viewModelScope.launch {
            runCatching { importer.prepare(backup, mappingFile) }
                .onSuccess { preview -> importing.value = ImportUiState(preview, hasMapping = mappingFile != null) }
                .onFailure { importing.update { it.copy(message = "Couldn't read that file") } }
        }
    }

    /** Nothing changes before this, and the database rolls back if it fails partway. */
    fun runImport() {
        val plan = importing.value.preview?.plan ?: return
        importing.value = ImportUiState(message = "Importing…")
        viewModelScope.launch {
            val done = runCatching { importer.import(plan) }.isSuccess
            importing.value = ImportUiState(message = if (done) "${plan.entries.size} entries imported" else "The import failed, so nothing changed")
        }
    }

    fun setBankNotifications(enabled: Boolean) { viewModelScope.launch { prefs.setBankNotifications(enabled) } }
    fun setBankApp(packageName: String, enabled: Boolean) { viewModelScope.launch { prefs.setBankApp(packageName, enabled) } }
    fun setEveningReminder(enabled: Boolean) { viewModelScope.launch { prefs.setEveningReminder(enabled) } }
    fun setEveningReminderMinute(minute: Int) { viewModelScope.launch { prefs.setEveningReminderMinute(minute) } }
}
