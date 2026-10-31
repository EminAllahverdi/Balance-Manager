package com.eminallahverdi.balance.core.data

import com.eminallahverdi.balance.core.database.dao.MerchantDao
import com.eminallahverdi.balance.core.database.dao.TransactionDao
import com.eminallahverdi.balance.core.database.entity.MerchantEntity
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountBalance
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.Merchant
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.Transaction
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.balanceAdjustment
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** What the quick-add panel pre-fills after the user picks a category. */
data class Suggestion(
    val subCategoryId: Long?,
    val merchantName: String?,
    val personId: Long?,
    val accountId: Long?,
    val recentMerchants: List<String>,
)

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val merchantDao: MerchantDao,
    private val catalog: CatalogRepository,
) {
    fun observeDetails(range: DateRange): Flow<List<TransactionDetails>> =
        joinDetails(dao.observeBetween(range.start.toEpochDay(), range.endInclusive.toEpochDay()).map { r -> r.map { it.toModel() } })

    fun observeLatest(limit: Int = 200): Flow<List<TransactionDetails>> =
        joinDetails(dao.observeLatest(limit).map { r -> r.map { it.toModel() } })

    fun observeTransfers(limit: Int): Flow<List<TransactionDetails>> =
        joinDetails(dao.observeTransfers(limit).map { r -> r.map { it.toModel() } })

    val balances: Flow<List<AccountBalance>> = combine(catalog.accounts, dao.observeAccountSums()) { accounts, sums ->
        val byAccount = sums.groupBy { it.accountId }.mapValues { (_, v) -> v.sumOf { it.total } }
        accounts.filterNot { it.isArchived }.map { AccountBalance(it, Money(byAccount[it.id] ?: 0)) }
    }

    suspend fun get(id: Long): Transaction? = dao.get(id)?.toModel()

    /** Saves and teaches the merchant what was chosen, so next time is one tap. */
    suspend fun save(transaction: Transaction, merchantName: String?): Long {
        val now = Instant.now()
        val isNew = transaction.id == 0L
        val merchantId = merchantName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            val existing = merchantDao.findByName(name)
            if (existing == null) {
                merchantDao.insert(
                    MerchantEntity(name = name, lastCategoryId = transaction.categoryId, lastAccountId = transaction.accountId, lastPersonId = transaction.personId, useCount = 1),
                )
            } else {
                merchantDao.update(
                    existing.copy(lastCategoryId = transaction.categoryId, lastAccountId = transaction.accountId, lastPersonId = transaction.personId, useCount = existing.useCount + if (isNew) 1 else 0),
                )
                existing.id
            }
        }
        val toSave = transaction.copy(
            merchantId = merchantId,
            createdAt = if (isNew) now else transaction.createdAt,
            updatedAt = now,
        )
        // An edit keeps the import id, or the next import would add this transaction again.
        return dao.upsert(toSave.toEntity(externalId = dao.get(transaction.id)?.externalId))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Makes the account read [target] by recording the difference as an adjustment. */
    suspend fun adjustBalance(accountId: Long, current: Money, target: Money) {
        balanceAdjustment(accountId, current, target, LocalDate.now())?.let { save(it, merchantName = null) }
    }

    /** Pre-fill from the last time this category (or one of its sub-categories) was used. */
    suspend fun suggestFor(category: Category, subCategories: List<Category>): Suggestion {
        val candidates = listOf(category) + subCategories
        val last = candidates.mapNotNull { dao.lastForCategory(it.id) }.maxWithOrNull(compareBy({ it.date }, { it.createdAt }))
        val merchants = candidates.flatMap { merchantDao.recentForCategory(it.id, 6) }.sortedByDescending { it.useCount }.map { it.name }.distinct().take(6)
        val merchantName = last?.merchantId?.let { merchantDao.get(it)?.name }
        return Suggestion(
            subCategoryId = last?.categoryId?.takeIf { id -> subCategories.any { it.id == id } },
            merchantName = merchantName,
            personId = last?.personId ?: category.defaultPersonId,
            accountId = last?.accountId,
            recentMerchants = merchants,
        )
    }

    private fun joinDetails(source: Flow<List<Transaction>>): Flow<List<TransactionDetails>> =
        combine(source, catalog.accounts, catalog.categories, catalog.people, catalog.merchants) { txns, accounts, categories, people, merchants ->
            val a = accounts.associateBy(Account::id)
            val c = categories.associateBy(Category::id)
            val p = people.associateBy(Person::id)
            val m = merchants.associateBy(Merchant::id)
            txns.mapNotNull { t ->
                val account = a[t.accountId] ?: return@mapNotNull null
                val category = t.categoryId?.let(c::get)
                TransactionDetails(
                    transaction = t,
                    account = account,
                    toAccount = t.toAccountId?.let(a::get),
                    category = category,
                    parentCategory = category?.parentId?.let(c::get),
                    person = t.personId?.let(p::get),
                    merchant = t.merchantId?.let(m::get),
                )
            }
        }
}
