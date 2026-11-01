package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class RecurringPaymentTest {
    private val date = LocalDate.of(2026, 11, 10)
    private val account = Account(2, "Card", AccountType.DEBIT_CARD, icon = "credit_card", color = 0xFF8A94A3)

    private fun payment(on: LocalDate = date, frequency: RecurringFrequency = RecurringFrequency.MONTHLY, minor: Long = 12_00) = RecurringPayment(
        id = 1,
        name = "Spotify",
        amount = Money(minor),
        accountId = 2,
        categoryId = 3,
        personId = 4,
        frequency = frequency,
        nextDate = on,
    )

    private fun expense(on: LocalDate, merchant: String? = null, categoryId: Long = 9, minor: Long = 50_00, kind: TransactionKind = TransactionKind.EXPENSE) = TransactionDetails(
        transaction = Transaction(id = 0, kind = kind, amount = Money(minor), date = on, accountId = 2, categoryId = categoryId),
        account = account,
        toAccount = null,
        category = null,
        parentCategory = null,
        person = null,
        merchant = merchant?.let { Merchant(1, it) },
    )

    @Test fun `due on its date and after, not before`() {
        assertThat(payment().isDue(date.minusDays(1))).isFalse()
        assertThat(payment().isDue(date)).isTrue()
        assertThat(payment().isDue(date.plusDays(5))).isTrue()
    }

    @Test fun `weekly and monthly move on by one period`() {
        assertThat(payment(frequency = RecurringFrequency.WEEKLY).advanced().nextDate).isEqualTo(LocalDate.of(2026, 11, 17))
        assertThat(payment().advanced().nextDate).isEqualTo(LocalDate.of(2026, 12, 10))
    }

    @Test fun `the 31st lands on the last day of a short month, then returns`() {
        val february = payment(LocalDate.of(2027, 1, 31)).advanced()
        assertThat(february.nextDate).isEqualTo(LocalDate.of(2027, 2, 28))
        assertThat(february.advanced().nextDate).isEqualTo(LocalDate.of(2027, 3, 31))
    }

    @Test fun `29 February returns in the next leap year`() {
        val next = payment(LocalDate.of(2028, 2, 29), RecurringFrequency.YEARLY).advanced()
        assertThat(next.nextDate).isEqualTo(LocalDate.of(2029, 2, 28))
        assertThat(next.advanced().advanced().advanced().nextDate).isEqualTo(LocalDate.of(2032, 2, 29))
    }

    @Test fun `monthly cost averages other periods over a year`() {
        assertThat(payment(minor = 16_31).monthlyCost).isEqualTo(Money(16_31))
        assertThat(payment(frequency = RecurringFrequency.WEEKLY, minor = 12_00).monthlyCost).isEqualTo(Money(52_00))
        assertThat(payment(frequency = RecurringFrequency.YEARLY, minor = 120_00).monthlyCost).isEqualTo(Money(10_00))
    }

    @Test fun `an entry with the same name counts, whatever its amount or case`() {
        assertThat(payment().isLoggedBy(expense(date, merchant = "SPOTIFY", minor = 15_75))).isTrue()
    }

    @Test fun `an entry with the same category and amount counts without a name`() {
        assertThat(payment().isLoggedBy(expense(date, categoryId = 3, minor = 12_00))).isTrue()
    }

    @Test fun `the same category at another amount is a different expense`() {
        assertThat(payment().isLoggedBy(expense(date, categoryId = 3, minor = 8_00))).isFalse()
    }

    @Test fun `an entry logged a few days early counts, because billing dates move`() {
        assertThat(payment().isLoggedBy(expense(date.minusDays(10), merchant = "Spotify"))).isTrue()
        assertThat(payment().isLoggedBy(expense(date.minusDays(11), merchant = "Spotify"))).isFalse()
    }

    @Test fun `one entry can't settle two months`() {
        val entry = expense(LocalDate.of(2026, 12, 10), merchant = "Spotify")
        assertThat(payment().isLoggedBy(entry)).isFalse()
        assertThat(payment().advanced().isLoggedBy(entry)).isTrue()
    }

    @Test fun `only expenses count`() {
        assertThat(payment().isLoggedBy(expense(date, merchant = "Spotify", kind = TransactionKind.INCOME))).isFalse()
    }

    private fun loan(made: Int, left: Int, last: Money? = null) = payment(minor = 1_000_00).copy(loan = LoanTerms(made, left, last))

    @Test fun `logging a loan payment counts it, skipping doesn't`() {
        val logged = loan(made = 2, left = 3).logged()
        assertThat(logged.loan).isEqualTo(LoanTerms(paymentsMade = 3, paymentsLeft = 2))
        assertThat(logged.nextDate).isEqualTo(LocalDate.of(2026, 12, 10))
        assertThat(loan(made = 2, left = 3).advanced().loan).isEqualTo(LoanTerms(paymentsMade = 2, paymentsLeft = 3))
    }

    @Test fun `a finished loan stops reminding and costs nothing`() {
        val finished = loan(made = 3, left = 1).logged()
        assertThat(finished.isFinished).isTrue()
        assertThat(finished.isDue(date.plusYears(1))).isFalse()
        assertThat(finished.monthlyCost).isEqualTo(Money.ZERO)
        assertThat(finished.stillToPay).isEqualTo(Money.ZERO)
    }

    @Test fun `still to pay counts every payment left`() {
        assertThat(loan(made = 2, left = 3).stillToPay).isEqualTo(Money(3_000_00))
    }

    @Test fun `a different last payment changes the total and only the last one due`() {
        val school = loan(made = 0, left = 5, last = Money(500_00))
        assertThat(school.stillToPay).isEqualTo(Money(4_500_00))
        assertThat(school.amountDue).isEqualTo(Money(1_000_00))
        assertThat(loan(made = 4, left = 1, last = Money(500_00)).amountDue).isEqualTo(Money(500_00))
    }

    @Test fun `progress is how much of the run is paid`() {
        assertThat(LoanTerms(paymentsMade = 1, paymentsLeft = 3).progress).isEqualTo(0.25f)
    }
}
