package com.eminallahverdi.balance.core.backup

import android.content.Context
import com.eminallahverdi.balance.core.common.IoDispatcher
import com.eminallahverdi.balance.core.datastore.BackupProblem
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.AppEnvironment
import com.eminallahverdi.balance.core.model.DriveBackup
import com.eminallahverdi.balance.core.model.backupsToDelete
import com.eminallahverdi.balance.core.model.backupsToOffer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private val FILE_NAME_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(java.time.ZoneOffset.UTC)

/** Backs up to, and restores from, a hidden folder in the user's own Google Drive. There's no server of ours in between. */
@Singleton
class DriveBackups @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archive: BackupArchive,
    private val authorization: DriveAuthorization,
    private val drive: DriveFiles,
    private val preferences: UserPreferencesRepository,
    private val environment: AppEnvironment,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Sends one backup and clears out old ones, and records how it went for the indicators. It never throws. */
    suspend fun backUp(): Boolean = withContext(io) {
        val access = authorization.access()
        if (access !is DriveAccess.Granted) {
            preferences.setBackupProblem(if (access is DriveAccess.NeedsConsent) BackupProblem.SIGN_IN else BackupProblem.FAILED)
            return@withContext false
        }
        val file = File.createTempFile("backup", ".zip", context.cacheDir)
        val sent = runCatching {
            val facts = archive.write(file)
            drive.upload(access.token, file, "balance-${FILE_NAME_TIME.format(Instant.now())}.zip", facts.entries, environment)
            backupsToDelete(environment, drive.list(access.token)).forEach { drive.delete(access.token, it.id) }
        }.isSuccess
        file.delete()
        if (sent) preferences.setBackedUp(Instant.now().toEpochMilli()) else preferences.setBackupProblem(BackupProblem.FAILED)
        sent
    }

    suspend fun list(token: String): List<DriveBackup> = withContext(io) {
        backupsToOffer(environment, drive.list(token)).sortedByDescending { it.createdAt }
    }

    /** Downloads and checks [backup]. It takes over the next time the app starts. */
    suspend fun stageRestore(token: String, backup: DriveBackup) = withContext(io) {
        val file = File.createTempFile("restore", ".zip", context.cacheDir)
        try {
            drive.download(token, backup.id, file)
            archive.stage(file)
        } finally {
            file.delete()
        }
    }
}
