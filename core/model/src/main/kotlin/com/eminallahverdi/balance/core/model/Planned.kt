package com.eminallahverdi.balance.core.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * A payment the owner means to make, like rent or a school trip: a one-off, unlike a recurring payment. It counts against
 * what's in the accounts until it's paid, which is when it's ticked by hand or an entry for it is added after it was
 * planned. Like a reminder, it never adds an entry by itself.
 */
data class PlannedPayment(
    val id: Long,
    val name: String,
    val amount: Money,
    val date: LocalDate,
    val accountId: Long? = null,
    /** A category or sub-category, so an entry there for the same amount counts as paying it. */
    val categoryId: Long? = null,
    /** The recurring payment it was offered from, so that one isn't offered again for the same date. */
    val recurringId: Long? = null,
    /** The day it was planned. Only entries from then on can pay it. */
    val plannedOn: LocalDate,
    /** Set when it's ticked as paid by hand. */
    val paidOn: LocalDate? = null,
) {
    /** Whether [expense] is this payment, made: the same name, or the same category and amount, from the day it was planned. */
    fun isPaidBy(expense: TransactionDetails): Boolean {
        val entry = expense.transaction
        if (entry.kind != TransactionKind.EXPENSE || entry.date.isBefore(plannedOn)) return false
        return expense.merchant?.name?.caseKey() == name.caseKey() || (categoryId != null && entry.categoryId == categoryId && entry.amount == amount)
    }
}

/** A planned payment and what paid it: an entry, or nothing yet, or the owner's tick. */
data class PlannedStatus(val payment: PlannedPayment, val paidBy: TransactionDetails? = null) {
    val isPaid: Boolean get() = payment.paidOn != null || paidBy != null

    /** The day it was paid, by hand or by the entry. */
    val paidOn: LocalDate? get() = payment.paidOn ?: paidBy?.transaction?.date

    /** Ticked by hand rather than found, so it can be unticked. */
    val isTicked: Boolean get() = payment.paidOn != null
}

/** Each planned payment with what paid it, soonest first. An entry pays one payment at most, the soonest it fits. */
fun plannedStatuses(planned: List<PlannedPayment>, entries: List<TransactionDetails>): List<PlannedStatus> {
    val used = mutableSetOf<Long>()
    val byDate = entries.sortedWith(compareBy({ it.transaction.date }, { it.transaction.id }))
    return planned.sortedWith(compareBy({ it.date }, { it.id })).map { payment ->
        if (payment.paidOn != null) return@map PlannedStatus(payment)
        val match = byDate.firstOrNull { it.transaction.id !in used && payment.isPaidBy(it) }
        match?.let { used += it.transaction.id }
        PlannedStatus(payment, match)
    }
}

/** What's still to pay by the end of [month]: unpaid ones dated in it, or before it and still waiting. */
fun List<PlannedStatus>.toPayBy(month: YearMonth): List<PlannedStatus> = filter { !it.isPaid && !it.payment.date.isAfter(month.atEndOfMonth()) }
