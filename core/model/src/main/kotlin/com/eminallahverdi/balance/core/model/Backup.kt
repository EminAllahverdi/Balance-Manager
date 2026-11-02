package com.eminallahverdi.balance.core.model

import java.time.Duration
import java.time.Instant

/** A nightly run can land hours late under Doze, so this allows a day and a bit before saying anything. */
private val OVERDUE_AFTER: Duration = Duration.ofHours(30)
private const val BACKUPS_KEPT = 7

/** Which app this is. Dev is for trying changes before they reach the real app, Prod. Both are in one Google Cloud project, so they share one Drive folder. */
enum class AppEnvironment { DEV, PROD }

/** One backup in the user's Drive, and the app that made it. Backups from before Dev existed count as Prod's: they're the real data. */
data class DriveBackup(val id: String, val createdAt: Instant, val entries: Int?, val environment: AppEnvironment = AppEnvironment.PROD)

/** Prod never offers Dev's test data. Dev offers real backups too, to try an update on a copy of them. */
fun backupsToOffer(environment: AppEnvironment, backups: List<DriveBackup>): List<DriveBackup> =
    if (environment == AppEnvironment.DEV) backups else backups.filter { it.environment == environment }

/** A missed night shows from the morning after, which is when the user can do something about it. */
fun isBackupOverdue(now: Instant, lastBackup: Instant?): Boolean =
    lastBackup == null || Duration.between(lastBackup, now) >= OVERDUE_AFTER

/**
 * The ones past the newest week, so one bad night can't take away the only good copy. An app only ever counts and
 * deletes its own, so a week of testing can't push out the real backups.
 */
fun backupsToDelete(environment: AppEnvironment, backups: List<DriveBackup>, keep: Int = BACKUPS_KEPT): List<DriveBackup> =
    backups.filter { it.environment == environment }.sortedByDescending { it.createdAt }.drop(keep)
