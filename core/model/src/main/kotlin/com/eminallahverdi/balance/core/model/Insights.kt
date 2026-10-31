package com.eminallahverdi.balance.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class InsightPeriod { WEEK, MONTH, YEAR }

/** One column of the chart: what a period cost, split by who it was for. */
data class InsightBar(val range: DateRange, val spent: Money, val byPerson: List<Share<Person>>)

/** How a category moved against the period before. [change] is positive when it cost more. */
data class CategoryChange(val category: Category, val amount: Money, val change: Money)

data class InsightOverview(
    val bars: List<InsightBar>,
    val selectedIndex: Int,
    /** The period the selected one is compared with. */
    val previousRange: DateRange,
    val change: Money,
    val income: Money,
    val dailyAverage: Money,
    /** Days the average covers: only those that have happened, for the current period. */
    val days: Int,
    /** Whoever has spending in the visible bars, for the legend. */
    val people: List<Person>,
    /** Biggest move first, either way. */
    val changes: List<CategoryChange>,
) {
    val selected: InsightBar get() = bars[selectedIndex]
    val spent: Money get() = selected.spent
    val saved: Money get() = income - spent
    val isCurrent: Boolean get() = selectedIndex == bars.lastIndex
}

/** Pure, like [SpendingCalculator], so it can be unit-tested without a database. */
object InsightsCalculator {
    /** Bars on the chart. */
    const val VISIBLE_PERIODS = 6

    /** The visible periods oldest first, after one more so the first bar has something to compare with. */
    fun periods(period: InsightPeriod, today: LocalDate): List<DateRange> =
        (VISIBLE_PERIODS downTo 0).map { back ->
            when (period) {
                InsightPeriod.WEEK -> {
                    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(back.toLong())
                    DateRange(monday, monday.plusDays(6))
                }
                InsightPeriod.MONTH -> DateRange.month(YearMonth.from(today).minusMonths(back.toLong()))
                InsightPeriod.YEAR -> {
                    val year = today.year - back
                    DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                }
            }
        }

    /**
     * [people] should include archived people, so what was spent for them stays in the bars.
     * [selected] is an index into the visible bars; null means the current period.
     */
    fun overview(periods: List<DateRange>, items: List<TransactionDetails>, people: List<Person>, selected: Int?, today: LocalDate): InsightOverview {
        val summaries = periods.map { SpendingCalculator.summarize(it, items, people) }
        val visible = summaries.drop(1)
        val index = selected?.coerceIn(visible.indices) ?: visible.lastIndex
        val current = visible[index]
        val previous = summaries[index]
        val bars = visible.map { summary ->
            InsightBar(summary.range, summary.spent, summary.byPerson.filter { it.total.minor > 0 }.map { Share(it.person, it.total) })
        }
        val days = daysCounted(current.range, today)
        return InsightOverview(
            bars = bars,
            selectedIndex = index,
            previousRange = previous.range,
            change = current.spent - previous.spent,
            income = current.income,
            dailyAverage = Money((current.spent.minor + days / 2) / days),
            days = days,
            people = people.filter { person -> bars.any { bar -> bar.byPerson.any { it.key.id == person.id } } },
            changes = categoryChanges(current, previous),
        )
    }

    private fun daysCounted(range: DateRange, today: LocalDate): Int {
        val last = minOf(range.endInclusive, today)
        return (last.toEpochDay() - range.start.toEpochDay() + 1).coerceAtLeast(1).toInt()
    }

    private fun categoryChanges(current: MonthSummary, previous: MonthSummary): List<CategoryChange> {
        val now = current.byCategory.associate { it.category.id to it.total }
        val before = previous.byCategory.associate { it.category.id to it.total }
        return (current.byCategory + previous.byCategory)
            .map { it.category }
            .distinctBy { it.id }
            .map { category ->
                val amount = now[category.id] ?: Money.ZERO
                CategoryChange(category, amount, amount - (before[category.id] ?: Money.ZERO))
            }
            .filterNot { it.change.isZero }
            .sortedByDescending { it.change.abs() }
    }
}
