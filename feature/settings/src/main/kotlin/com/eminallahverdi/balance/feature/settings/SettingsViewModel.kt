package com.eminallahverdi.balance.feature.settings

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.common.IoDispatcher
import com.eminallahverdi.balance.core.data.BankAlertRepository
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.ThemePreference
import com.eminallahverdi.balance.core.datastore.UserPreferences
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.datastore.WidgetBalanceMode
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.ExportSpan
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.exportFileName
import com.eminallahverdi.balance.core.model.transactionsToCsv
import com.eminallahverdi.balance.importer.moneymanager.ImportPreview
import com.eminallahverdi.balance.importer.moneymanager.MoneyManagerImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val accounts: List<Account> = emptyList(),
    val categoryCount: Int = 0,
    val peopleCount: Int = 0,
    val toConfirm: Int = 0,
)

/** The export sheet: a ready-made [span] or dates picked by hand, which clear it, and how many entries fall in them. */
data class ExportDraft(val span: ExportSpan?, val from: LocalDate, val to: LocalDate, val count: Int? = null) {
    val range: DateRange get() = DateRange(from, to)
    val fileName: String get() = exportFileName(range)
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
    ) { (p, waiting), accounts, categories, people ->
        SettingsUiState(
            toConfirm = waiting,
            preferences = p,
            accounts = accounts.filterNot { it.isArchived },
            categoryCount = categories.count { it.parentId == null && !it.isArchived },
            peopleCount = people.count { !it.isArchived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(theme: ThemePreference) { viewModelScope.launch { prefs.setTheme(theme) } }
    fun setWidgetBalanceMode(mode: WidgetBalanceMode) { viewModelScope.launch { prefs.setWidgetBalanceMode(mode) } }
    fun setAppLock(enabled: Boolean) { viewModelScope.launch { prefs.setAppLock(enabled) } }

    private val exporting = MutableStateFlow<ExportDraft?>(null)
    private var entryDates: DateRange? = null

    /** The export sheet while it's open, with the count of what it would write kept up to date. */
    val export: StateFlow<ExportDraft?> = exporting
        .flatMapLatest { draft -> if (draft == null) flowOf(null) else transactions.countIn(draft.range).map { draft.copy(count = it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openExport() {
        viewModelScope.launch {
            entryDates = transactions.entryDates()
            exporting.value = draftFor(ExportSpan.THIS_MONTH)
        }
    }

    fun closeExport() = exporting.update { null }

    fun pickSpan(span: ExportSpan) = exporting.update { draftFor(span) }

    /** A date picked by hand: the span no longer says what it is, and the other end moves if they'd cross. */
    fun setFrom(date: LocalDate) = exporting.update { it?.copy(span = null, from = date, to = maxOf(date, it.to)) }

    fun setTo(date: LocalDate) = exporting.update { it?.copy(span = null, to = date, from = minOf(date, it.from)) }

    private fun draftFor(span: ExportSpan): ExportDraft = span.dates(LocalDate.now(), entryDates).let { ExportDraft(span, it.start, it.endInclusive) }

    /** Writes the entries in the sheet's dates to the file the user picked. */
    fun exportCsv(file: Uri) {
        val range = exporting.value?.range ?: return
        exporting.value = null
        viewModelScope.launch(io) {
            val entries = transactions.observeDetails(range).first()
            val saved = runCatching { context.contentResolver.openOutputStream(file)?.use { it.write(csvBytes(entries)) } != null }.getOrDefault(false)
            exportResult.value = when {
                !saved -> "Couldn't save the file"
                entries.size == 1 -> "1 entry saved"
                else -> "${entries.size} entries saved"
            }
        }
    }

    /**
     * Writes the entries in the sheet's dates to a file only this app's share link can open, for the share sheet to hand to
     * another app. The folder holds only the latest export, so nothing piles up. The sheet stays open until the share sheet
     * is up: closing it first would cancel the work that's waiting for the file.
     */
    suspend fun shareFile(): Uri? {
        val draft = exporting.value ?: return null
        return withContext(io) {
            val entries = transactions.observeDetails(draft.range).first()
            runCatching {
                val folder = File(context.cacheDir, EXPORT_FOLDER).apply { deleteRecursively(); mkdirs() }
                val file = File(folder, draft.fileName).apply { writeBytes(csvBytes(entries)) }
                FileProvider.getUriForFile(context, "${context.packageName}$EXPORT_AUTHORITY", file)
            }.getOrNull()
        }
    }

    /** The byte order mark tells Excel it's UTF-8, or ə and ş come out wrong. */
    private fun csvBytes(entries: List<TransactionDetails>): ByteArray = (Char(0xFEFF) + transactionsToCsv(entries)).toByteArray()

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

/** Where a shared export is written, and the share link's authority after the package name. AndroidManifest.xml names both. */
private const val EXPORT_FOLDER = "exports"
private const val EXPORT_AUTHORITY = ".exports"
