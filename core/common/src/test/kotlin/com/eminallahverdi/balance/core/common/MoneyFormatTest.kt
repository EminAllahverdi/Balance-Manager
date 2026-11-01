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

    @Test fun shortLabels() {
        assertThat(MoneyFormat.short(Money(780))).isEqualTo("7.80")
        assertThat(MoneyFormat.short(Money(34050))).isEqualTo("340.50")
        assertThat(MoneyFormat.short(Money(99999))).isEqualTo("999.99")
        assertThat(MoneyFormat.short(Money(100000))).isEqualTo("1.0k")
        assertThat(MoneyFormat.short(Money(343512))).isEqualTo("3.4k")
        assertThat(MoneyFormat.short(Money(1470000))).isEqualTo("14.7k")
        assertThat(MoneyFormat.short(Money(9996000))).isEqualTo("100k")
        assertThat(MoneyFormat.short(Money(12050000))).isEqualTo("121k")
    }

    @Test fun relativeDates() {
        val today = LocalDate.of(2026, 10, 31)
        assertThat(DateLabels.relative(today, today)).isEqualTo("Today")
        assertThat(DateLabels.relative(today.minusDays(5), today)).isEqualTo("Mon 26 Oct")
        assertThat(DateLabels.daysAgo(today.minusDays(5), today)).isEqualTo("5 days ago")
        assertThat(DateLabels.dayHeader(today, today)).isEqualTo("Today · Sat 31 Oct")
        assertThat(DateLabels.dayHeader(today.minusDays(5), today)).isEqualTo("Mon 26 Oct")
    }

    @Test fun `times of day are 24-hour, with two digits`() {
        assertThat(DateLabels.timeOfDay(21 * 60)).isEqualTo("21:00")
        assertThat(DateLabels.timeOfDay(7 * 60 + 5)).isEqualTo("07:05")
    }
}
