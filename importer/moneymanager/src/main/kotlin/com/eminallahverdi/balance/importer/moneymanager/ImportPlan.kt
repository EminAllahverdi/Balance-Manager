package com.eminallahverdi.balance.importer.moneymanager

import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.TransactionKind
import java.time.Instant
import java.time.LocalDate
import kotlin.math.absoluteValue

/** One entry as it will be saved. [amount] is positive, except on kinds that carry their own sign. */
data class PlannedEntry(
    val externalId: String,
    val kind: TransactionKind,
    val amount: Long,
    val date: LocalDate,
    val created: Instant,
    val accountUid: String,
    val toAccountUid: String? = null,
    val category: String? = null,
    val sub: String? = null,
    val person: String? = null,
    val merchant: String? = null,
    val note: String = "",
) {
    val categoryType: CategoryType get() = if (kind == TransactionKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE

    fun effectOn(account: String): Long = when (kind) {
        TransactionKind.EXPENSE -> if (accountUid == account) -amount else 0
        TransactionKind.TRANSFER -> when (account) {
            accountUid -> -amount
            toAccountUid -> amount
            else -> 0
        }
        else -> if (accountUid == account) amount else 0
    }
}

data class PlannedAccount(val source: BackupAccount, val mapped: MappedAccount?) {
    val name: String get() = mapped?.rename ?: source.title
}

/** An account whose entries don't add up to the balance in the backup. */
data class BalanceDifference(val account: String, val inBackup: Long, val fromEntries: Long)

data class ImportPlan(
    val accounts: List<PlannedAccount>,
    val people: List<MappedPerson>,
    val categories: List<MappedCategory>,
    val entries: List<PlannedEntry>,
    val defaultAccountUid: String?,
    val differences: List<BalanceDifference>,
)

/** Works out everything the import will write, without touching the database, so the same plan can be previewed and tested. */
fun planImport(backup: Backup, mapping: ImportMapping): ImportPlan {
    val entries = backup.entries.map { planEntry(it, mapping.rules) } + backup.transfers.map { planTransfer(it) }
    val mappedNames = mapping.people.map { it.name }.toSet()
    val namedOnlyInRules = entries.mapNotNull { it.person }.distinct().filter { it !in mappedNames }.map { MappedPerson(it) }
    return ImportPlan(
        accounts = backup.accounts.sortedBy { it.position }.map { account -> PlannedAccount(account, mapping.accounts.firstOrNull { it.name == account.title }) },
        people = mapping.people + namedOnlyInRules,
        categories = mapping.categories,
        entries = entries.sortedWith(compareBy({ it.date }, { it.created })),
        defaultAccountUid = backup.accounts.firstOrNull { it.title == mapping.defaultAccount }?.uid ?: backup.defaultAccountUid,
        differences = backup.accounts.mapNotNull { account ->
            val total = entries.sumOf { it.effectOn(account.uid) }
            if (total == account.balance) null else BalanceDifference(account.title, account.balance, total)
        },
    )
}

/** An entry with no category is the old app's balance correction, so it starts as an adjustment rather than spending. */
internal fun planEntry(entry: BackupEntry, rules: List<MappingRule>): PlannedEntry {
    var kind = when {
        entry.category == null -> TransactionKind.ADJUSTMENT
        entry.isIncome -> TransactionKind.INCOME
        else -> TransactionKind.EXPENSE
    }
    var category = entry.category
    var sub: String? = null
    var person: String? = null
    var merchant: String? = null
    var fallbackNote: String? = null
    val explained = mutableSetOf<String>()
    rules.filter { it.match.matches(entry) }.forEach { rule ->
        rule.match.tag?.let { explained += it }
        rule.set.kind?.let { kind = TransactionKind.valueOf(it) }
        rule.set.category?.let {
            category = it
            sub = null
        }
        rule.set.sub?.let { sub = it }
        rule.set.person?.let { person = it }
        rule.set.merchant?.let { merchant = it }
        rule.set.note?.let { fallbackNote = it }
    }
    // A tag no rule mentions still says something, so it isn't lost.
    if (sub == null) sub = entry.tags.firstOrNull { it !in explained }
    val signed = kind == TransactionKind.ADJUSTMENT || kind == TransactionKind.INTERNAL
    return PlannedEntry(
        externalId = externalId(entry.uid),
        kind = kind,
        amount = if (signed) (if (entry.isIncome) entry.amount else -entry.amount) else entry.amount.absoluteValue,
        date = entry.date,
        created = entry.created,
        accountUid = entry.accountUid,
        category = category.takeUnless { signed },
        sub = sub.takeUnless { signed },
        person = person,
        merchant = merchant,
        note = entry.note.trim().ifEmpty { fallbackNote.orEmpty() },
    )
}

internal fun planTransfer(transfer: BackupTransfer) = PlannedEntry(
    externalId = externalId(transfer.uid),
    kind = TransactionKind.TRANSFER,
    amount = transfer.amount,
    date = transfer.date,
    created = transfer.created,
    accountUid = transfer.fromUid,
    toAccountUid = transfer.toUid,
    note = transfer.note.trim(),
)

private fun externalId(uid: String) = "moneymanager:$uid"
