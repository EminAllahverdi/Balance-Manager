package com.eminallahverdi.balance.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.eminallahverdi.balance.core.database.entity.AccountEntity
import com.eminallahverdi.balance.core.database.entity.CategoryEntity
import com.eminallahverdi.balance.core.database.entity.MerchantEntity
import com.eminallahverdi.balance.core.database.entity.PersonEntity
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
    @Query("SELECT COUNT(*) FROM txn WHERE person_id = :id") suspend fun usageCount(id: Long): Int
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
    @Query("SELECT COUNT(*) FROM txn WHERE category_id = :id OR category_id IN (SELECT id FROM category WHERE parent_id = :id)")
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
}

/** Per-account sum of every movement, in minor units. */
data class AccountSum(val accountId: Long, val total: Long)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM txn WHERE date BETWEEN :fromEpochDay AND :toEpochDay ORDER BY date DESC, created_at DESC")
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM txn ORDER BY date DESC, created_at DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM txn WHERE id = :id") suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT * FROM txn WHERE category_id = :categoryId AND kind = 'EXPENSE' ORDER BY date DESC, created_at DESC LIMIT 1")
    suspend fun lastForCategory(categoryId: Long): TransactionEntity?

    /**
     * Balance contribution per account. Outgoing side of transfers and expenses are negative;
     * ADJUSTMENT and INTERNAL rows already carry their sign.
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
}

@Dao
interface MaintenanceDao {
    @Query("DELETE FROM txn") suspend fun clearTransactions()
}
