package com.eminallahverdi.balance.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eminallahverdi.balance.core.database.BalanceDatabase
import com.eminallahverdi.balance.core.database.entity.AccountEntity
import com.eminallahverdi.balance.core.database.entity.PersonEntity
import com.eminallahverdi.balance.core.database.entity.TransactionEntity
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The whole way round on a real database, because this is what stands between the user and losing everything. */
@RunWith(AndroidJUnit4::class)
class BackupArchiveTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zip = File(context.cacheDir, "test-backup.zip")
    private val photo = File(context.filesDir, "avatars/photo")
    private val settings = File(context.filesDir, "datastore/user_prefs.preferences_pb")
    private lateinit var db: BalanceDatabase

    private fun open() = Room.databaseBuilder(context, BalanceDatabase::class.java, BalanceDatabase.NAME).build()

    private fun wipe() {
        context.deleteDatabase(BalanceDatabase.NAME)
        File(context.filesDir, "avatars").deleteRecursively()
        File(context.filesDir, "datastore").deleteRecursively()
        File(context.filesDir, "restore").deleteRecursively()
        zip.delete()
    }

    @Before fun setUp() {
        wipe()
        db = open()
    }

    @After fun tearDown() {
        db.close()
        wipe()
    }

    private suspend fun addEntry(externalId: String) {
        val account = db.accountDao().getAll().firstOrNull()?.id ?: db.accountDao().upsert(AccountEntity(name = "Card", type = "DEBIT_CARD", icon = "payments", color = 0))
        db.transactionDao().insertAll(listOf(TransactionEntity(kind = "EXPENSE", amountMinor = 100, date = 20_000, accountId = account, externalId = externalId, createdAt = 0, updatedAt = 0)))
    }

    private fun restart() {
        db.close()
        applyPendingRestore(context)
        db = open()
    }

    @Test fun restoringBringsBackWhatWasBackedUpAndDropsWhatCameAfter() = runBlocking {
        db.personDao().upsert(PersonEntity(name = "Sam", color = 1))
        addEntry("kept")
        photo.parentFile?.mkdirs()
        photo.writeText("the photo")
        settings.parentFile?.mkdirs()
        settings.writeText("old settings")

        val facts = BackupArchive(context, db).write(zip)
        assertThat(facts.entries).isEqualTo(1)

        addEntry("after the backup")
        db.personDao().upsert(PersonEntity(name = "Alex", color = 2))
        photo.delete()
        settings.writeText("new settings")

        BackupArchive(context, db).stage(zip)
        assertThat(db.transactionDao().count()).isEqualTo(2)

        restart()
        assertThat(db.transactionDao().externalIds()).containsExactly("kept")
        assertThat(db.personDao().getAll().map { it.name }).containsExactly("Sam")
        assertThat(photo.readText()).isEqualTo("the photo")
        assertThat(settings.readText()).isEqualTo("old settings")
        assertThat(File(context.filesDir, "restore").exists()).isFalse()
    }

    @Test fun writesMadeJustBeforeTheBackupAreInIt() = runBlocking {
        repeat(50) { addEntry("entry $it") }
        val facts = BackupArchive(context, db).write(zip)
        assertThat(facts.entries).isEqualTo(50)
        BackupArchive(context, db).stage(zip)
        restart()
        assertThat(db.transactionDao().count()).isEqualTo(50)
    }

    @Test fun photosPointAtThisAppAfterARestore() = runBlocking {
        db.personDao().upsert(PersonEntity(name = "Sam", color = 1, avatarUri = "file:///data/user/0/some.other.app/files/avatars/photo"))
        BackupArchive(context, db).write(zip)
        BackupArchive(context, db).stage(zip)
        restart()
        assertThat(db.personDao().getAll().single().avatarUri).isEqualTo("file://" + File(context.filesDir, "avatars/photo").path)
    }

    @Test fun aBackupFromANewerAppIsRefusedAndNothingIsStaged() = runBlocking {
        val current = db.openHelper.readableDatabase.version
        db.openHelper.writableDatabase.execSQL("PRAGMA user_version = ${current + 1}")
        BackupArchive(context, db).write(zip)
        db.openHelper.writableDatabase.execSQL("PRAGMA user_version = $current")
        var refused = false
        try {
            BackupArchive(context, db).stage(zip)
        } catch (_: BackupTooNewException) {
            refused = true
        }
        assertThat(refused).isTrue()
        assertThat(File(context.filesDir, "restore/ready").exists()).isFalse()
    }
}
