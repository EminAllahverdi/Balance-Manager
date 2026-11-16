package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

class PlannedPaymentTest {
    private val planned = LocalDate.of(2026, 11, 15)
    private val card = Account(1, "Card", AccountType.DEBIT_CARD, icon = "credit_card", color = 0)

    private fun rent(id: Long = 1, on: LocalDate = LocalDate.of(2026, 11, 21), paidOn: LocalDate? = null) =
        PlannedPayment(id = id, name = "Rent", amount = Money(1_200_00), date = on, accountId = 1, categoryId = 5, plannedOn = planned, paidOn = paidOn)

    private fun entry(id: Long, on: LocalDate, minor: Long = 1_200_00, merchant: String? = null, categoryId: Long? = 5, kind: TransactionKind = TransactionKind.EXPENSE) = TransactionDetails(
        transaction = Transaction(id, kind, Money(minor), on, accountId = 1, categoryId = categoryId),
        account = card,
        toAccount = null,
        category = null,
        parentCategory = null,
        person = null,
        merchant = merchant?.let { Merchant(1, it) },
    )

    @Test fun `an entry with its name pays it, whatever the amount or case`() {
        assertThat(rent().isPaidBy(entry(1, planned.plusDays(2), minor = 1_150_00, merchant = "RENT", categoryId = null))).isTrue()
    }

    @Test fun `the same category and amount pays it`() {
        assertThat(rent().isPaidBy(entry(1, planned.plusDays(2)))).isTrue()
        assertThat(rent().isPaidBy(entry(1, planned.plusDays(2), minor = 1_000_00))).isFalse()
    }

    @Test fun `an entry from before it was planned, or one that isn't spending, doesn't`() {
        assertThat(rent().isPaidBy(entry(1, planned.minusDays(1)))).isFalse()
        assertThat(rent().isPaidBy(entry(1, planned, kind = TransactionKind.INCOME))).isFalse()
    }

    @Test fun `one entry pays only one of two alike, the soonest`() {
        val early = rent(id = 1)
        val late = rent(id = 2, on = LocalDate.of(2026, 12, 21))
        val statuses = plannedStatuses(listOf(late, early), listOf(entry(9, planned.plusDays(5))))
        assertThat(statuses.map { it.payment.id to it.isPaid }).containsExactly(1L to true, 2L to false).inOrder()
    }

    @Test fun `what's left to pay this month leaves out what's paid and what's later`() {
        val ticked = rent(id = 1, paidOn = planned)
        val waiting = rent(id = 2, on = LocalDate.of(2026, 11, 30))
        val overdue = rent(id = 3, on = LocalDate.of(2026, 10, 30))
        val later = rent(id = 4, on = LocalDate.of(2026, 12, 1))
        val left = plannedStatuses(listOf(ticked, waiting, overdue, later), emptyList()).toPayBy(YearMonth.of(2026, 11))
        assertThat(left.map { it.payment.id }).containsExactly(3L, 2L).inOrder()
        assertThat(plannedStatuses(listOf(ticked), emptyList()).single().isTicked).isTrue()
    }
}
