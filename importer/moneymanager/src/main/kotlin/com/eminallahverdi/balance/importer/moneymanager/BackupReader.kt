package com.eminallahverdi.balance.importer.moneymanager

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipInputStream

private const val DATABASE_ENTRY = "MyFinance.db"
private const val MAX_PREFIX = 1024
private val ZIP_START = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/** Reads a backup through a temporary copy of its database, which never outlives this call. */
internal fun readBackup(context: Context, stream: InputStream): Backup {
    val copy = File.createTempFile("moneymanager", ".db", context.cacheDir)
    try {
        copy.outputStream().use { unpackDatabase(stream, it) }
        return SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use(::readTables)
    } finally {
        SQLiteDatabase.deleteDatabase(copy)
    }
}

/** A backup is a zip with a few bytes in front of it, which ZipInputStream won't skip by itself. */
internal fun unpackDatabase(stream: InputStream, target: OutputStream) {
    val input = BufferedInputStream(stream, MAX_PREFIX * 2)
    input.mark(MAX_PREFIX)
    val head = ByteArray(MAX_PREFIX)
    var read = 0
    while (read < head.size) {
        val count = input.read(head, read, head.size - read)
        if (count < 0) break
        read += count
    }
    val start = (0..read - ZIP_START.size).firstOrNull { at -> ZIP_START.indices.all { head[at + it] == ZIP_START[it] } }
        ?: throw IOException("Not a backup")
    input.reset()
    var skipped = 0L
    while (skipped < start) skipped += input.skip(start - skipped)
    val zip = ZipInputStream(input)
    generateSequence { zip.nextEntry }.firstOrNull { it.name == DATABASE_ENTRY } ?: throw IOException("No database in the backup")
    zip.copyTo(target)
}

private fun readTables(db: SQLiteDatabase): Backup {
    val links = HashMap<String, MutableList<Pair<String, String>>>()
    db.forEachRow("SELECT entityUid, otherType, otherUid FROM sync_link WHERE isRemoved = 0") {
        links.getOrPut(getString(0)) { mutableListOf() } += getString(1) to getString(2)
    }
    fun link(uid: String, type: String) = links[uid]?.firstOrNull { it.first == type }?.second
    val categories = db.lookup("SELECT uid, title FROM category")
    val tags = db.lookup("SELECT uid, name FROM tag")

    val accounts = mutableListOf<BackupAccount>()
    db.forEachRow("SELECT a.uid, a.title, a.isRemoved, a.ignoreInBalance, a.position, b.value FROM account a LEFT JOIN account_balance b ON b.uid = a.uid") {
        accounts += BackupAccount(getString(0), getString(1), getInt(2) != 0, getInt(3) != 0, getInt(4), getLong(5))
    }
    val entries = mutableListOf<BackupEntry>()
    db.forEachRow("SELECT uid, type, amountInAccountCurrency, date, created, comment FROM `transaction` WHERE isRemoved = 0") {
        val uid = getString(0)
        val account = link(uid, "Account") ?: return@forEachRow
        entries += BackupEntry(
            uid = uid,
            isIncome = getString(1) == "Income",
            amount = getLong(2),
            date = LocalDate.parse(getString(3)),
            created = Instant.parse(getString(4)),
            accountUid = account,
            category = link(uid, "Category")?.let(categories::get),
            tags = links[uid].orEmpty().filter { it.first == "Tag" }.mapNotNull { tags[it.second] },
            note = getString(5).orEmpty(),
        )
    }
    val transfers = mutableListOf<BackupTransfer>()
    db.forEachRow("SELECT uid, fromAmount, date, created, comment FROM transfer WHERE isRemoved = 0") {
        val uid = getString(0)
        val from = link(uid, "FromAccount") ?: return@forEachRow
        val to = link(uid, "ToAccount") ?: return@forEachRow
        transfers += BackupTransfer(uid, getLong(1), LocalDate.parse(getString(2)), Instant.parse(getString(3)), from, to, getString(4).orEmpty())
    }
    var defaultAccount: String? = null
    db.forEachRow("SELECT value FROM user WHERE key = 'defaultAccountUid'") { defaultAccount = getString(0) }
    return Backup(accounts, entries, transfers, defaultAccount)
}

private fun SQLiteDatabase.lookup(sql: String): Map<String, String> {
    val result = HashMap<String, String>()
    forEachRow(sql) { result[getString(0)] = getString(1) }
    return result
}

private inline fun SQLiteDatabase.forEachRow(sql: String, read: Cursor.() -> Unit) {
    rawQuery(sql, null).use { cursor ->
        while (cursor.moveToNext()) cursor.read()
    }
}
