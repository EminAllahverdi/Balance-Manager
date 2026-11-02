package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class AccountRemovalTest {
    private val old = 1L
    private val card = 2L
    private val cash = 3L

    private fun entry(kind: TransactionKind, minor: Long, from: Long = old, to: Long? = null) =
        Transaction(id = 0, kind = kind, amount = Money(minor), date = LocalDate.of(2026, 11, 2), accountId = from, toAccountId = to)

    @Test fun `only transfers change another account when one is deleted`() {
        val entries = listOf(entry(TransactionKind.EXPENSE, 50_00), entry(TransactionKind.INCOME, 20_00), entry(TransactionKind.ADJUSTMENT, -5_00))
        assertThat(balanceChangesFromDeleting(old, entries)).isEmpty()
    }

    @Test fun `money that came from a deleted account leaves the one that got it`() {
        val sent = entry(TransactionKind.TRANSFER, 100_00, from = old, to = card)
        assertThat(balanceChangesFromDeleting(old, listOf(sent))).containsExactly(card, Money(-100_00))
    }

    @Test fun `money sent to a deleted account comes back to the one that sent it`() {
        val received = entry(TransactionKind.TRANSFER, 30_00, from = cash, to = old)
        assertThat(balanceChangesFromDeleting(old, listOf(received))).containsExactly(cash, Money(30_00))
    }

    @Test fun `transfers with one account add up, and an account that nets to nothing is left out`() {
        val entries = listOf(
            entry(TransactionKind.TRANSFER, 100_00, from = old, to = card),
            entry(TransactionKind.TRANSFER, 40_00, from = card, to = old),
            entry(TransactionKind.TRANSFER, 10_00, from = old, to = cash),
            entry(TransactionKind.TRANSFER, 10_00, from = cash, to = old),
        )
        assertThat(balanceChangesFromDeleting(old, entries)).containsExactly(card, Money(-60_00))
    }

    @Test fun `transfers between two accounts are found in either direction, and no others`() {
        val out = entry(TransactionKind.TRANSFER, 100_00, from = old, to = card)
        val back = entry(TransactionKind.TRANSFER, 40_00, from = card, to = old)
        val other = entry(TransactionKind.TRANSFER, 10_00, from = old, to = cash)
        val expense = entry(TransactionKind.EXPENSE, 5_00)
        assertThat(transfersBetween(old, card, listOf(out, back, other, expense))).containsExactly(out, back)
    }

    @Test fun `the balance counts every kind of entry, with transfers on the right side`() {
        val entries = listOf(
            entry(TransactionKind.INCOME, 100_00),
            entry(TransactionKind.EXPENSE, 30_00),
            entry(TransactionKind.TRANSFER, 20_00, from = old, to = card),
            entry(TransactionKind.TRANSFER, 5_00, from = cash, to = old),
            entry(TransactionKind.ADJUSTMENT, -1_00),
            entry(TransactionKind.DEBT, 2_00),
            entry(TransactionKind.INTERNAL, 3_00),
        )
        assertThat(balanceFrom(old, entries)).isEqualTo(Money(59_00))
    }
}
