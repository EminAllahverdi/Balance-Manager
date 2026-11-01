package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Test

class EveningReminderTest {
    private val nine = 21 * 60

    private fun at(hour: Int, minute: Int = 0, day: Int = 1) = LocalDateTime.of(2026, 11, day, hour, minute)

    @Test fun `goes off later today while the time is still ahead`() {
        assertThat(nextReminderAt(at(18), nine)).isEqualTo(at(21))
    }

    @Test fun `moves to tomorrow once the time has passed`() {
        assertThat(nextReminderAt(at(21, 5), nine)).isEqualTo(at(21, day = 2))
    }

    @Test fun `at exactly the time it books the next day, so a run can't book itself again`() {
        assertThat(nextReminderAt(at(21), nine)).isEqualTo(at(21, day = 2))
    }

    @Test fun `any minute of the day works`() {
        assertThat(nextReminderAt(at(18), 19 * 60 + 45)).isEqualTo(at(19, 45))
        assertThat(nextReminderAt(at(18), 0)).isEqualTo(at(0, day = 2))
    }

    @Test fun `a run after the time still counts for that day`() {
        assertThat(isReminderTimeReached(at(21), nine)).isTrue()
        assertThat(isReminderTimeReached(at(23, 59), nine)).isTrue()
    }

    @Test fun `a run the next morning is too late to say anything about the day`() {
        assertThat(isReminderTimeReached(at(7), nine)).isFalse()
    }
}
