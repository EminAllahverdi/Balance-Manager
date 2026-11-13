package com.eminallahverdi.balance.feature.history

import androidx.annotation.StringRes
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.common.UiText
import com.eminallahverdi.balance.core.common.rawText
import com.eminallahverdi.balance.core.common.uiText
import com.eminallahverdi.balance.core.designsystem.component.debtNoteText
import com.eminallahverdi.balance.core.designsystem.component.label
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.core.model.sum
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * What the list is narrowed to. [month] null means all time. [span] is a stretch of days that isn't a month, like a week or
 * a year Reports opened, and stands in for [month] while it's set. [kind] is set when the list opens on one kind, like
 * transfers.
 */
data class HistoryFilter(
    val month: YearMonth? = YearMonth.now(),
    val span: DateRange? = null,
    val query: String = "",
    val personId: Long? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val kind: TransactionKind? = null,
) {
    val range: DateRange get() = span ?: month?.let(DateRange::month) ?: DateRange.ALL_TIME

    /** What Clear goes back to: this month, or all time for a list of one kind, with nothing else narrowed. The search has its own clear. */
    fun cleared(now: YearMonth = YearMonth.now()): HistoryFilter = HistoryFilter(month = if (kind != null) null else now, query = query, kind = kind)

    /** Whether there's anything for Clear to undo. */
    fun canClear(now: YearMonth = YearMonth.now()): Boolean = this != cleared(now)

    fun matches(d: TransactionDetails): Boolean =
        (kind == null || d.transaction.kind == kind) &&
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

/**
 * What a list opens with: this month, or what was tapped on Home or in Reports. [month] is a YearMonth as text, and [from] and
 * [to] the days of a period Reports showed, as epoch days. A whole month reads as one, so the month sheet shows it picked. A
 * list of one [kind], like Accounts' See all for transfers, starts on all time, since that's what "all" means there.
 */
internal fun initialFilter(month: String?, categoryId: Long?, personId: Long?, kind: String? = null, from: Long? = null, to: Long? = null): HistoryFilter {
    val only = kind?.let { name -> TransactionKind.entries.firstOrNull { it.name == name } }
    val days = if (from != null && to != null) DateRange(LocalDate.ofEpochDay(from), LocalDate.ofEpochDay(to)) else null
    val wholeMonth = days?.let { YearMonth.from(it.start) }?.takeIf { DateRange.month(it) == days }
    return HistoryFilter(
        month = wholeMonth ?: if (days != null) null else month?.let(YearMonth::parse) ?: if (only != null) null else YearMonth.now(),
        span = days.takeIf { wholeMonth == null },
        categoryId = categoryId,
        personId = personId,
        kind = only,
    )
}

/** What an empty list says. It only invites a first expense when there's truly nothing yet. */
internal fun HistoryFilter.emptyMessage(now: YearMonth): UiText = when {
    query.isNotBlank() || personId != null || categoryId != null || accountId != null -> uiText(R.string.history_nothing_matches)
    span != null -> uiText(R.string.history_nothing_in, span.label())
    kind == TransactionKind.TRANSFER -> if (month == null) uiText(R.string.history_no_transfers_yet) else uiText(R.string.history_no_transfers_in, month.label())
    month == null -> uiText(R.string.history_nothing_yet_first)
    month == now -> uiText(R.string.history_nothing_this_month)
    else -> uiText(R.string.history_nothing_in, month.label())
}

/** "2026" for a whole year, otherwise its first and last days: "3 Nov – 9 Nov". A line can break at the dash, never inside a date. */
internal fun DateRange.label(): String =
    if (start.dayOfYear == 1 && endInclusive == start.withDayOfYear(start.lengthOfYear())) start.year.toString()
    else "${DateLabels.dayMonth(start).keptTogether()} – ${DateLabels.dayMonth(endInclusive).keptTogether()}"

private fun String.keptTogether(): String = replace(' ', ' ')

/** How the list's period is named: a span, else [month] naming the month, or null for all time. */
internal fun HistoryFilter.periodLabel(month: (YearMonth) -> String): String? = span?.label() ?: this.month?.let(month)

/** One figure for a set of rows: what was spent, or what came in when nothing was spent, or what moved when it's only transfers. */
internal fun List<TransactionDetails>.headlineTotal(): Money {
    val spent = filter { it.transaction.kind == TransactionKind.EXPENSE }
    val rows = spent
        .ifEmpty { filter { it.transaction.kind == TransactionKind.INCOME } }
        .ifEmpty { filter { it.transaction.kind == TransactionKind.TRANSFER } }
    return rows.map { it.transaction.amount }.sum()
}

/** Rows grouped by day, keeping their order, each with its headline figure. */
fun List<TransactionDetails>.byDay(): List<HistoryDay> =
    groupBy { it.transaction.date }.map { (date, day) -> HistoryDay(date, day.headlineTotal(), day.hasSpending(), day) }

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

internal val TransactionDetails.title: UiText
    get() = debtLabel.takeIf { transaction.kind == TransactionKind.DEBT } ?: (merchant?.name ?: category?.name)?.let(::rawText) ?: when (transaction.kind) {
        TransactionKind.TRANSFER -> uiText(R.string.history_transfer)
        TransactionKind.ADJUSTMENT -> uiText(R.string.history_balance_adjustment)
        else -> uiText(R.string.history_transaction)
    }

/** "Borrowed from Sam", on an entry that belongs to a debt. */
private val TransactionDetails.debtLabel: UiText? get() = debt?.let { it.role(transaction).label(it.counterparty) }

/** "Transport · Taxi · Card", "Card → Cash" for a transfer, and who a paid-back expense went to. */
internal val TransactionDetails.meta: List<UiText>
    get() = if (toAccount != null) {
        listOf(rawText("${account.name} → ${toAccount?.name}"))
    } else if (transaction.kind == TransactionKind.DEBT) {
        listOf(account.name, transaction.note).filter { it.isNotBlank() }.map(::debtNoteText)
    } else {
        listOfNotNull(parentCategory?.name?.let(::rawText), category?.name?.let(::rawText), debtLabel, rawText(account.name))
    }

/** Rows quick add can't open that can still be taken back. The money a debt began with goes with the debt, on its own page. */
internal val TransactionDetails.isRemovable: Boolean
    get() = transaction.kind == TransactionKind.ADJUSTMENT || (transaction.kind == TransactionKind.DEBT && debt?.isRepayment(transaction) == true)

/** Quick add edits only what it can add; adjustments and imported internal moves aren't its kinds. */
internal val TransactionDetails.isEditable: Boolean
    get() = transaction.kind == TransactionKind.EXPENSE || transaction.kind == TransactionKind.INCOME || transaction.kind == TransactionKind.TRANSFER

/** The question before an entry goes: [named] says which one, as its row does, and [consequence] what changes. */
internal data class Removal(@StringRes val title: Int, val named: List<UiText>, @StringRes val consequence: Int, @StringRes val action: Int)

/** The eye hides the amount here as it does on the row, so only spending is named with one. */
internal fun TransactionDetails.removal(hideAmounts: Boolean, today: LocalDate = LocalDate.now()): Removal {
    val amount = signedAmount().takeUnless { hideAmounts && isSensitive }
    val named = listOfNotNull(title, amount?.let(::rawText), rawText(DateLabels.relative(transaction.date, today)))
    return when (transaction.kind) {
        TransactionKind.DEBT -> Removal(R.string.history_remove_payment_q, named, R.string.history_remove_payment_text, R.string.history_remove)
        TransactionKind.ADJUSTMENT -> Removal(R.string.history_remove_adjustment_q, named, R.string.history_remove_adjustment_text, R.string.history_remove)
        else -> Removal(R.string.history_delete_transaction_q, named, R.string.history_delete_transaction_text, R.string.history_delete)
    }
}
