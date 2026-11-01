package com.eminallahverdi.balance.core.model

import java.time.Duration
import java.time.Instant

/** A nightly run can land hours late under Doze, so this allows a day and a bit before saying anything. */
private val OVERDUE_AFTER: Duration = Duration.ofHours(30)
private const val BACKUPS_KEPT = 7

/** One backup in the user's Drive. */
data class DriveBackup(val id: String, val createdAt: Instant, val entries: Int?)

/** A missed night shows from the morning after, which is when the user can do something about it. */
fun isBackupOverdue(now: Instant, lastBackup: Instant?): Boolean =
    lastBackup == null || Duration.between(lastBackup, now) >= OVERDUE_AFTER

/** The ones past the newest week, so one bad night can't take away the only good copy. */
fun backupsToDelete(backups: List<DriveBackup>, keep: Int = BACKUPS_KEPT): List<DriveBackup> =
    backups.sortedByDescending { it.createdAt }.drop(keep)
