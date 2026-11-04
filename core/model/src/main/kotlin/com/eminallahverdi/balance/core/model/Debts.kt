package com.eminallahverdi.balance.core.model

import java.time.LocalDate

enum class DebtDirection { I_OWE, OWED_TO_ME }

/**
 * Money between the user and someone outside the household.
 *
 * With a [categoryId] and [DebtDirection.I_OWE], it's something they paid for on the user's behalf, like a share of a
 * group lunch. No money moves until it's paid back, and then it lands as an ordinary expense in that category.
 *
 * With a [categoryId] and [DebtDirection.OWED_TO_ME], it's a bill the user paid for others too, like lunch for the table.
 * [amount] is the whole bill. The user's own share is an ordinary expense in that category, now or later, and the rest
 * is owed back: it leaves the account as a [TransactionKind.DEBT] entry, and coming back to any account isn't income.
 *
 * Without a category it's a plain loan. Money moves when it's made and again when it's repaid, as [TransactionKind.DEBT]
 * entries, which change a balance but are never spending or income. One that was already running when it was added moved
 * its money before, so nothing moves when it's added, and [paidBefore] is what was paid back by then.
 */
data class Debt(
    val id: Long,
    val counterparty: String,
    val direction: DebtDirection,
    val amount: Money,
    val categoryId: Long? = null,
    /** What it was for, like "Friday lunch". */
    val note: String = "",
    val date: LocalDate,
    /** Paid back before it was added here, so no entry shows it. */
    val paidBefore: Money = Money.ZERO,
    /** The payment agreed for each month, when it's paid back in parts. */
    val monthly: Money? = null,
) {
    val isPlainLoan: Boolean get() = categoryId == null
    val isPaidForMe: Boolean get() = categoryId != null && direction == DebtDirection.I_OWE
    val isPaidForOthers: Boolean get() = categoryId != null && direction == DebtDirection.OWED_TO_ME

    /** Whether it moves a balance, so the eye hides it. A share someone paid for you is spending only once you pay it. */
    val movesMoney: Boolean get() = !isPaidForMe

    /**
     * The money that moved when it was made: a plain loan, or what the others owe of a bill paid for them, after [mine].
     * Someone paying for you moves none of yours.
     */
    fun opening(accountId: Long, mine: Money = Money.ZERO): Transaction? {
        if (isPaidForMe) return null
        val moved = if (direction == DebtDirection.I_OWE) amount else -(amount - mine)
        if (moved.isZero) return null
        return Transaction(id = 0, kind = TransactionKind.DEBT, amount = moved, date = date, accountId = accountId, debtId = id, note = note)
    }

    /** The user's own part of a bill paid for others: an ordinary expense in its category, on the day of the bill. */
    fun share(mine: Money, accountId: Long, personId: Long?): Transaction = Transaction(
        id = 0,
        kind = TransactionKind.EXPENSE,
        amount = mine,
        date = date,
        accountId = accountId,
        categoryId = categoryId,
        personId = personId,
        debtId = id,
        note = note,
    )

    /** The entry for paying [paid] of this back. [personId] is who the expense was for, when it's an expense. */
    fun repayment(paid: Money, accountId: Long, on: LocalDate, personId: Long? = null): Transaction {
        val shared = Transaction(id = 0, kind = TransactionKind.DEBT, amount = paid, date = on, accountId = accountId, debtId = id, note = note)
        return when {
            isPaidForMe -> shared.copy(
                kind = TransactionKind.EXPENSE,
                categoryId = categoryId,
                personId = personId,
                note = listOf(note, "Paid back to $counterparty").filter { it.isNotBlank() }.joinToString(" · "),
            )
            direction == DebtDirection.I_OWE -> shared.copy(amount = -paid)
            else -> shared
        }
    }

    /** How much of this is settled: what was paid before it was added, and the entries that point at it. The opening entry doesn't count. */
    fun paidBack(entries: List<Transaction>): Money = paidBefore + entries.filter { isRepayment(it) }.map { it.amount.abs() }.sum()

    /** The user's own share of a bill paid for others, from the expenses that point at it. */
    fun mine(entries: List<Transaction>): Money =
        if (isPaidForOthers) entries.filter { it.debtId == id && it.kind == TransactionKind.EXPENSE }.map { it.amount }.sum() else Money.ZERO

    fun isRepayment(entry: Transaction): Boolean = when {
        entry.debtId != id -> false
        entry.kind == TransactionKind.EXPENSE -> isPaidForMe
        entry.kind != TransactionKind.DEBT -> false
        direction == DebtDirection.I_OWE -> entry.amount.isNegative
        else -> !entry.amount.isNegative
    }

    /** What a list calls an entry of this debt, so which way the money went is never in doubt. */
    fun label(entry: Transaction): String = when {
        direction == DebtDirection.I_OWE -> if (isRepayment(entry)) "Paid back to $counterparty" else "Borrowed from $counterparty"
        isRepayment(entry) -> "Paid back by $counterparty"
        !isPaidForOthers -> "Lent to $counterparty"
        entry.kind == TransactionKind.EXPENSE -> "Your share"
        else -> "Paid for $counterparty"
    }
}

/** A debt, how much of it is settled, and of a bill paid for others, the user's own share. */
data class DebtBalance(val debt: Debt, val paid: Money, val mine: Money = Money.ZERO) {
    val left: Money get() = debt.amount - mine - paid
    val isSettled: Boolean get() = left.minor <= 0

    /** Whether anything was paid since it was added. One with nothing can still be removed as a mistake. */
    val hasRepayments: Boolean get() = paid > debt.paidBefore

    /** The monthly payments still to go, the last one maybe smaller. Null without an agreed monthly payment. */
    val paymentsLeft: Int?
        get() {
            val monthly = debt.monthly?.minor?.takeIf { it > 0 } ?: return null
            return if (isSettled) 0 else ((left.minor + monthly - 1) / monthly).toInt()
        }

    /** What the next payment comes to: the agreed monthly one, or what's left if that's less. */
    val nextPayment: Money get() = debt.monthly?.takeIf { it.minor > 0 && it < left } ?: left
}

/** The monthly payment that settles [left] in [payments], rounded up to the qəpik so the last one is never bigger. */
fun monthlyFor(left: Money, payments: Int): Money? =
    if (payments <= 0 || left.minor <= 0) null else Money((left.minor + payments - 1) / payments)

/** The names in "Sam, Alex and Kamal", for an even split. */
fun namesIn(who: String): List<String> =
    // Spaces, not \b: on the JVM ə doesn't count as a letter, so \bvə\b would never match.
    who.split(Regex(""",|;|&|\s+(?:and|və|ve)\s+""", RegexOption.IGNORE_CASE)).map { it.trim() }.filter { it.isNotEmpty() }

/**
 * The user's share when [total] is split evenly between them and [others] people. Each of the others owes the same, and
 * the qəpik left over from dividing stays with the user, so the others' shares always add up exactly.
 */
fun evenShare(total: Money, others: Int): Money {
    if (others <= 0) return total
    val each = total.minor / (others + 1)
    return Money(total.minor - each * others)
}
