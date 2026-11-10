package com.eminallahverdi.balance.core.common

import com.eminallahverdi.balance.core.model.Money
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.absoluteValue

object MoneyFormat {
    const val SYMBOL = "₼"

    /** ₼1,184.00, always two decimals · −₼16.31 (signed = true and negative). */
    fun format(money: Money, signed: Boolean = false): String {
        val abs = money.minor.absoluteValue
        val major = abs / 100
        val minor = abs % 100
        val grouped = major.toString().reversed().chunked(3).joinToString(",").reversed()
        val sign = if (signed && money.minor < 0) "−" else ""
        return "$sign$SYMBOL$grouped.${minor.toString().padStart(2, '0')}"
    }

    /** A label on a chart, where the exact figure is a tap away: 7.80 · 340.50 · 3.4k · 120k. */
    fun short(money: Money): String {
        val abs = money.minor.absoluteValue
        if (abs < 100_000) return format(Money(abs)).removePrefix(SYMBOL)
        val tenths = (abs + 5_000) / 10_000
        return if (tenths < 1_000) "${tenths / 10}.${tenths % 10}k" else "${(abs + 50_000) / 100_000}k"
    }

    const val HIDDEN = "$SYMBOL ••••"
}

/**
 * Days as the app says them, in the language picked ([Language]): "Today", "Yesterday", then "Mon 26 Oct", or in
 * Azerbaijani "Bu gün", "Dünən", then "26 okt, B.e.". Month names start with a capital, as a title does in both.
 */
object DateLabels {
    private val hourMinute = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val az: Boolean get() = Language.isAzerbaijani

    private fun weekdayDayMonth(): DateTimeFormatter =
        if (az) DateTimeFormatter.ofPattern("d MMM, EEE", Language.locale) else DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    /** "Today", "Yesterday", otherwise "Mon 26 Oct". */
    fun relative(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> if (az) "Bu gün" else "Today"
        today.minusDays(1) -> if (az) "Dünən" else "Yesterday"
        else -> date.format(weekdayDayMonth())
    }

    /** "Today · Sat 31 Oct", "Yesterday · Fri 30 Oct", otherwise "Thu 29 Oct". */
    fun dayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val label = relative(date, today)
        val full = date.format(weekdayDayMonth())
        return if (label == full) full else "$label · $full"
    }

    fun weekdayShort(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Language.locale).capitalised()

    fun monthName(date: LocalDate): String = date.month.getDisplayName(TextStyle.FULL_STANDALONE, Language.locale).capitalised()

    /** "November", "November 2025" when it isn't this year: a month as a title, or as a filter says it. */
    fun monthTitle(month: java.time.YearMonth, withYear: Boolean = month.year != java.time.YearMonth.now().year): String =
        monthName(month.atDay(1)) + if (withYear) " ${month.year}" else ""

    /** "3 Nov", or "3 noy" in Azerbaijani. */
    fun dayMonth(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", Language.locale))

    /** "3 Nov 2026". */
    fun dayMonthYear(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Language.locale))

    /** "Nov", or "Noy": a month in a grid or on a chart. */
    fun monthShort(month: java.time.Month): String = month.getDisplayName(TextStyle.SHORT_STANDALONE, Language.locale).capitalised()

    /** "21:00" for minutes after midnight. */
    fun timeOfDay(minuteOfDay: Int): String = LocalTime.MIDNIGHT.plusMinutes(minuteOfDay.toLong()).format(hourMinute)

    fun daysAgo(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val d = today.toEpochDay() - date.toEpochDay()
        return when {
            d == 0L -> if (az) "bu gün" else "today"
            d == 1L -> if (az) "dünən" else "yesterday"
            d > 1 -> if (az) "$d gün əvvəl" else "$d days ago"
            else -> if (az) "${-d} gün sonra" else "in ${-d} days"
        }
    }

    private fun String.capitalised(): String = replaceFirstChar { it.titlecase(Language.locale) }
}
