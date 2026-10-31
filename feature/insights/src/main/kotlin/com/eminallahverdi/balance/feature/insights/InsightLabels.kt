package com.eminallahverdi.balance.feature.insights

import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.InsightPeriod
import com.eminallahverdi.balance.core.model.Money
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val MONTH_FULL = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)

/** Under a bar: 26 Oct, Oct or 2026. */
internal fun InsightPeriod.barLabel(range: DateRange): String = when (this) {
    InsightPeriod.WEEK -> range.start.format(DAY_MONTH)
    InsightPeriod.MONTH -> range.start.format(MONTH_SHORT)
    InsightPeriod.YEAR -> range.start.year.toString()
}

/** Names a period in a sentence: week of 26 Oct, September or 2025. */
internal fun InsightPeriod.periodName(range: DateRange): String = when (this) {
    InsightPeriod.WEEK -> "week of ${range.start.format(DAY_MONTH)}"
    InsightPeriod.MONTH -> range.start.format(MONTH_FULL)
    InsightPeriod.YEAR -> range.start.year.toString()
}

internal fun InsightPeriod.savedLabel(isCurrent: Boolean): String = when {
    !isCurrent -> "Saved"
    this == InsightPeriod.WEEK -> "Saved this week"
    this == InsightPeriod.MONTH -> "Saved this month"
    else -> "Saved this year"
}

/** +₼412.00 or −₼96.00. */
internal fun signedChange(change: Money): String =
    (if (change.minor > 0) "+" else "") + MoneyFormat.format(change, signed = true)

/** −₼2,110.00 vs September, or Same as September. */
internal fun comparison(change: Money, previous: String): String =
    if (change.isZero) "Same as $previous" else "${signedChange(change)} vs $previous"
