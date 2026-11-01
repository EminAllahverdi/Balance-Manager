package com.eminallahverdi.balance.core.model

import java.time.LocalDate
import java.time.YearMonth

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(endInclusive)

    companion object {
        val ALL_TIME = DateRange(LocalDate.of(1970, 1, 1), LocalDate.of(2999, 12, 31))

        fun month(month: YearMonth) = DateRange(month.atDay(1), month.atEndOfMonth())
    }
}

data class AccountBalance(val account: Account, val balance: Money)

/** One slice of a split bar: e.g. how much of "Health" was for Wife. */
data class Share<K>(val key: K, val amount: Money)

data class CategorySpending(
    val category: Category,
    val total: Money,
    val byPerson: List<Share<Person>>,
)

data class PersonSpending(
    val person: Person,
    val total: Money,
    val byCategory: List<Share<Category>>,
)

data class MonthSummary(
    val range: DateRange,
    val spent: Money,
    val income: Money,
    val byCategory: List<CategorySpending>,
    val byPerson: List<PersonSpending>,
)

/** Pure aggregation so it can be unit-tested without a database. */
object SpendingCalculator {
    fun summarize(
        range: DateRange,
        items: List<TransactionDetails>,
        people: List<Person>,
    ): MonthSummary {
        val inRange = items.filter { it.transaction.date in range && it.transaction.countsInReports }
        val expenses = inRange.filter { it.transaction.kind == TransactionKind.EXPENSE }
        val income = inRange.filter { it.transaction.kind == TransactionKind.INCOME }
        val fallbackPerson = people.firstOrNull()

        fun topCategory(d: TransactionDetails) = d.parentCategory ?: d.category

        val byCategory = expenses
            .filter { topCategory(it) != null }
            .groupBy { topCategory(it)!! }
            .map { (category, rows) ->
                CategorySpending(
                    category = category,
                    total = rows.map { it.transaction.amount }.sum(),
                    byPerson = rows.groupBy { it.person ?: fallbackPerson }
                        .filterKeys { it != null }
                        .map { (p, r) -> Share(p!!, r.map { it.transaction.amount }.sum()) }
                        .sortedByDescending { it.amount },
                )
            }
            .sortedByDescending { it.total }

        val byPerson = people.map { person ->
            val rows = expenses.filter { (it.person ?: fallbackPerson)?.id == person.id }
            PersonSpending(
                person = person,
                total = rows.map { it.transaction.amount }.sum(),
                byCategory = rows.filter { topCategory(it) != null }
                    .groupBy { topCategory(it)!! }
                    .map { (c, r) -> Share(c, r.map { it.transaction.amount }.sum()) }
                    .sortedByDescending { it.amount },
            )
        }

        return MonthSummary(
            range = range,
            spent = expenses.map { it.transaction.amount }.sum(),
            income = income.map { it.transaction.amount }.sum(),
            byCategory = byCategory,
            byPerson = byPerson,
        )
    }
}
