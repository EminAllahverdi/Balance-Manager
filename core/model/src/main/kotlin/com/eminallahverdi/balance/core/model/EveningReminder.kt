package com.eminallahverdi.balance.core.model

import java.time.LocalDateTime
import java.time.LocalTime

/** The next time a reminder set for [minuteOfDay] goes off: later today, or tomorrow once that's passed. */
fun nextReminderAt(now: LocalDateTime, minuteOfDay: Int): LocalDateTime {
    val today = now.toLocalDate().atStartOfDay().plusMinutes(minuteOfDay.toLong())
    return if (today.isAfter(now)) today else today.plusDays(1)
}

/**
 * Whether a reminder set for [minuteOfDay] still belongs to the day of [now]. A phone that slept through it can run the
 * job the next morning, when "nothing added today" would be true of a day that's barely started.
 */
fun isReminderTimeReached(now: LocalDateTime, minuteOfDay: Int): Boolean =
    now.toLocalTime() >= LocalTime.MIDNIGHT.plusMinutes(minuteOfDay.toLong())
