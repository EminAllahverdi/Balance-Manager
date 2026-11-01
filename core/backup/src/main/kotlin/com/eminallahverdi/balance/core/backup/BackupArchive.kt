package com.eminallahverdi.balance.core.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.eminallahverdi.balance.core.database.BalanceDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val DATABASE_ENTRY = "database"
private const val PREFERENCES_ENTRY = "preferences"
private const val AVATARS = "avatars"
private const val PREFERENCES_FILE = "datastore/user_prefs.preferences_pb"
private const val STAGE = "restore"
private const val READY = "ready"

/** What a finished archive holds, for the file's description in Drive. */
data class ArchiveFacts(val entries: Int, val schemaVersion: Int)

/** Thrown for a backup made by a newer version of the app, which this one can't open. */
class BackupTooNewException : IOException("Backup is from a newer app")

/** A backup is a zip of the database, the settings and people's photos. */
@Singleton
class BackupArchive @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: BalanceDatabase,
) {
    /** Writes [target] from a checked copy of the database, so a damaged one is never uploaded over a good backup. */
    suspend fun write(target: File): ArchiveFacts {
        val work = File(context.cacheDir, "backup").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val copy = File(work, BalanceDatabase.NAME)
            copyDatabase(copy)
            val facts = openChecked(copy) { ArchiveFacts(entries = count("SELECT COUNT(*) FROM txn"), schemaVersion = version) }
            ZipOutputStream(target.outputStream().buffered()).use { zip ->
                zip.add(DATABASE_ENTRY, copy)
                File(context.filesDir, PREFERENCES_FILE).takeIf { it.exists() }?.let { zip.add(PREFERENCES_ENTRY, it) }
                File(context.filesDir, AVATARS).listFiles().orEmpty().forEach { zip.add("$AVATARS/${it.name}", it) }
            }
            return facts
        } finally {
            work.deleteRecursively()
        }
    }

    /** Unpacks [archive], checks it, and leaves it for [applyPendingRestore]. Nothing live changes until the app restarts. */
    fun stage(archive: File) {
        val stage = File(context.filesDir, STAGE).apply {
            deleteRecursively()
            mkdirs()
        }
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
                val file = File(stage, entry.name)
                if (!file.canonicalPath.startsWith(stage.canonicalPath + File.separator)) throw IOException("Bad entry in the backup")
                file.parentFile?.mkdirs()
                file.outputStream().use { zip.copyTo(it) }
            }
        }
        val current = db.openHelper.readableDatabase.version
        openChecked(File(stage, DATABASE_ENTRY)) {
            if (version > current) throw BackupTooNewException()
            pointAvatarsAtThisApp()
        }
        File(stage, READY).createNewFile()
    }

    /**
     * A plain file copy, under a write transaction so nothing changes partway. The checkpoint first keeps the log small,
     * and the log is copied too in case something was written between the two.
     */
    private suspend fun copyDatabase(target: File) {
        val live = context.getDatabasePath(BalanceDatabase.NAME)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        db.withTransaction {
            live.copyTo(target, overwrite = true)
            File(live.path + "-wal").takeIf { it.exists() }?.copyTo(File(target.path + "-wal"), overwrite = true)
        }
    }

    /** Photos are stored by full path, which differs between the debug and release apps. */
    private fun SQLiteDatabase.pointAvatarsAtThisApp() {
        val photos = buildList {
            rawQuery("SELECT id, avatar_uri FROM person WHERE avatar_uri IS NOT NULL", null).use { row ->
                while (row.moveToNext()) add(row.getLong(0) to row.getString(1))
            }
        }
        photos.forEach { (id, uri) ->
            val name = Uri.parse(uri).lastPathSegment ?: return@forEach
            execSQL("UPDATE person SET avatar_uri = ? WHERE id = ?", arrayOf(Uri.fromFile(File(File(context.filesDir, AVATARS), name)).toString(), id))
        }
    }
}

/** Opening it folds in any copied log, which leaves one self-contained file. */
private inline fun <T> openChecked(file: File, use: SQLiteDatabase.() -> T): T =
    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { database ->
        database.rawQuery("PRAGMA quick_check(1)", null).use { row ->
            if (!row.moveToFirst() || row.getString(0) != "ok") throw IOException("Damaged database")
        }
        database.use()
    }

private fun SQLiteDatabase.count(sql: String): Int = rawQuery(sql, null).use { row ->
    row.moveToFirst()
    row.getInt(0)
}

private fun ZipOutputStream.add(name: String, file: File) {
    putNextEntry(ZipEntry(name))
    file.inputStream().use { it.copyTo(this) }
    closeEntry()
}

/**
 * Swaps in a stage left by [BackupArchive.stage]. It has to run before anything opens the database or the settings, so
 * the application calls it ahead of Hilt. The marker goes last: if the phone dies partway, the next start finishes the job.
 */
fun applyPendingRestore(context: Context) {
    val stage = File(context.filesDir, STAGE)
    val ready = File(stage, READY)
    if (!ready.exists()) return
    val live = context.getDatabasePath(BalanceDatabase.NAME)
    SQLiteDatabase.deleteDatabase(live)
    live.parentFile?.mkdirs()
    File(stage, DATABASE_ENTRY).copyTo(live, overwrite = true)
    val preferences = File(context.filesDir, PREFERENCES_FILE)
    File(stage, PREFERENCES_ENTRY).takeIf { it.exists() }?.let {
        preferences.parentFile?.mkdirs()
        it.copyTo(preferences, overwrite = true)
    }
    val avatars = File(context.filesDir, AVATARS)
    File(stage, AVATARS).takeIf { it.exists() }?.let {
        avatars.deleteRecursively()
        it.copyRecursively(avatars, overwrite = true)
    }
    ready.delete()
    stage.deleteRecursively()
}
