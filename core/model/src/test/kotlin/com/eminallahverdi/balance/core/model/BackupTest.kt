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
        assertThat(backupsToDelete(AppEnvironment.PROD, backups).map { it.id }).containsExactly("b8", "b9")
    }

    @Test fun `nothing goes while there are few`() {
        assertThat(backupsToDelete(AppEnvironment.PROD, listOf(DriveBackup("b1", now, null)))).isEmpty()
    }

    private fun backup(id: String, daysAgo: Long, environment: AppEnvironment) =
        DriveBackup(id, now.minus(Duration.ofDays(daysAgo)), entries = null, environment = environment)

    @Test fun `a week of testing can't push out a real backup`() {
        val real = (1L..7L).map { backup("real$it", it + 20, AppEnvironment.PROD) }
        val test = (1L..20L).map { backup("test$it", it, AppEnvironment.DEV) }
        assertThat(backupsToDelete(AppEnvironment.PROD, real + test)).isEmpty()
    }

    @Test fun `dev only ever deletes dev's backups`() {
        val real = (1L..9L).map { backup("real$it", it, AppEnvironment.PROD) }
        val test = (1L..9L).map { backup("test$it", it, AppEnvironment.DEV) }
        assertThat(backupsToDelete(AppEnvironment.DEV, real + test).map { it.id }).containsExactly("test8", "test9")
    }

    @Test fun `prod never offers test data`() {
        val backups = listOf(backup("real", 1, AppEnvironment.PROD), backup("test", 1, AppEnvironment.DEV))
        assertThat(backupsToOffer(AppEnvironment.PROD, backups).map { it.id }).containsExactly("real")
    }

    @Test fun `dev can restore a real backup, to try an update on a copy of it`() {
        val backups = listOf(backup("real", 1, AppEnvironment.PROD), backup("test", 1, AppEnvironment.DEV))
        assertThat(backupsToOffer(AppEnvironment.DEV, backups).map { it.id }).containsExactly("real", "test")
    }
}
