package com.eminallahverdi.balance.core.model

import java.time.LocalDate

enum class DebtDirection { I_OWE, OWED_TO_ME }

/**
 * Money between the user and someone outside the household.
 *
 * With a [categoryId] it's something they paid for on the user's behalf, like a share of a group lunch. No money
 * moves until it's paid back, and then it lands as an ordinary expense in that category.
 *
 * Without one it's a plain loan. Money moves when it's made and again when it's repaid, as [TransactionKind.DEBT]
 * entries, which change a balance but are never spending or income.
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
) {
    val isPlainLoan: Boolean get() = categoryId == null

    /** The money that moved when a plain loan was made. Someone paying for you moves none of yours. */
    fun opening(accountId: Long): Transaction? = if (categoryId != null) {
        null
    } else {
        Transaction(
            id = 0,
            kind = TransactionKind.DEBT,
            amount = if (direction == DebtDirection.I_OWE) amount else -amount,
            date = date,
            accountId = accountId,
            debtId = id,
            note = note,
        )
    }

    /** The entry for paying [paid] of this back. [personId] is who the expense was for, when it's an expense. */
    fun repayment(paid: Money, accountId: Long, on: LocalDate, personId: Long? = null): Transaction {
        val shared = Transaction(id = 0, kind = TransactionKind.DEBT, amount = paid, date = on, accountId = accountId, debtId = id, note = note)
        return when {
            categoryId != null -> shared.copy(
                kind = TransactionKind.EXPENSE,
                categoryId = categoryId,
                personId = personId,
                note = listOf(note, "Paid back to $counterparty").filter { it.isNotBlank() }.joinToString(" · "),
            )
            direction == DebtDirection.I_OWE -> shared.copy(amount = -paid)
            else -> shared
        }
    }

    /** How much of this is settled, from the entries that point at it. The opening entry doesn't count. */
    fun paidBack(entries: List<Transaction>): Money = entries.filter { isRepayment(it) }.map { it.amount.abs() }.sum()

    fun isRepayment(entry: Transaction): Boolean = when {
        entry.debtId != id -> false
        entry.kind == TransactionKind.EXPENSE -> true
        entry.kind != TransactionKind.DEBT -> false
        direction == DebtDirection.I_OWE -> entry.amount.isNegative
        else -> !entry.amount.isNegative
    }

    /** What a list calls an entry of this debt, so which way the money went is never in doubt. */
    fun label(entry: Transaction): String = when (direction) {
        DebtDirection.I_OWE -> if (isRepayment(entry)) "Paid back to $counterparty" else "Borrowed from $counterparty"
        DebtDirection.OWED_TO_ME -> if (isRepayment(entry)) "Paid back by $counterparty" else "Lent to $counterparty"
    }
}

/** A debt and how much of it is settled. */
data class DebtBalance(val debt: Debt, val paid: Money) {
    val left: Money get() = debt.amount - paid
    val isSettled: Boolean get() = left.minor <= 0
}
