package com.eminallahverdi.balance.core.model

import java.time.LocalDate

enum class RecurringFrequency { WEEKLY, MONTHLY, YEARLY }

/**
 * What makes a recurring payment a bank loan: a fixed run of payments, the last of which can differ. The rest is what the
 * contract says, when the user typed it in, so the interest can be told apart from what was borrowed.
 */
data class LoanTerms(
    val paymentsMade: Int,
    val paymentsLeft: Int,
    val lastPayment: Money? = null,
    /** What was borrowed. */
    val principal: Money? = null,
    /** The yearly interest rate in hundredths of a percent, so 18.5% is 1850. */
    val annualRateBp: Int? = null,
    /** A one-off fee the bank took for the loan. */
    val commission: Money? = null,
) {
    val progress: Float get() = term.let { total -> if (total == 0) 1f else paymentsMade.toFloat() / total }

    /** Every payment, made and to come. */
    val term: Int get() = paymentsMade + paymentsLeft
}

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
    /** Set when this is a bank loan. */
    val loan: LoanTerms? = null,
) {
    /** A loan with nothing left stops reminding. */
    val isFinished: Boolean get() = loan?.paymentsLeft == 0

    fun isDue(today: LocalDate): Boolean = !isFinished && !nextDate.isAfter(today)

    /** What the next payment comes to: the usual amount, or a loan's different last one. */
    val amountDue: Money get() = loan?.lastPayment?.takeIf { loan.paymentsLeft == 1 } ?: amount

    /** All that's left to pay on a loan. */
    val stillToPay: Money
        get() {
            val left = loan?.paymentsLeft ?: return Money.ZERO
            if (left == 0) return Money.ZERO
            return Money(amount.minor * (left - 1) + (loan.lastPayment ?: amount).minor)
        }

    /** The same payment, one period on. */
    fun advanced(): RecurringPayment = copy(
        nextDate = when (frequency) {
            RecurringFrequency.WEEKLY -> nextDate.plusWeeks(1)
            RecurringFrequency.MONTHLY -> onAnchorDay(nextDate.plusMonths(1))
            RecurringFrequency.YEARLY -> onAnchorDay(nextDate.plusYears(1))
        },
    )

    /** One period on with this one paid, which counts towards a loan. Skipping uses [advanced] instead. */
    fun logged(): RecurringPayment = advanced().copy(loan = loan?.let { it.copy(paymentsMade = it.paymentsMade + 1, paymentsLeft = (it.paymentsLeft - 1).coerceAtLeast(0)) })

    private fun onAnchorDay(date: LocalDate) = date.withDayOfMonth(minOf(anchorDay, date.lengthOfMonth()))

    /** Roughly what this costs in an average month. */
    val monthlyCost: Money
        get() = when {
            isFinished -> Money.ZERO
            frequency == RecurringFrequency.WEEKLY -> Money(amount.minor * WEEKS_PER_YEAR / MONTHS_PER_YEAR)
            frequency == RecurringFrequency.MONTHLY -> amount
            else -> Money(amount.minor / MONTHS_PER_YEAR)
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
        return expense.merchant?.name.equals(name, ignoreCase = true) || (entry.categoryId == categoryId && entry.amount == amountDue)
    }

    private companion object {
        const val WEEKS_PER_YEAR = 52
        const val MONTHS_PER_YEAR = 12
        const val EARLY_DAYS = 10L
        const val WEEKLY_EARLY_DAYS = 2L
    }
}
