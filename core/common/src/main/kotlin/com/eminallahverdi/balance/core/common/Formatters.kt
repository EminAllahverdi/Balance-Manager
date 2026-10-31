package com.eminallahverdi.balance.core.common

import com.eminallahverdi.balance.core.model.Money
import java.time.LocalDate
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

object DateLabels {
    private val dayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    /** "Today", "Yesterday", otherwise "Mon 26 Oct". */
    fun relative(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayMonth)
    }

    /** "Today · Sat 31 Oct", "Yesterday · Fri 30 Oct", otherwise "Thu 29 Oct". */
    fun dayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val label = relative(date, today)
        val full = date.format(dayMonth)
        return if (label == full) full else "$label · $full"
    }

    fun weekdayShort(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    fun monthName(date: LocalDate): String = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    fun daysAgo(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val d = today.toEpochDay() - date.toEpochDay()
        return when {
            d == 0L -> "today"
            d == 1L -> "yesterday"
            d > 1 -> "$d days ago"
            else -> "in ${-d} days"
        }
    }
}
