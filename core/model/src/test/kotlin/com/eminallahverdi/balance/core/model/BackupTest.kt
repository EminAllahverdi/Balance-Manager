package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Test

class BackupTest {
    private val now = Instant.parse("2026-11-03T09:00:00Z")

    @Test fun `a backup from last night isn't overdue, even a late one`() {
        assertThat(isBackupOverdue(now, Instant.parse("2026-11-03T03:00:00Z"))).isFalse()
        assertThat(isBackupOverdue(now.plus(Duration.ofHours(23)), Instant.parse("2026-11-03T06:30:00Z"))).isFalse()
    }

    @Test fun `a missed night shows by the next morning`() {
        assertThat(isBackupOverdue(now, Instant.parse("2026-11-02T03:00:00Z"))).isTrue()
    }

    @Test fun `no backup yet is overdue`() {
        assertThat(isBackupOverdue(now, null)).isTrue()
    }

    @Test fun `the newest are kept, whatever order they came in`() {
        val backups = (1..9).map { DriveBackup("b$it", now.minus(Duration.ofDays(it.toLong())), entries = null) }.shuffled()
        assertThat(backupsToDelete(backups).map { it.id }).containsExactly("b8", "b9")
    }

    @Test fun `nothing goes while there are few`() {
        assertThat(backupsToDelete(listOf(DriveBackup("b1", now, null)))).isEmpty()
    }
}
