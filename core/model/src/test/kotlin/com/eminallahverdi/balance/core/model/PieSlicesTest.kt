package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PieSlicesTest {
    private fun share(id: Long, minor: Long) = Share(Category(id, "Category $id", CategoryType.EXPENSE, icon = "payments", color = 0), Money(minor))

    @Test fun `each share gets its percent, biggest first`() {
        val slices = pieSlices(listOf(share(1, 25_00), share(2, 75_00)))
        assertThat(slices.map { it.category?.id }).containsExactly(2L, 1L).inOrder()
        assertThat(slices.map { it.percent }).containsExactly(75L, 25L).inOrder()
    }

    @Test fun `percents round to the nearest`() {
        val slices = pieSlices(listOf(share(1, 75_00), share(2, 25_00), share(3, 12_00)))
        assertThat(slices.map { it.percent }).containsExactly(67L, 22L, 11L).inOrder()
    }

    @Test fun `past the limit the smallest go together`() {
        val slices = pieSlices((1L..8L).map { share(it, it * 100) }, limit = 5)
        assertThat(slices).hasSize(6)
        assertThat(slices.last().category).isNull()
        assertThat(slices.last().amount).isEqualTo(Money(600))
    }

    @Test fun `a lone leftover keeps its name`() {
        val slices = pieSlices((1L..6L).map { share(it, it * 100) }, limit = 5)
        assertThat(slices).hasSize(6)
        assertThat(slices.last().category?.id).isEqualTo(1L)
    }

    @Test fun `nothing spent gives no slices`() {
        assertThat(pieSlices(emptyList())).isEmpty()
        assertThat(pieSlices(listOf(share(1, 0)))).isEmpty()
    }

    @Test fun `a month's income is grouped by its top category`() {
        val pay = Category(1, "Paycheck", CategoryType.INCOME, icon = "payments", color = 0)
        val bonus = Category(2, "Bonus", CategoryType.INCOME, parentId = 1, icon = "payments", color = 0)
        fun entry(id: Long, minor: Long, category: Category, parent: Category?) = TransactionDetails(
            transaction = Transaction(id, TransactionKind.INCOME, Money(minor), java.time.LocalDate.of(2026, 11, 1), accountId = 1),
            category = category,
            parentCategory = parent,
            person = null,
            account = Account(1, "Card", AccountType.DEBIT_CARD, "payments", 0),
            toAccount = null,
            merchant = null,
        )
        val range = DateRange.month(java.time.YearMonth.of(2026, 11))
        val summary = SpendingCalculator.summarize(range, listOf(entry(1, 100_00, pay, null), entry(2, 50_00, bonus, pay)), emptyList())
        assertThat(summary.incomeByCategory).containsExactly(Share(pay, Money(150_00)))
    }
}
