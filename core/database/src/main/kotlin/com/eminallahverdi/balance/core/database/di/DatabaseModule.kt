package com.eminallahverdi.balance.core.database.di

import android.content.Context
import androidx.room.Room
import com.eminallahverdi.balance.core.database.BalanceDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): BalanceDatabase =
        Room.databaseBuilder(context, BalanceDatabase::class.java, BalanceDatabase.NAME).build()

    @Provides fun personDao(db: BalanceDatabase) = db.personDao()
    @Provides fun categoryDao(db: BalanceDatabase) = db.categoryDao()
    @Provides fun accountDao(db: BalanceDatabase) = db.accountDao()
    @Provides fun merchantDao(db: BalanceDatabase) = db.merchantDao()
    @Provides fun transactionDao(db: BalanceDatabase) = db.transactionDao()
    @Provides fun recurringPaymentDao(db: BalanceDatabase) = db.recurringPaymentDao()
    @Provides fun debtDao(db: BalanceDatabase) = db.debtDao()
    @Provides fun pendingEntryDao(db: BalanceDatabase) = db.pendingEntryDao()
}
