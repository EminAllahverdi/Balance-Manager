package com.eminallahverdi.balance.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** What Home's Spent and Income cover on the month that's running. A past month always shows the whole month. */
enum class SpentSpan {
    TODAY,
    /** Monday to Sunday, so early in a month it reaches back into the last one. */
    WEEK,
    MONTH,
    ;

    fun range(today: LocalDate): DateRange = when (this) {
        TODAY -> DateRange(today, today)
        WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { monday -> DateRange(monday, monday.plusDays(6)) }
        MONTH -> DateRange.month(YearMonth.from(today))
    }
}
