package com.eminallahverdi.balance.importer.moneymanager

import java.time.Instant
import java.time.LocalDate

/** What a backup holds. Its amounts are minor units already, and its stored balances are what the import is checked against. */
data class Backup(
    val accounts: List<BackupAccount>,
    val entries: List<BackupEntry>,
    val transfers: List<BackupTransfer>,
    val defaultAccountUid: String? = null,
)

data class BackupAccount(
    val uid: String,
    val title: String,
    val isRemoved: Boolean = false,
    val ignoreInBalance: Boolean = false,
    val position: Int = 0,
    val balance: Long = 0,
)

data class BackupEntry(
    val uid: String,
    val isIncome: Boolean,
    val amount: Long,
    val date: LocalDate,
    val created: Instant,
    val accountUid: String,
    val category: String?,
    val tags: List<String> = emptyList(),
    val note: String = "",
)

data class BackupTransfer(
    val uid: String,
    val amount: Long,
    val date: LocalDate,
    val created: Instant,
    val fromUid: String,
    val toUid: String,
    val note: String = "",
)
