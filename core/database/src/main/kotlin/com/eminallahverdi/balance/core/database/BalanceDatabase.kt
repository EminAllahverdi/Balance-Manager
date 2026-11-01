package com.eminallahverdi.balance.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.eminallahverdi.balance.core.database.dao.AccountDao
import com.eminallahverdi.balance.core.database.dao.CategoryDao
import com.eminallahverdi.balance.core.database.dao.MaintenanceDao
import com.eminallahverdi.balance.core.database.dao.MerchantDao
import com.eminallahverdi.balance.core.database.dao.PersonDao
import com.eminallahverdi.balance.core.database.dao.RecurringPaymentDao
import com.eminallahverdi.balance.core.database.dao.TransactionDao
import com.eminallahverdi.balance.core.database.entity.AccountEntity
import com.eminallahverdi.balance.core.database.entity.CategoryEntity
import com.eminallahverdi.balance.core.database.entity.MerchantEntity
import com.eminallahverdi.balance.core.database.entity.PersonEntity
import com.eminallahverdi.balance.core.database.entity.RecurringPaymentEntity
import com.eminallahverdi.balance.core.database.entity.TransactionEntity

@Database(
    entities = [PersonEntity::class, CategoryEntity::class, AccountEntity::class, MerchantEntity::class, TransactionEntity::class, RecurringPaymentEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class BalanceDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun merchantDao(): MerchantDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringPaymentDao(): RecurringPaymentDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        const val NAME = "balance.db"
    }
}
