package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class LoanMathTest {
    private fun loan(monthly: Long, made: Int, left: Int, rateBp: Int? = 1200, principal: Long? = 10_000_00, last: Long? = null, commission: Long? = null) = RecurringPayment(
        id = 1,
        name = "Car loan",
        amount = Money(monthly),
        accountId = 1,
        categoryId = 1,
        personId = null,
        frequency = RecurringFrequency.MONTHLY,
        nextDate = LocalDate.of(2026, 12, 1),
        loan = LoanTerms(made, left, last?.let(::Money), principal?.let(::Money), rateBp, commission?.let(::Money)),
    )

    @Test fun `the monthly payment is the bank's annuity`() {
        // 10,000 at 12% a year over a year: 1% a month.
        assertThat(annuityPayment(Money(10_000_00), 1200, 12)).isEqualTo(Money(888_49))
        assertThat(annuityPayment(Money(10_000_00), 0, 12)).isEqualTo(Money(833_33))
        assertThat(annuityPayment(Money(10_000_00), 1200, 0)).isNull()
    }

    @Test fun `what's left splits into what was borrowed and interest`() {
        val fresh = loan(888_49, made = 0, left = 12)
        assertThat(fresh.principalLeft).isEqualTo(Money(10_000_02))
        assertThat(fresh.interestLeft).isEqualTo(Money(661_86))
        assertThat(fresh.stillToPay).isEqualTo(Money(10_661_88))
    }

    @Test fun `less of each payment is interest as the loan runs`() {
        val halfway = loan(888_49, made = 6, left = 6)
        assertThat(halfway.interestLeft!!.minor).isLessThan(661_86 / 2)
        assertThat(loan(888_49, made = 12, left = 0).principalLeft).isEqualTo(Money.ZERO)
    }

    @Test fun `without a rate nothing is split, and without interest it's all borrowed money`() {
        assertThat(loan(888_49, made = 0, left = 12, rateBp = null).interestLeft).isNull()
        assertThat(loan(500_00, made = 0, left = 4, rateBp = 0).interestLeft).isEqualTo(Money.ZERO)
    }

    @Test fun `a different last payment counts as itself`() {
        val withLast = loan(888_49, made = 11, left = 1, last = 888_00)
        assertThat(withLast.principalLeft).isEqualTo(Money(879_21))
    }

    @Test fun `the whole loan costs its interest and commission`() {
        assertThat(loan(888_49, made = 3, left = 9, commission = 100_00).totalCost).isEqualTo(Money(761_88))
        assertThat(loan(888_49, made = 3, left = 9, principal = null).totalCost).isNull()
    }

    @Test fun `rates are typed as a percent, with either separator`() {
        assertThat(parseRate("18.5")).isEqualTo(1850)
        assertThat(parseRate("18,5 %")).isEqualTo(1850)
        assertThat(parseRate("0")).isEqualTo(0)
        assertThat(parseRate("150")).isNull()
        assertThat(parseRate("-1")).isNull()
        assertThat(parseRate("abc")).isNull()
        assertThat(formatRate(1850)).isEqualTo("18.5")
        assertThat(formatRate(1200)).isEqualTo("12")
    }

    @Test fun `a monthly plan never leaves a bigger last payment`() {
        assertThat(monthlyFor(Money(5_500_00), 5)).isEqualTo(Money(1_100_00))
        assertThat(monthlyFor(Money(1_000_00), 3)).isEqualTo(Money(333_34))
        assertThat(monthlyFor(Money(1_000_00), 0)).isNull()
    }
}
