package com.eminallahverdi.balance.core.data

import androidx.room.withTransaction
import com.eminallahverdi.balance.core.database.BalanceDatabase
import com.eminallahverdi.balance.core.database.dao.MerchantDao
import com.eminallahverdi.balance.core.database.dao.PendingEntryDao
import com.eminallahverdi.balance.core.database.entity.MerchantAliasEntity
import com.eminallahverdi.balance.core.database.entity.PendingEntryEntity
import com.eminallahverdi.balance.core.datastore.UserPreferences
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.EntryGuess
import com.eminallahverdi.balance.core.model.Merchant
import com.eminallahverdi.balance.core.model.PendingEntry
import com.eminallahverdi.balance.core.model.PendingStatus
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.Transaction
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.core.model.guessFor
import com.eminallahverdi.balance.core.model.parseBankNotification
import com.eminallahverdi.balance.core.model.whyNotRead
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** Tells MainActivity to open To confirm, from the launcher shortcut or the widget. */
const val EXTRA_OPEN_CONFIRM = "openConfirm"

private const val DAYS_TO_REMEMBER_FINISHED = 30L

data class PendingItem(val entry: PendingEntry, val guess: EntryGuess)

/** A notification from a picked bank app that wasn't read, and why. Kept in memory only, so it never reaches a backup. */
data class UnreadNotification(val source: String, val title: String, val text: String, val postedAt: Instant, val why: String)

private const val UNREAD_TO_KEEP = 10

private class Known(val aliases: Map<String, Long>, val merchants: List<Merchant>, val categories: List<Category>, val payments: List<RecurringPayment>)

/**
 * Turns bank notifications into ready-made entries. Nothing reaches the list until the user confirms it, with one
 * exception they have to accept first: auto-confirm, for merchants they've confirmed before.
 */
@Singleton
class BankAlertRepository @Inject constructor(
    private val db: BalanceDatabase,
    private val dao: PendingEntryDao,
    private val merchantDao: MerchantDao,
    private val transactions: TransactionRepository,
    private val catalog: CatalogRepository,
    private val recurring: RecurringRepository,
    private val preferences: UserPreferencesRepository,
) {
    val waitingCount: Flow<Int> = dao.observeWaitingCount()

    private val skipped = MutableStateFlow<List<UnreadNotification>>(emptyList())

    /** The last few notifications from picked bank apps that weren't read, newest first, since the app started. */
    val unread: StateFlow<List<UnreadNotification>> = skipped.asStateFlow()

    /** Everything waiting, and what was added since [since], with what each would be saved as. */
    fun items(since: Instant): Flow<List<PendingItem>> {
        val known = combine(dao.observeAliases(), catalog.merchants, catalog.categories, recurring.payments) { aliases, merchants, categories, payments ->
            Known(aliases.associate { it.raw to it.merchantId }, merchants, categories, payments)
        }
        return combine(dao.observe(since.toEpochMilli()), known, catalog.accounts, preferences.preferences) { entries, k, accounts, settings ->
            val defaultAccount = defaultAccountId(settings, accounts)
            entries.map { it.toModel() }.map { PendingItem(it, guessFor(it, k.aliases, k.merchants, k.categories, defaultAccount, k.payments, accounts)) }
        }
    }

    suspend fun entry(id: Long): PendingEntry? = dao.get(id)?.toModel()

    suspend fun guess(entry: PendingEntry): EntryGuess {
        val aliases = dao.observeAliases().first().associate { it.raw to it.merchantId }
        val accounts = catalog.accounts.first()
        val defaultAccount = defaultAccountId(preferences.current(), accounts)
        return guessFor(entry, aliases, catalog.merchants.first(), catalog.categories.first(), defaultAccount, recurring.payments.first(), accounts)
    }

    /** Remembers which apps send notifications, so bank apps can be picked from them. Only their names. */
    suspend fun noteApps(packages: Set<String>) {
        val settings = preferences.current()
        if (settings.bankNotificationsEnabled && !settings.seenApps.containsAll(packages)) preferences.addSeenApps(packages)
    }

    /** One notification, from any app. It's only read if bank notifications are on and the user picked this app. */
    suspend fun receive(packageName: String, source: String, title: String, text: String, postedAt: Instant, setAside: String? = null) {
        val settings = preferences.current()
        if (!settings.bankNotificationsEnabled) return
        noteApps(setOf(packageName))
        if (packageName !in settings.bankApps) return
        if (setAside != null) {
            // A group's own summary usually has no text, and says nothing worth showing.
            if (title.isNotBlank() || text.isNotBlank()) rememberUnread(UnreadNotification(source, title, text, postedAt, setAside))
            return
        }
        val alert = parseBankNotification(title, text, appName = source)
        if (alert == null) {
            whyNotRead(title, text, appName = source)?.let { rememberUnread(UnreadNotification(source, title, text, postedAt, it)) }
            return
        }
        val id = dao.insert(
            PendingEntryEntity(
                externalId = fingerprint(packageName, title, text, postedAt),
                source = source,
                amountMinor = alert.amount.minor,
                kind = alert.kind.name,
                merchant = alert.merchant,
                title = title.takeUnless { it.equals(source, ignoreCase = true) }.orEmpty(),
                postedAt = postedAt.toEpochMilli(),
                status = PendingStatus.WAITING.name,
                cardEnding = alert.cardEnding,
            ),
        )
        if (id == -1L) return
        dao.deleteFinishedBefore(postedAt.minus(DAYS_TO_REMEMBER_FINISHED, ChronoUnit.DAYS).toEpochMilli())
        // The one thing that adds an entry unasked, and only after the user accepted it, for merchants they've confirmed themselves.
        if (settings.autoConfirmKnownMerchants) entry(id)?.let { if (guess(it).isKnown) confirm(id) }
    }

    private fun rememberUnread(unread: UnreadNotification) = skipped.update { list ->
        (listOf(unread) + list.filterNot { it.title == unread.title && it.text == unread.text && it.postedAt == unread.postedAt })
            .sortedByDescending { it.postedAt }
            .take(UNREAD_TO_KEEP)
    }

    /** Saves the entry as guessed. A merchant with no category yet has to go through quick add. */
    suspend fun confirm(id: Long) {
        val entry = entry(id)?.takeIf { it.status == PendingStatus.WAITING } ?: return
        val guess = guess(entry)
        if (entry.kind == TransactionKind.TRANSFER) return confirmTransfer(entry, guess)
        val categoryId = guess.categoryId ?: return
        val accountId = guess.accountId ?: return
        val personId = guess.personId ?: catalog.people.first().firstOrNull { !it.isArchived }?.id
        db.withTransaction {
            transactions.save(
                Transaction(
                    id = 0,
                    kind = entry.kind,
                    amount = entry.amount,
                    date = entry.postedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
                    accountId = accountId,
                    categoryId = categoryId,
                    personId = personId,
                ),
                merchantName = guess.merchantName,
                externalId = entry.externalId,
            )
            markAdded(entry, guess.merchantName)
        }
    }

    /** Money between two accounts: no category, no person, and nothing to teach, because the place it happened isn't a merchant. */
    private suspend fun confirmTransfer(entry: PendingEntry, guess: EntryGuess) {
        val accountId = guess.accountId ?: return
        val toAccountId = guess.toAccountId ?: return
        db.withTransaction {
            transactions.save(
                Transaction(
                    id = 0,
                    kind = entry.kind,
                    amount = entry.amount,
                    date = entry.postedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
                    accountId = accountId,
                    toAccountId = toAccountId,
                ),
                merchantName = null,
                externalId = entry.externalId,
            )
            dao.setStatus(entry.id, PendingStatus.ADDED.name)
        }
    }

    /** Teaches the merchant as well, which is what makes it known next time. Quick add calls this after saving an edited entry. */
    suspend fun markAdded(entry: PendingEntry, merchantName: String) {
        dao.setStatus(entry.id, PendingStatus.ADDED.name)
        if (entry.merchant.isBlank()) return
        merchantDao.findByName(merchantName.trim())?.let { dao.upsertAlias(MerchantAliasEntity(entry.merchant.lowercase(Locale.ROOT), it.id)) }
    }

    suspend fun skip(id: Long) = dao.setStatus(id, PendingStatus.SKIPPED.name)

    private fun defaultAccountId(settings: UserPreferences, accounts: List<Account>): Long? {
        val active = accounts.filterNot { it.isArchived }
        return active.firstOrNull { it.id == settings.defaultExpenseAccountId }?.id ?: active.firstOrNull()?.id
    }

    /** The same notification posted again gets the same value. Two equal payments don't, as their times differ. */
    private fun fingerprint(packageName: String, title: String, text: String, postedAt: Instant): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$packageName|$title|$text|${postedAt.toEpochMilli()}".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
