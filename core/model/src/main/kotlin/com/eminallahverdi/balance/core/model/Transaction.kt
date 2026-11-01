package com.eminallahverdi.balance.core.model

import java.time.Instant
import java.time.LocalDate

enum class TransactionKind {
    EXPENSE,
    INCOME,
    /** Money moved from [Transaction.accountId] to [Transaction.toAccountId]. Not spending, not income. */
    TRANSFER,
    /** Correction so an account matches the bank. Signed amount. Left out of charts. */
    ADJUSTMENT,
    /** Legacy bookkeeping moves from an import (e.g. old "taksit income"). Signed amount. Left out of charts. */
    INTERNAL,
    /** Money borrowed, lent or repaid. Changes a balance but is neither spending nor income. Signed amount. Left out of charts. */
    DEBT,
}

data class Transaction(
    val id: Long,
    val kind: TransactionKind,
    /** Positive for EXPENSE, INCOME and TRANSFER; signed for ADJUSTMENT, INTERNAL and DEBT. */
    val amount: Money,
    val date: LocalDate,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val personId: Long? = null,
    val merchantId: Long? = null,
    val note: String = "",
    val tripName: String? = null,
    /** The debt this settles or opens, so its remaining amount always follows the entries. */
    val debtId: Long? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    /** Effect of this transaction on the balance of [account]. */
    fun effectOn(account: Long): Money = when (kind) {
        TransactionKind.EXPENSE -> if (accountId == account) -amount else Money.ZERO
        TransactionKind.INCOME -> if (accountId == account) amount else Money.ZERO
        TransactionKind.TRANSFER -> when (account) {
            accountId -> -amount
            toAccountId -> amount
            else -> Money.ZERO
        }
        TransactionKind.ADJUSTMENT, TransactionKind.INTERNAL, TransactionKind.DEBT -> if (accountId == account) amount else Money.ZERO
    }

    /** Whether this counts towards spending/income charts. */
    val countsInReports: Boolean get() = kind == TransactionKind.EXPENSE || kind == TransactionKind.INCOME
}

/** The correction that brings an account from [current] to [target], or null when they already match. */
fun balanceAdjustment(accountId: Long, current: Money, target: Money, date: LocalDate): Transaction? {
    val difference = target - current
    if (difference.isZero) return null
    return Transaction(id = 0, kind = TransactionKind.ADJUSTMENT, amount = difference, date = date, accountId = accountId)
}

/** A transaction joined with everything the UI needs to draw a row. */
data class TransactionDetails(
    val transaction: Transaction,
    val account: Account,
    val toAccount: Account?,
    val category: Category?,
    val parentCategory: Category?,
    val person: Person?,
    val merchant: Merchant?,
    val debt: Debt? = null,
)
