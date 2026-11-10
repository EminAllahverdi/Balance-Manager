package com.eminallahverdi.balance.core.model

import java.time.LocalDate
import java.time.YearMonth

/** Ready-made spans for an export, each worked out from today, so the sheet can always show the dates it means. */
enum class ExportSpan {
    THIS_MONTH,
    LAST_MONTH,
    LAST_30_DAYS,
    LAST_90_DAYS,
    THIS_YEAR,
    LAST_YEAR,
    EVERYTHING,
    ;

    /** The days it covers. [entries] is from the first entry to the last, so Everything says where the data begins. */
    fun dates(today: LocalDate, entries: DateRange?): DateRange = when (this) {
        THIS_MONTH -> DateRange(today.withDayOfMonth(1), today)
        LAST_MONTH -> DateRange.month(YearMonth.from(today).minusMonths(1))
        LAST_30_DAYS -> DateRange(today.minusDays(29), today)
        LAST_90_DAYS -> DateRange(today.minusDays(89), today)
        THIS_YEAR -> DateRange(today.withDayOfYear(1), today)
        LAST_YEAR -> today.minusYears(1).let { DateRange(it.withDayOfYear(1), it.withDayOfYear(it.lengthOfYear())) }
        EVERYTHING -> DateRange(minOf(entries?.start ?: today, today), maxOf(entries?.endInclusive ?: today, today))
    }
}

/** The file's name carries its dates, so it reads right in a chat or a folder: cibim-2026-11-01-to-2026-11-11.csv. */
fun exportFileName(range: DateRange): String = "cibim-${range.start}-to-${range.endInclusive}.csv"
