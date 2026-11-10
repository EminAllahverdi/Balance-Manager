package com.eminallahverdi.balance.core.model

/** A debt as quick add or a spoken sentence makes it: one of the four new kinds, or someone paying back. */
enum class DebtType {
    PAID_FOR_OTHERS,
    PAID_FOR_ME,
    LENT,
    BORROWED,
    PAID_BACK,
    ;

    /** A bill, or a share of one: it's in a category, because some of it becomes spending. */
    val hasCategory: Boolean get() = this == PAID_FOR_OTHERS || this == PAID_FOR_ME

    /** Who ends up owing: the user, or the people named. */
    val direction: DebtDirection get() = if (this == PAID_FOR_ME || this == BORROWED) DebtDirection.I_OWE else DebtDirection.OWED_TO_ME
}

/** Everyone the user owes, or who owes them, as one line: what's left, and the debts it's made of, oldest first. */
data class OwedTo(val name: String, val direction: DebtDirection, val debts: List<DebtBalance>) {
    val left: Money get() = debts.map { it.left }.sum()
}

/** Open debts, one line per person and direction, the most owed first. A name is the same person whatever its case. */
fun List<DebtBalance>.owedByPerson(): List<OwedTo> =
    filterNot { it.isSettled }
        .groupBy { it.debt.counterparty.trim().lowercase() to it.debt.direction }
        .values
        .map { group -> OwedTo(group.first().debt.counterparty.trim(), group.first().debt.direction, group.sortedWith(compareBy({ it.debt.date }, { it.debt.id }))) }
        .sortedByDescending { it.left.minor }

/**
 * [amount] paid back across [debts], oldest first, each up to what's left on it, so one payment settles the old ones before
 * it starts on the new. More than is owed stops at what's owed.
 */
fun payBack(amount: Money, debts: List<DebtBalance>): List<Pair<DebtBalance, Money>> {
    var rest = amount.minor
    return debts.mapNotNull { balance ->
        val part = minOf(rest, balance.left.minor).takeIf { it > 0 } ?: return@mapNotNull null
        rest -= part
        balance to Money(part)
    }
}
