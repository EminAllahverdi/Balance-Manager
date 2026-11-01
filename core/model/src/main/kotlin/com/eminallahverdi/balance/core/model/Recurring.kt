package com.eminallahverdi.balance.core.model

import java.time.LocalDate

enum class RecurringFrequency { WEEKLY, MONTHLY, YEARLY }

/**
 * A reminder for a payment that repeats. It never adds anything by itself: the user logs the expense,
 * and the reminder only shows while that entry is missing. [nextDate] is the next one still to be logged or skipped.
 */
data class RecurringPayment(
    val id: Long,
    val name: String,
    val amount: Money,
    val accountId: Long,
    /** A category or sub-category, like any expense. */
    val categoryId: Long,
    val personId: Long?,
    val frequency: RecurringFrequency,
    val nextDate: LocalDate,
    /** The day of the month it was set for, so a payment on the 31st returns to the 31st after a short month. */
    val anchorDay: Int = nextDate.dayOfMonth,
) {
    fun isDue(today: LocalDate): Boolean = !nextDate.isAfter(today)

    /** The same payment, one period on. */
    fun advanced(): RecurringPayment = copy(
        nextDate = when (frequency) {
            RecurringFrequency.WEEKLY -> nextDate.plusWeeks(1)
            RecurringFrequency.MONTHLY -> onAnchorDay(nextDate.plusMonths(1))
            RecurringFrequency.YEARLY -> onAnchorDay(nextDate.plusYears(1))
        },
    )

    private fun onAnchorDay(date: LocalDate) = date.withDayOfMonth(minOf(anchorDay, date.lengthOfMonth()))

    /** Roughly what this costs in an average month. */
    val monthlyCost: Money
        get() = when (frequency) {
            RecurringFrequency.WEEKLY -> Money(amount.minor * WEEKS_PER_YEAR / MONTHS_PER_YEAR)
            RecurringFrequency.MONTHLY -> amount
            RecurringFrequency.YEARLY -> Money(amount.minor / MONTHS_PER_YEAR)
        }

    /** The earliest an entry can count as this occurrence. Billing dates move, so it starts before the date itself. */
    val firstLoggedDay: LocalDate
        get() = nextDate.minusDays(if (frequency == RecurringFrequency.WEEKLY) WEEKLY_EARLY_DAYS else EARLY_DAYS)

    /**
     * Whether [expense] is this occurrence, logged by hand: the same name, or the same category and amount.
     * Each occurrence has its own days, up to where the next one's start, so one entry can't settle two.
     */
    fun isLoggedBy(expense: TransactionDetails): Boolean {
        val entry = expense.transaction
        if (entry.kind != TransactionKind.EXPENSE) return false
        if (entry.date.isBefore(firstLoggedDay) || !entry.date.isBefore(advanced().firstLoggedDay)) return false
        return expense.merchant?.name.equals(name, ignoreCase = true) || (entry.categoryId == categoryId && entry.amount == amount)
    }

    private companion object {
        const val WEEKS_PER_YEAR = 52
        const val MONTHS_PER_YEAR = 12
        const val EARLY_DAYS = 10L
        const val WEEKLY_EARLY_DAYS = 2L
    }
}
