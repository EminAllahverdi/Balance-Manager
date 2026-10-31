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
}

data class Transaction(
    val id: Long,
    val kind: TransactionKind,
    /** Positive for EXPENSE, INCOME and TRANSFER; signed for ADJUSTMENT and INTERNAL. */
    val amount: Money,
    val date: LocalDate,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val personId: Long? = null,
    val merchantId: Long? = null,
    val note: String = "",
    val tripName: String? = null,
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
        TransactionKind.ADJUSTMENT, TransactionKind.INTERNAL -> if (accountId == account) amount else Money.ZERO
    }

    /** Whether this counts towards spending/income charts. */
    val countsInReports: Boolean get() = kind == TransactionKind.EXPENSE || kind == TransactionKind.INCOME
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
)
