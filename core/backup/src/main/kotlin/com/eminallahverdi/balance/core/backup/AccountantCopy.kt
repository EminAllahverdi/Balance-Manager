package com.eminallahverdi.balance.core.backup

import android.content.Context
import com.eminallahverdi.balance.core.common.IoDispatcher
import com.eminallahverdi.balance.core.data.BankAlertRepository
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.DebtRepository
import com.eminallahverdi.balance.core.data.PlannedRepository
import com.eminallahverdi.balance.core.data.ReceiptRepository
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.InstallPreferences
import com.eminallahverdi.balance.core.model.AccountantInput
import com.eminallahverdi.balance.core.model.AppEnvironment
import com.eminallahverdi.balance.core.model.PendingStatus
import com.eminallahverdi.balance.core.model.accountantSnapshotHtml
import com.eminallahverdi.balance.core.model.accountantTransactionsCsv
import com.eminallahverdi.balance.core.model.accountantWindow
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * What each file is tagged with, to be found again. Dev and Prod share one Google project, so each sees the other's files:
 * the tags differ, so Dev's test data can never overwrite the copy the accountant reads.
 */
internal fun accountantTag(file: String, environment: AppEnvironment): String =
    "accountant-$file" + if (environment == AppEnvironment.DEV) "-dev" else ""

/** The folder the accountant reads. Dev's has its own name, so test data never reaches the accountant. */
internal fun accountantFolderName(environment: AppEnvironment): String =
    if (environment == AppEnvironment.DEV) "Cibim Dev – accountant" else "Cibim – accountant"

/**
 * The owner's own extra: after each backup, a copy their accountant can read, in a visible folder of their Drive. A Doc
 * with where the money stands and the last months worked out, and a Sheet of every entry in those months. Both are
 * rewritten in place, so the accountant always opens the latest. Off, and hidden behind a PIN, for everyone else.
 */
@Singleton
class AccountantCopy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authorization: DriveAuthorization,
    private val drive: AccountantDrive,
    private val install: InstallPreferences,
    private val environment: AppEnvironment,
    private val catalog: CatalogRepository,
    private val transactions: TransactionRepository,
    private val debts: DebtRepository,
    private val recurring: RecurringRepository,
    private val planned: PlannedRepository,
    private val bankAlerts: BankAlertRepository,
    private val receipts: ReceiptRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Sends the copy when it's on, and records how it went. Never throws; off is a success. */
    suspend fun send(): Boolean = withContext(io) {
        val state = install.accountant.first()
        if (!state.unlocked || !state.on) return@withContext true
        val access = authorization.accountantAccess()
        if (access !is DriveAccess.Granted) {
            install.setAccountantFailed()
            return@withContext false
        }
        val sent = runCatching { upload(access.token, gather()) }.isSuccess
        if (sent) install.setAccountantCopied(Instant.now().toEpochMilli()) else install.setAccountantFailed()
        sent
    }

    private suspend fun gather(): AccountantInput {
        val now = LocalDateTime.now()
        val window = accountantWindow(now.toLocalDate())
        return AccountantInput(
            madeAt = now,
            zone = ZoneId.systemDefault(),
            appVersion = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty(),
            balances = transactions.balances.first().filterNot { it.account.isArchived },
            entries = transactions.observeDetails(window).first(),
            categories = catalog.categories.first(),
            people = catalog.people.first(),
            debts = debts.debts.first(),
            recurring = recurring.payments.first(),
            planned = planned.statuses.first(),
            waiting = bankAlerts.items(Instant.now()).first().map { it.entry }.filter { it.status == PendingStatus.WAITING },
            receipts = receipts.receipts.first().filter { it.date in window },
        )
    }

    private fun upload(token: String, input: AccountantInput) {
        val folderTag = accountantTag("folder", environment)
        val folder = drive.find(token, folderTag) ?: drive.createFolder(token, accountantFolderName(environment), folderTag)
        put(token, folder, accountantTag("snapshot", environment), "Cibim snapshot", DOC_TYPE, "text/html", accountantSnapshotHtml(input))
        put(token, folder, accountantTag("transactions", environment), "Cibim transactions", SHEET_TYPE, "text/csv", accountantTransactionsCsv(input))
    }

    private fun put(token: String, folder: String, tag: String, name: String, googleType: String, contentType: String, text: String) {
        val bytes = text.toByteArray()
        val existing = drive.find(token, tag)
        if (existing != null) drive.replaceContent(token, existing, contentType, bytes) else drive.createConverted(token, name, googleType, folder, tag, contentType, bytes)
    }
}
