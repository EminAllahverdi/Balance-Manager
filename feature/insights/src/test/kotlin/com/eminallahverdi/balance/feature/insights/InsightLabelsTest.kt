package com.eminallahverdi.balance.feature.insights

import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.InsightPeriod
import com.eminallahverdi.balance.core.model.Money
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class InsightLabelsTest {
    private val october = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
    private val week = DateRange(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1))
    private val year = DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))

    @Test fun `bars are labelled to fit`() {
        assertThat(InsightPeriod.WEEK.barLabel(week)).isEqualTo("26 Oct")
        assertThat(InsightPeriod.MONTH.barLabel(october)).isEqualTo("Oct")
        assertThat(InsightPeriod.YEAR.barLabel(year)).isEqualTo("2025")
    }

    @Test fun `periods read naturally in a sentence`() {
        assertThat(InsightPeriod.WEEK.periodName(week)).isEqualTo("week of 26 Oct")
        assertThat(InsightPeriod.MONTH.periodName(october)).isEqualTo("October")
        assertThat(InsightPeriod.YEAR.periodName(year)).isEqualTo("2025")
    }

    @Test fun `saved only says this period when it is the current one`() {
        assertThat(InsightPeriod.MONTH.savedLabel(isCurrent = true)).isEqualTo("Saved this month")
        assertThat(InsightPeriod.YEAR.savedLabel(isCurrent = true)).isEqualTo("Saved this year")
        assertThat(InsightPeriod.MONTH.savedLabel(isCurrent = false)).isEqualTo("Saved")
    }

    @Test fun `changes carry their sign`() {
        assertThat(signedChange(Money(41200))).isEqualTo("+₼412.00")
        assertThat(signedChange(Money(-9600))).isEqualTo("−₼96.00")
    }

    @Test fun `comparison handles no change`() {
        assertThat(comparison(Money(-211000), "September")).isEqualTo("−₼2,110.00 vs September")
        assertThat(comparison(Money.ZERO, "September")).isEqualTo("Same as September")
    }
}
