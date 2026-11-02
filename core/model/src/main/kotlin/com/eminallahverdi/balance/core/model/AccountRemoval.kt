package com.eminallahverdi.balance.core.model

/**
 * What deleting an account does to the others, from every entry that touches it. Only a transfer has another side, so
 * nothing else shows here. Accounts whose balance ends up the same are left out.
 */
fun balanceChangesFromDeleting(account: Long, entries: List<Transaction>): Map<Long, Money> =
    entries.filter { it.kind == TransactionKind.TRANSFER }
        .mapNotNull { t -> (if (t.accountId == account) t.toAccountId else t.accountId)?.let { other -> other to -t.effectOn(other) } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, changes) -> changes.sum() }
        .filterValues { !it.isZero }

/** Transfers between two accounts. If everything in one moved to the other, these would go from an account to itself. */
fun transfersBetween(a: Long, b: Long, entries: List<Transaction>): List<Transaction> =
    entries.filter { it.kind == TransactionKind.TRANSFER && setOf(it.accountId, it.toAccountId) == setOf(a, b) }

/** An account's balance, from every entry that touches it. It's also what another account gains when they all move there. */
fun balanceFrom(account: Long, entries: List<Transaction>): Money = entries.map { it.effectOn(account) }.sum()
