package com.eminallahverdi.balance.feature.history

import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.core.model.sum
import java.time.YearMonth
import java.util.Locale

/** What the list is narrowed to. [month] null means all time. */
data class HistoryFilter(
    val month: YearMonth? = YearMonth.now(),
    val query: String = "",
    val personId: Long? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
) {
    val range: DateRange get() = month?.let(DateRange::month) ?: DateRange.ALL_TIME

    fun matches(d: TransactionDetails): Boolean =
        (personId == null || d.person?.id == personId) &&
            (categoryId == null || d.category?.id == categoryId || d.parentCategory?.id == categoryId) &&
            (accountId == null || d.account.id == accountId || d.toAccount?.id == accountId) &&
            matchesQuery(d)

    private fun matchesQuery(d: TransactionDetails): Boolean {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return true
        val text = listOfNotNull(d.merchant?.name, d.category?.name, d.parentCategory?.name, d.debt?.counterparty, d.transaction.note)
        return text.any { it.lowercase(Locale.ROOT).contains(q) } || plainAmount(d.transaction.amount).contains(q.replace(',', '.'))
    }

    private fun plainAmount(amount: Money): String = MoneyFormat.format(amount.abs()).removePrefix(MoneyFormat.SYMBOL).replace(",", "")
}

/** What a list opens with: this month, or what was tapped on Home. [month] is a YearMonth as text. */
internal fun initialFilter(month: String?, categoryId: Long?, personId: Long?) = HistoryFilter(
    month = month?.let(YearMonth::parse) ?: YearMonth.now(),
    categoryId = categoryId,
    personId = personId,
)

/** What an empty list says. It only invites a first expense when there's truly nothing yet. */
internal fun HistoryFilter.emptyMessage(now: YearMonth): String = when {
    query.isNotBlank() || personId != null || categoryId != null || accountId != null -> "Nothing matches these filters."
    month == null -> "Nothing here yet. Tap + to add your first expense."
    month == now -> "Nothing this month yet. Tap + to add an expense."
    else -> "Nothing in ${month.label()}."
}

/** One figure for a set of rows: what was spent, or what came in when nothing was spent. */
internal fun List<TransactionDetails>.headlineTotal(): Money {
    val spent = filter { it.transaction.kind == TransactionKind.EXPENSE }
    val rows = spent.ifEmpty { filter { it.transaction.kind == TransactionKind.INCOME } }
    return rows.map { it.transaction.amount }.sum()
}

/** Whether [headlineTotal] is spending, which the eye leaves visible, rather than income, which it hides. */
internal fun List<TransactionDetails>.hasSpending(): Boolean = any { it.transaction.kind == TransactionKind.EXPENSE }

/** The eye hides everything but spending: income, and moves that show what's in an account. */
internal val TransactionDetails.isSensitive: Boolean
    get() = transaction.kind != TransactionKind.EXPENSE

/** Category colour to amount, biggest first, for the summary's split bar. */
internal fun List<TransactionDetails>.categoryShares(): List<Pair<Long, Long>> =
    filter { it.transaction.kind == TransactionKind.EXPENSE }
        .groupBy { (it.parentCategory ?: it.category)?.color ?: 0xFF8A94A3 }
        .map { (color, rows) -> color to rows.sumOf { it.transaction.amount.minor } }
        .sortedByDescending { it.second }

/** The list shows a sign: −₼7.70 spent, +₼50.00 received, and either way for a debt. A transfer is neither. */
internal fun TransactionDetails.signedAmount(): String = when (transaction.kind) {
    TransactionKind.EXPENSE -> MoneyFormat.format(-transaction.amount, signed = true)
    TransactionKind.INCOME -> "+" + MoneyFormat.format(transaction.amount)
    TransactionKind.TRANSFER -> MoneyFormat.format(transaction.amount)
    TransactionKind.DEBT -> if (transaction.amount.isNegative) MoneyFormat.format(transaction.amount, signed = true) else "+" + MoneyFormat.format(transaction.amount)
    TransactionKind.ADJUSTMENT, TransactionKind.INTERNAL -> MoneyFormat.format(transaction.amount, signed = true)
}

internal val TransactionDetails.title: String
    get() = debtLabel.takeIf { transaction.kind == TransactionKind.DEBT } ?: merchant?.name ?: category?.name ?: when (transaction.kind) {
        TransactionKind.TRANSFER -> "Transfer"
        TransactionKind.ADJUSTMENT -> "Balance adjustment"
        else -> "Transaction"
    }

/** "Borrowed from Sam", on an entry that belongs to a debt. */
private val TransactionDetails.debtLabel: String? get() = debt?.label(transaction)

/** "Transport · Taxi · Card", "Card → Cash" for a transfer, and who a paid-back expense went to. */
internal val TransactionDetails.meta: String
    get() = if (toAccount != null) {
        "${account.name} → ${toAccount?.name}"
    } else if (transaction.kind == TransactionKind.DEBT) {
        listOf(account.name, transaction.note).filter { it.isNotBlank() }.joinToString(" · ")
    } else {
        listOfNotNull(parentCategory?.name, category?.name, debtLabel, account.name).joinToString(" · ")
    }

/** Rows quick add can't open that can still be taken back. The money a debt began with goes with the debt, on its own page. */
internal val TransactionDetails.isRemovable: Boolean
    get() = transaction.kind == TransactionKind.ADJUSTMENT || (transaction.kind == TransactionKind.DEBT && debt?.isRepayment(transaction) == true)

/** Quick add edits only what it can add; adjustments and imported internal moves aren't its kinds. */
internal val TransactionDetails.isEditable: Boolean
    get() = transaction.kind == TransactionKind.EXPENSE || transaction.kind == TransactionKind.INCOME || transaction.kind == TransactionKind.TRANSFER
