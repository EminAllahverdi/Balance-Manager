package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class BalanceAdjustmentTest {
    private val today = LocalDate.of(2026, 11, 1)

    @Test fun `brings a negative balance up to the real one`() {
        val t = balanceAdjustment(1, Money(-440), Money(50000), today)!!
        assertThat(t.kind).isEqualTo(TransactionKind.ADJUSTMENT)
        assertThat(Money(-440) + t.effectOn(1)).isEqualTo(Money(50000))
    }

    @Test fun `can bring a balance down, even below zero`() {
        val t = balanceAdjustment(1, Money(1000), Money(-250), today)!!
        assertThat(Money(1000) + t.effectOn(1)).isEqualTo(Money(-250))
    }

    @Test fun `touches only its own account and stays out of reports`() {
        val t = balanceAdjustment(1, Money(0), Money(100), today)!!
        assertThat(t.effectOn(2)).isEqualTo(Money.ZERO)
        assertThat(t.countsInReports).isFalse()
    }

    @Test fun `nothing to do when the balance already matches`() {
        assertThat(balanceAdjustment(1, Money(100), Money(100), today)).isNull()
    }
}
