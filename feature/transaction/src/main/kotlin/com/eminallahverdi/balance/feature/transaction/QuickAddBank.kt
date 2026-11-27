package com.eminallahverdi.balance.feature.transaction

import com.eminallahverdi.balance.core.model.DebtDirection
import com.eminallahverdi.balance.core.model.DebtType
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.OwedTo
import com.eminallahverdi.balance.core.model.PendingEntry
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.core.model.caseKey
import com.eminallahverdi.balance.core.model.namesIn

/** Which way a bank payment's money went, which decides what kind of debt it can be. */
enum class BankFlow { OUT, IN }

/** Money in is income as the bank read it. A payment and a cash withdrawal both left an account. */
internal val PendingEntry.flow: BankFlow get() = if (kind == TransactionKind.INCOME) BankFlow.IN else BankFlow.OUT

/**
 * The new debts the Debt tab offers. For a bank payment, only those that move money the way it went: money that left is a
 * bill paid for others or money lent, and money that came in is money borrowed. Someone paying for the user moves none of
 * theirs, so it never fits a bank payment.
 */
internal fun newDebtsFor(flow: BankFlow?): Set<DebtType> = when (flow) {
    null -> setOf(DebtType.PAID_FOR_OTHERS, DebtType.PAID_FOR_ME, DebtType.LENT, DebtType.BORROWED)
    BankFlow.OUT -> setOf(DebtType.PAID_FOR_OTHERS, DebtType.LENT)
    BankFlow.IN -> setOf(DebtType.BORROWED)
}

/**
 * People to settle with for a bank payment: those the user owes when money left, those who owe the user when it came in.
 * Someone the notification names comes first, as "C2C SAM" puts Sam at the top. Without a bank payment, everyone.
 */
internal fun List<OwedTo>.forBank(entry: PendingEntry?): List<OwedTo> {
    entry ?: return this
    val direction = if (entry.flow == BankFlow.OUT) DebtDirection.I_OWE else DebtDirection.OWED_TO_ME
    val said = "${entry.merchant} ${entry.title}"
    return filter { it.direction == direction }.sortedByDescending { line -> namesIn(line.name).any { named(said, it) } }
}

/** Whether [name] stands as a word of its own in [text], whatever its case, so Sam isn't found in SAMSUNG. */
internal fun named(text: String, name: String): Boolean {
    val key = name.trim().caseKey()
    if (key.isEmpty()) return false
    return Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(key) + "($|[^\\p{L}\\p{N}])").containsMatchIn(text.caseKey())
}

/** Paying back from a bank payment: what the bank sent, never more than is left, which the debt couldn't take. */
internal fun bankPayBack(bank: Money?, left: Money): Money = if (bank != null && bank < left) bank else left
