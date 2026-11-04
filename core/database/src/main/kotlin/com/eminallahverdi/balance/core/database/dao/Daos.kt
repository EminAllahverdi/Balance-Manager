package com.eminallahverdi.balance.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.eminallahverdi.balance.core.database.entity.AccountEntity
import com.eminallahverdi.balance.core.database.entity.CategoryEntity
import com.eminallahverdi.balance.core.database.entity.DebtEntity
import com.eminallahverdi.balance.core.database.entity.MerchantAliasEntity
import com.eminallahverdi.balance.core.database.entity.MerchantEntity
import com.eminallahverdi.balance.core.database.entity.PendingEntryEntity
import com.eminallahverdi.balance.core.database.entity.PersonEntity
import com.eminallahverdi.balance.core.database.entity.ReceiptEntity
import com.eminallahverdi.balance.core.database.entity.ReceiptItemEntity
import com.eminallahverdi.balance.core.database.entity.ReceiptWithItems
import com.eminallahverdi.balance.core.database.entity.RecurringPaymentEntity
import com.eminallahverdi.balance.core.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM person ORDER BY is_archived, sort_order, id")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person ORDER BY sort_order, id")
    suspend fun getAll(): List<PersonEntity>

    @Upsert suspend fun upsert(person: PersonEntity): Long
    @Update suspend fun updateAll(people: List<PersonEntity>)
    @Query("DELETE FROM person WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT (SELECT COUNT(*) FROM txn WHERE person_id = :id) + (SELECT COUNT(*) FROM recurring_payment WHERE person_id = :id)")
    suspend fun usageCount(id: Long): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY is_archived, sort_order, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY sort_order, id")
    suspend fun getAll(): List<CategoryEntity>

    @Upsert suspend fun upsert(category: CategoryEntity): Long
    @Update suspend fun updateAll(categories: List<CategoryEntity>)
    @Query("DELETE FROM category WHERE id = :id") suspend fun delete(id: Long)
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM txn WHERE category_id = :id OR category_id IN (SELECT id FROM category WHERE parent_id = :id))
            + (SELECT COUNT(*) FROM recurring_payment WHERE category_id = :id OR category_id IN (SELECT id FROM category WHERE parent_id = :id))
            + (SELECT COUNT(*) FROM debt WHERE category_id = :id OR category_id IN (SELECT id FROM category WHERE parent_id = :id))
        """,
    )
    suspend fun usageCount(id: Long): Int
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM account ORDER BY is_archived, sort_order, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account ORDER BY sort_order, id")
    suspend fun getAll(): List<AccountEntity>

    @Upsert suspend fun upsert(account: AccountEntity): Long
    @Update suspend fun updateAll(accounts: List<AccountEntity>)
    @Query("DELETE FROM account WHERE id = :id") suspend fun delete(id: Long)
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM txn WHERE account_id = :id OR to_account_id = :id)
            + (SELECT COUNT(*) FROM recurring_payment WHERE account_id = :id)
        """,
    )
    suspend fun usageCount(id: Long): Int
}

@Dao
interface MerchantDao {
    @Query("SELECT * FROM merchant ORDER BY use_count DESC, name")
    fun observeAll(): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM merchant WHERE id = :id") suspend fun get(id: Long): MerchantEntity?

    @Query("SELECT * FROM merchant WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): MerchantEntity?

    @Query("SELECT * FROM merchant WHERE last_category_id = :categoryId ORDER BY use_count DESC LIMIT :limit")
    suspend fun recentForCategory(categoryId: Long, limit: Int): List<MerchantEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(merchant: MerchantEntity): Long
    @Update suspend fun update(merchant: MerchantEntity)

    @Query("UPDATE merchant SET last_account_id = :to WHERE last_account_id = :from")
    suspend fun replaceLastAccount(from: Long, to: Long?)
}

/** Per-account sum of every movement, in minor units. */
data class AccountSum(val accountId: Long, val total: Long)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM txn WHERE date BETWEEN :fromEpochDay AND :toEpochDay ORDER BY date DESC, created_at DESC")
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM txn ORDER BY date DESC, created_at DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM txn WHERE kind = 'TRANSFER' ORDER BY date DESC, created_at DESC LIMIT :limit")
    fun observeTransfers(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM txn WHERE account_id = :accountId OR to_account_id = :accountId ORDER BY date DESC, created_at DESC")
    fun observeForAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM txn WHERE id = :id") suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT * FROM txn WHERE category_id = :categoryId AND kind = 'EXPENSE' ORDER BY date DESC, created_at DESC LIMIT 1")
    suspend fun lastForCategory(categoryId: Long): TransactionEntity?

    /**
     * Balance contribution per account. Outgoing side of transfers and expenses are negative;
     * ADJUSTMENT, INTERNAL and DEBT rows already carry their sign.
     */
    @Query(
        """
        SELECT account_id AS accountId, SUM(CASE kind
            WHEN 'EXPENSE' THEN -amount_minor
            WHEN 'TRANSFER' THEN -amount_minor
            ELSE amount_minor END) AS total
        FROM txn GROUP BY account_id
        UNION ALL
        SELECT to_account_id AS accountId, SUM(amount_minor) AS total
        FROM txn WHERE kind = 'TRANSFER' AND to_account_id IS NOT NULL GROUP BY to_account_id
        """,
    )
    fun observeAccountSums(): Flow<List<AccountSum>>

    @Upsert suspend fun upsert(txn: TransactionEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(txns: List<TransactionEntity>): List<Long>
    @Query("DELETE FROM txn WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT COUNT(*) FROM txn") suspend fun count(): Int
    @Query("SELECT external_id FROM txn WHERE external_id IS NOT NULL") suspend fun externalIds(): List<String>

    @Query("SELECT * FROM txn WHERE debt_id IS NOT NULL")
    fun observeDebtEntries(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM txn WHERE debt_id = :debtId ORDER BY date, id") suspend fun forDebt(debtId: Long): List<TransactionEntity>

    @Query("DELETE FROM txn WHERE debt_id = :debtId") suspend fun deleteForDebt(debtId: Long)

    @Query("DELETE FROM txn WHERE account_id = :accountId OR to_account_id = :accountId")
    suspend fun deleteForAccount(accountId: Long)

    @Query(
        """
        DELETE FROM txn WHERE kind = 'TRANSFER'
            AND ((account_id = :a AND to_account_id = :b) OR (account_id = :b AND to_account_id = :a))
        """,
    )
    suspend fun deleteTransfersBetween(a: Long, b: Long)

    @Query("UPDATE txn SET account_id = :to, updated_at = :now WHERE account_id = :from")
    suspend fun moveAccount(from: Long, to: Long, now: Long)

    @Query("UPDATE txn SET to_account_id = :to, updated_at = :now WHERE to_account_id = :from")
    suspend fun moveToAccount(from: Long, to: Long, now: Long)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debt ORDER BY date, id")
    fun observeAll(): Flow<List<DebtEntity>>

    @Upsert suspend fun upsert(debt: DebtEntity): Long
    @Query("DELETE FROM debt WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface RecurringPaymentDao {
    @Query("SELECT * FROM recurring_payment ORDER BY next_date, name")
    fun observeAll(): Flow<List<RecurringPaymentEntity>>

    @Query("SELECT * FROM recurring_payment WHERE id = :id") suspend fun get(id: Long): RecurringPaymentEntity?

    @Query("SELECT * FROM recurring_payment WHERE next_date <= :epochDay ORDER BY next_date, name")
    suspend fun dueBy(epochDay: Long): List<RecurringPaymentEntity>

    @Upsert suspend fun upsert(payment: RecurringPaymentEntity): Long
    @Query("DELETE FROM recurring_payment WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM recurring_payment WHERE account_id = :accountId") suspend fun deleteForAccount(accountId: Long)
    @Query("UPDATE recurring_payment SET account_id = :to WHERE account_id = :from") suspend fun moveAccount(from: Long, to: Long)
}

@Dao
interface PendingEntryDao {
    /** Everything waiting, plus what was finished as added since [since]. */
    @Query("SELECT * FROM pending_entry WHERE status = 'WAITING' OR (status = 'ADDED' AND posted_at >= :since) ORDER BY status = 'WAITING' DESC, posted_at DESC")
    fun observe(since: Long): Flow<List<PendingEntryEntity>>

    @Query("SELECT COUNT(*) FROM pending_entry WHERE status = 'WAITING'")
    fun observeWaitingCount(): Flow<Int>

    @Query("SELECT * FROM pending_entry WHERE id = :id") suspend fun get(id: Long): PendingEntryEntity?

    /** Returns -1 for a notification already seen. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entry: PendingEntryEntity): Long

    @Query("UPDATE pending_entry SET status = :status WHERE id = :id") suspend fun setStatus(id: Long, status: String)

    @Query("DELETE FROM pending_entry WHERE status <> 'WAITING' AND posted_at < :before") suspend fun deleteFinishedBefore(before: Long)

    @Query("SELECT * FROM merchant_alias") fun observeAliases(): Flow<List<MerchantAliasEntity>>

    @Upsert suspend fun upsertAlias(alias: MerchantAliasEntity)
}

@Dao
interface MaintenanceDao {
    /** Archived rows count: an install that has ever been used is never a fresh one. */
    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM account) OR EXISTS(SELECT 1 FROM person)
            OR EXISTS(SELECT 1 FROM category) OR EXISTS(SELECT 1 FROM txn)
        """,
    )
    suspend fun hasData(): Boolean

    @Query("DELETE FROM txn") suspend fun clearTransactions()
    @Query("DELETE FROM debt") suspend fun clearDebts()
    @Query("DELETE FROM recurring_payment") suspend fun clearRecurring()
    @Query("DELETE FROM pending_entry") suspend fun clearPending()
    @Query("DELETE FROM merchant") suspend fun clearMerchants()
    @Query("DELETE FROM account") suspend fun clearAccounts()
}

@Dao
interface ReceiptDao {
    @Transaction
    @Query("SELECT * FROM receipt ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<ReceiptWithItems>>

    @Query("SELECT EXISTS(SELECT 1 FROM receipt WHERE fiscal_id = :fiscalId)") suspend fun exists(fiscalId: String): Boolean
    @Insert suspend fun insert(receipt: ReceiptEntity): Long
    @Insert suspend fun insertItems(items: List<ReceiptItemEntity>)
    @Query("DELETE FROM receipt WHERE id = :id") suspend fun delete(id: Long)
}
