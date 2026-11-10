package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class ExportSpanTest {
    private val today = LocalDate.of(2026, 11, 11)
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun `each span covers the days it says, up to today`() {
        assertThat(ExportSpan.THIS_MONTH.dates(today, null)).isEqualTo(DateRange(d(2026, 11, 1), today))
        assertThat(ExportSpan.LAST_MONTH.dates(today, null)).isEqualTo(DateRange(d(2026, 10, 1), d(2026, 10, 31)))
        assertThat(ExportSpan.THIS_YEAR.dates(today, null)).isEqualTo(DateRange(d(2026, 1, 1), today))
        assertThat(ExportSpan.LAST_YEAR.dates(today, null)).isEqualTo(DateRange(d(2025, 1, 1), d(2025, 12, 31)))
    }

    @Test fun `a span of days counts today as one of them`() {
        assertThat(ExportSpan.LAST_30_DAYS.dates(today, null)).isEqualTo(DateRange(d(2026, 10, 13), today))
        assertThat(ExportSpan.LAST_90_DAYS.dates(today, null)).isEqualTo(DateRange(d(2026, 8, 14), today))
    }

    @Test fun `everything runs from the first entry to the last, and never stops before today`() {
        assertThat(ExportSpan.EVERYTHING.dates(today, DateRange(d(2021, 2, 3), d(2026, 11, 2)))).isEqualTo(DateRange(d(2021, 2, 3), today))
        assertThat(ExportSpan.EVERYTHING.dates(today, DateRange(d(2021, 2, 3), d(2026, 12, 1)))).isEqualTo(DateRange(d(2021, 2, 3), d(2026, 12, 1)))
        assertThat(ExportSpan.EVERYTHING.dates(today, null)).isEqualTo(DateRange(today, today))
    }

    @Test fun `last month from the end of March is February, whatever its length`() {
        assertThat(ExportSpan.LAST_MONTH.dates(d(2028, 3, 31), null)).isEqualTo(DateRange(d(2028, 2, 1), d(2028, 2, 29)))
    }

    @Test fun `the file is named for its dates`() {
        assertThat(exportFileName(DateRange(d(2026, 11, 1), today))).isEqualTo("cibim-2026-11-01-to-2026-11-11.csv")
    }
}
