package com.eminallahverdi.balance.feature.insights

import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.common.UiText
import com.eminallahverdi.balance.core.common.rawText
import com.eminallahverdi.balance.core.common.uiText
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.InsightPeriod
import com.eminallahverdi.balance.core.model.Money

/** Under a bar: 26 Oct, Oct or 2026. */
internal fun InsightPeriod.barLabel(range: DateRange): String = when (this) {
    InsightPeriod.WEEK -> DateLabels.dayMonth(range.start)
    InsightPeriod.MONTH -> DateLabels.monthShort(range.start.month)
    InsightPeriod.YEAR -> range.start.year.toString()
}

/** Names a period in a sentence: week of 26 Oct, September or 2025. */
internal fun InsightPeriod.periodName(range: DateRange): UiText = when (this) {
    InsightPeriod.WEEK -> uiText(R.string.insights_week_of, DateLabels.dayMonth(range.start))
    InsightPeriod.MONTH -> rawText(DateLabels.monthName(range.start))
    InsightPeriod.YEAR -> rawText(range.start.year.toString())
}

internal fun InsightPeriod.savedLabel(isCurrent: Boolean): Int = when {
    !isCurrent -> R.string.insights_saved
    this == InsightPeriod.WEEK -> R.string.insights_saved_this_week
    this == InsightPeriod.MONTH -> R.string.insights_saved_this_month
    else -> R.string.insights_saved_this_year
}

/** +₼412.00 or −₼96.00. */
internal fun signedChange(change: Money): String =
    (if (change.minor > 0) "+" else "") + MoneyFormat.format(change, signed = true)

/** −₼2,110.00 vs September, or Same as September. */
internal fun comparison(change: Money, previous: UiText): UiText =
    if (change.isZero) uiText(R.string.insights_same_as, previous) else uiText(R.string.insights_versus, signedChange(change), previous)
