package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class InsightsCalculatorTest {
    private val today = LocalDate.of(2026, 11, 4) // A Wednesday.
    private val card = Account(1, "Card", AccountType.DEBIT_CARD, "payments", 0)
    private val me = Person(1, "Me", 0xFF0000)
    private val family = Person(2, "Family", 0x00FF00)
    private val gone = Person(3, "Gone", 0x0000FF, isArchived = true)
    private val people = listOf(me, family, gone)
    private val car = Category(1, "Car", CategoryType.EXPENSE, icon = "work", color = 0)
    private val health = Category(2, "Health", CategoryType.EXPENSE, icon = "work", color = 0)
    private val food = Category(3, "Food", CategoryType.EXPENSE, icon = "work", color = 0)
    private val months = InsightsCalculator.periods(InsightPeriod.MONTH, today)

    private fun row(date: LocalDate, minor: Long, category: Category? = car, person: Person? = me, kind: TransactionKind = TransactionKind.EXPENSE) = TransactionDetails(
        transaction = Transaction(0, kind, Money(minor), date, accountId = card.id),
        account = card,
        toAccount = null,
        category = category,
        parentCategory = null,
        person = person,
        merchant = null,
    )

    private fun overview(vararg rows: TransactionDetails, selected: Int? = null) =
        InsightsCalculator.overview(months, rows.toList(), people, selected, today)

    @Test fun `weeks start on Monday and end on the current one`() {
        val weeks = InsightsCalculator.periods(InsightPeriod.WEEK, today)
        assertThat(weeks).hasSize(InsightsCalculator.VISIBLE_PERIODS + 1)
        assertThat(weeks.last()).isEqualTo(DateRange(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8)))
        assertThat(weeks[weeks.size - 2].start).isEqualTo(LocalDate.of(2026, 10, 26))
    }

    @Test fun `a Monday belongs to its own week`() {
        val monday = LocalDate.of(2026, 11, 2)
        assertThat(InsightsCalculator.periods(InsightPeriod.WEEK, monday).last().start).isEqualTo(monday)
    }

    @Test fun `months cross into the year before`() {
        val january = InsightsCalculator.periods(InsightPeriod.MONTH, LocalDate.of(2026, 1, 15))
        assertThat(january.last()).isEqualTo(DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
        assertThat(january[january.size - 2].start).isEqualTo(LocalDate.of(2025, 12, 1))
    }

    @Test fun `years are whole calendar years`() {
        val years = InsightsCalculator.periods(InsightPeriod.YEAR, today)
        assertThat(years.last()).isEqualTo(DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
        assertThat(years.first().start.year).isEqualTo(2020)
    }

    @Test fun `shows the current period unless a bar is chosen`() {
        val rows = arrayOf(row(LocalDate.of(2026, 11, 1), 500), row(LocalDate.of(2026, 10, 5), 900))
        assertThat(overview(*rows).spent).isEqualTo(Money(500))
        assertThat(overview(*rows).isCurrent).isTrue()
        val october = overview(*rows, selected = InsightsCalculator.VISIBLE_PERIODS - 2)
        assertThat(october.spent).isEqualTo(Money(900))
        assertThat(october.isCurrent).isFalse()
        assertThat(october.previousRange.start).isEqualTo(LocalDate.of(2026, 9, 1))
    }

    @Test fun `the first bar compares with the hidden period before it`() {
        val first = overview(row(LocalDate.of(2026, 5, 2), 100), row(LocalDate.of(2026, 6, 2), 700), selected = 0)
        assertThat(first.selected.range.start).isEqualTo(LocalDate.of(2026, 6, 1))
        assertThat(first.change).isEqualTo(Money(600))
    }

    @Test fun `change is against the period before`() {
        val spentLess = overview(row(LocalDate.of(2026, 11, 1), 300), row(LocalDate.of(2026, 10, 1), 1000))
        assertThat(spentLess.change).isEqualTo(Money(-700))
    }

    @Test fun `daily average counts only days that have happened`() {
        assertThat(overview(row(LocalDate.of(2026, 11, 1), 1000)).days).isEqualTo(4)
        assertThat(overview(row(LocalDate.of(2026, 11, 1), 1000)).dailyAverage).isEqualTo(Money(250))
        val october = overview(row(LocalDate.of(2026, 10, 1), 3100), selected = InsightsCalculator.VISIBLE_PERIODS - 2)
        assertThat(october.days).isEqualTo(31)
        assertThat(october.dailyAverage).isEqualTo(Money(100))
    }

    @Test fun `daily average rounds to the nearest qepik`() {
        assertThat(overview(row(LocalDate.of(2026, 11, 1), 1001)).dailyAverage).isEqualTo(Money(250))
        assertThat(overview(row(LocalDate.of(2026, 11, 1), 1002)).dailyAverage).isEqualTo(Money(251))
    }

    @Test fun `saved is income minus spending and can be negative`() {
        val o = overview(row(LocalDate.of(2026, 11, 1), 1000), row(LocalDate.of(2026, 11, 2), 300, kind = TransactionKind.INCOME))
        assertThat(o.income).isEqualTo(Money(300))
        assertThat(o.saved).isEqualTo(Money(-700))
    }

    @Test fun `transfers and adjustments stay out`() {
        val o = overview(
            row(LocalDate.of(2026, 11, 1), 1000, category = null, kind = TransactionKind.TRANSFER),
            row(LocalDate.of(2026, 11, 1), 5000, category = null, kind = TransactionKind.ADJUSTMENT),
        )
        assertThat(o.spent).isEqualTo(Money.ZERO)
        assertThat(o.income).isEqualTo(Money.ZERO)
        assertThat(o.changes).isEmpty()
    }

    @Test fun `bars split by person and keep archived people`() {
        val bar = overview(row(LocalDate.of(2026, 11, 1), 300), row(LocalDate.of(2026, 11, 2), 200, person = gone)).selected
        assertThat(bar.byPerson.map { it.key }).containsExactly(me, gone).inOrder()
        assertThat(bar.byPerson.sumOf { it.amount.minor }).isEqualTo(bar.spent.minor)
    }

    @Test fun `legend lists only people in the visible bars`() {
        val o = overview(row(LocalDate.of(2026, 10, 1), 300, person = family))
        assertThat(o.people).containsExactly(family)
    }

    @Test fun `biggest move comes first, up or down`() {
        val o = overview(
            row(LocalDate.of(2026, 11, 1), 100, category = car),
            row(LocalDate.of(2026, 10, 1), 900, category = car),
            row(LocalDate.of(2026, 11, 1), 300, category = health),
            row(LocalDate.of(2026, 10, 1), 200, category = health),
        )
        assertThat(o.changes.map { it.category }).containsExactly(car, health).inOrder()
        assertThat(o.changes.first().change).isEqualTo(Money(-800))
        assertThat(o.changes.first().amount).isEqualTo(Money(100))
    }

    @Test fun `a category that stopped is still a change, and one that held steady isn't`() {
        val o = overview(
            row(LocalDate.of(2026, 10, 1), 400, category = car),
            row(LocalDate.of(2026, 11, 1), 200, category = food),
            row(LocalDate.of(2026, 10, 1), 200, category = food),
        )
        val change = o.changes.single()
        assertThat(change.category).isEqualTo(car)
        assertThat(change.amount).isEqualTo(Money.ZERO)
        assertThat(change.change).isEqualTo(Money(-400))
    }

    @Test fun `no data at all is still a full chart`() {
        val o = overview()
        assertThat(o.bars).hasSize(InsightsCalculator.VISIBLE_PERIODS)
        assertThat(o.spent).isEqualTo(Money.ZERO)
        assertThat(o.people).isEmpty()
        assertThat(o.changes).isEmpty()
    }
}
