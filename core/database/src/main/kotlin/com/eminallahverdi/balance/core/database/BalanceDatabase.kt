package com.eminallahverdi.balance.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.eminallahverdi.balance.core.database.dao.AccountDao
import com.eminallahverdi.balance.core.database.dao.CategoryDao
import com.eminallahverdi.balance.core.database.dao.DebtDao
import com.eminallahverdi.balance.core.database.dao.MaintenanceDao
import com.eminallahverdi.balance.core.database.dao.MerchantDao
import com.eminallahverdi.balance.core.database.dao.PendingEntryDao
import com.eminallahverdi.balance.core.database.dao.PersonDao
import com.eminallahverdi.balance.core.database.dao.ReceiptDao
import com.eminallahverdi.balance.core.database.dao.RecurringPaymentDao
import com.eminallahverdi.balance.core.database.dao.TransactionDao
import com.eminallahverdi.balance.core.database.entity.AccountEntity
import com.eminallahverdi.balance.core.database.entity.CategoryEntity
import com.eminallahverdi.balance.core.database.entity.DebtEntity
import com.eminallahverdi.balance.core.database.entity.MerchantAliasEntity
import com.eminallahverdi.balance.core.database.entity.MerchantEntity
import com.eminallahverdi.balance.core.database.entity.PendingEntryEntity
import com.eminallahverdi.balance.core.database.entity.PersonEntity
import com.eminallahverdi.balance.core.database.entity.ReceiptEntity
import com.eminallahverdi.balance.core.database.entity.ReceiptItemEntity
import com.eminallahverdi.balance.core.database.entity.RecurringPaymentEntity
import com.eminallahverdi.balance.core.database.entity.TransactionEntity

@Database(
    entities = [PendingEntryEntity::class, MerchantAliasEntity::class, PersonEntity::class, CategoryEntity::class, AccountEntity::class, MerchantEntity::class, TransactionEntity::class, RecurringPaymentEntity::class, DebtEntity::class, ReceiptEntity::class, ReceiptItemEntity::class],
    version = 7,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7)],
)
abstract class BalanceDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun merchantDao(): MerchantDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringPaymentDao(): RecurringPaymentDao
    abstract fun debtDao(): DebtDao
    abstract fun pendingEntryDao(): PendingEntryDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun receiptDao(): ReceiptDao

    companion object {
        const val NAME = "balance.db"
    }
}
