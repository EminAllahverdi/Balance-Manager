package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class SpentSpanTest {
    @Test fun `today is the day alone`() {
        val day = LocalDate.of(2026, 11, 17)
        assertThat(SpentSpan.TODAY.range(day)).isEqualTo(DateRange(day, day))
    }

    @Test fun `a week runs Monday to Sunday and can start in the last month`() {
        assertThat(SpentSpan.WEEK.range(LocalDate.of(2026, 11, 17))).isEqualTo(DateRange(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22)))
        assertThat(SpentSpan.WEEK.range(LocalDate.of(2026, 11, 16))).isEqualTo(DateRange(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22)))
        assertThat(SpentSpan.WEEK.range(LocalDate.of(2026, 11, 1))).isEqualTo(DateRange(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)))
    }

    @Test fun `the month is the whole month`() {
        assertThat(SpentSpan.MONTH.range(LocalDate.of(2026, 2, 10))).isEqualTo(DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
    }
}
