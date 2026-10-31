package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {
    @Test fun `parses dot and comma decimals`() {
        assertThat(Money.parse("16.31")).isEqualTo(Money(1631))
        assertThat(Money.parse("16,3")).isEqualTo(Money(1630))
        assertThat(Money.parse("1 184")).isEqualTo(Money(118400))
        assertThat(Money.parse(".5")).isEqualTo(Money(50))
    }

    @Test fun `rejects garbage and too many decimals`() {
        assertThat(Money.parse("abc")).isNull()
        assertThat(Money.parse("1.234")).isNull()
        assertThat(Money.parse("")).isNull()
    }

    @Test fun `transfer moves money between two accounts only`() {
        val t = Transaction(1, TransactionKind.TRANSFER, Money(1000), java.time.LocalDate.of(2026, 10, 31), accountId = 1, toAccountId = 2)
        assertThat(t.effectOn(1)).isEqualTo(Money(-1000))
        assertThat(t.effectOn(2)).isEqualTo(Money(1000))
        assertThat(t.effectOn(3)).isEqualTo(Money.ZERO)
        assertThat(t.countsInReports).isFalse()
    }
}
