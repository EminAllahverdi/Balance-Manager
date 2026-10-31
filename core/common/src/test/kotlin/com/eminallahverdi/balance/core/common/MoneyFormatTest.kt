package com.eminallahverdi.balance.core.common

import com.eminallahverdi.balance.core.model.Money
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class MoneyFormatTest {
    @Test fun formats() {
        assertThat(MoneyFormat.format(Money(118400))).isEqualTo("₼1,184.00")
        assertThat(MoneyFormat.format(Money(440))).isEqualTo("₼4.40")
        assertThat(MoneyFormat.format(Money(499))).isEqualTo("₼4.99")
        assertThat(MoneyFormat.format(Money(-1631), signed = true)).isEqualTo("−₼16.31")
        assertThat(MoneyFormat.format(Money(5)))
            .isEqualTo("₼0.05")
    }

    @Test fun relativeDates() {
        val today = LocalDate.of(2026, 10, 31)
        assertThat(DateLabels.relative(today, today)).isEqualTo("Today")
        assertThat(DateLabels.relative(today.minusDays(5), today)).isEqualTo("Mon 26 Oct")
        assertThat(DateLabels.daysAgo(today.minusDays(5), today)).isEqualTo("5 days ago")
    }
}
