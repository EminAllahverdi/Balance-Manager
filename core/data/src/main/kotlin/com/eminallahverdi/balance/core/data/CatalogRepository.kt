package com.eminallahverdi.balance.core.data

import androidx.room.withTransaction
import com.eminallahverdi.balance.core.database.BalanceDatabase
import com.eminallahverdi.balance.core.database.dao.AccountDao
import com.eminallahverdi.balance.core.database.dao.CategoryDao
import com.eminallahverdi.balance.core.database.dao.MerchantDao
import com.eminallahverdi.balance.core.database.dao.PersonDao
import com.eminallahverdi.balance.core.database.dao.RecurringPaymentDao
import com.eminallahverdi.balance.core.database.dao.TransactionDao
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.Merchant
import com.eminallahverdi.balance.core.model.Person
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the user configures: people, categories, accounts, merchants. */
@Singleton
class CatalogRepository @Inject constructor(
    private val db: BalanceDatabase,
    private val personDao: PersonDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val merchantDao: MerchantDao,
    private val transactionDao: TransactionDao,
    private val recurringDao: RecurringPaymentDao,
) {
    val people: Flow<List<Person>> = personDao.observeAll().map { list -> list.map { it.toModel() } }
    val categories: Flow<List<Category>> = categoryDao.observeAll().map { list -> list.map { it.toModel() } }
    val accounts: Flow<List<Account>> = accountDao.observeAll().map { list -> list.map { it.toModel() } }
    val merchants: Flow<List<Merchant>> = merchantDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun savePerson(person: Person): Long = personDao.upsert(person.toEntity())

    /** Saves a category. Its sub-categories follow a new icon or colour unless they set their own. */
    suspend fun saveCategory(category: Category) = db.withTransaction {
        val all = categoryDao.getAll().map { it.toModel() }
        val old = all.firstOrNull { it.id == category.id }
        categoryDao.upsert(category.toEntity())
        if (old != null) {
            categoryDao.updateAll(all.filter { it.parentId == old.id }.map { it.followParentStyle(old, category).toEntity() })
        }
    }

    suspend fun saveAccount(account: Account): Long = accountDao.upsert(account.toEntity())

    suspend fun reorderPeople(ordered: List<Person>) =
        personDao.updateAll(ordered.mapIndexed { i, p -> p.copy(sortOrder = i).toEntity() })

    suspend fun reorderCategories(ordered: List<Category>) =
        categoryDao.updateAll(ordered.mapIndexed { i, c -> c.copy(sortOrder = i).toEntity() })

    /**
     * Removes a person. If any transaction uses them they are archived instead,
     * so history keeps its colours and names.
     */
    suspend fun removePerson(person: Person): RemoveResult =
        if (personDao.usageCount(person.id) > 0) {
            personDao.upsert(person.copy(isArchived = true).toEntity()); RemoveResult.ARCHIVED
        } else {
            personDao.delete(person.id); RemoveResult.DELETED
        }

    suspend fun removeCategory(category: Category): RemoveResult =
        if (categoryDao.usageCount(category.id) > 0) {
            categoryDao.upsert(category.copy(isArchived = true).toEntity()); RemoveResult.ARCHIVED
        } else {
            categoryDao.delete(category.id); RemoveResult.DELETED
        }

    suspend fun removeAccount(account: Account): RemoveResult =
        if (accountDao.usageCount(account.id) > 0) {
            accountDao.upsert(account.copy(isArchived = true).toEntity()); RemoveResult.ARCHIVED
        } else {
            accountDao.delete(account.id); RemoveResult.DELETED
        }

    /** Moves everything that uses [from] to [to]. A transfer between the two would go from an account to itself, so those go. */
    suspend fun moveAccountEntries(from: Long, to: Long) = db.withTransaction {
        val now = Instant.now().toEpochMilli()
        transactionDao.deleteTransfersBetween(from, to)
        transactionDao.moveAccount(from, to, now)
        transactionDao.moveToAccount(from, to, now)
        recurringDao.moveAccount(from, to)
        merchantDao.replaceLastAccount(from, to)
    }

    /** Deletes an account for good, with every entry and recurring payment on it. Past months change, so the user is told first. */
    suspend fun deleteAccount(id: Long) = db.withTransaction {
        transactionDao.deleteForAccount(id)
        recurringDao.deleteForAccount(id)
        merchantDao.replaceLastAccount(id, null)
        accountDao.delete(id)
    }

    suspend fun isEmpty(): Boolean = accountDao.getAll().isEmpty()

    /** Fills an empty database from a template (first run, or someone you shared the app with). */
    suspend fun applyTemplate(template: StarterTemplate) = db.withTransaction {
        val personIds = template.people.associate { p -> p.key to personDao.upsert(p.toEntity()) }
        template.accounts.forEach { accountDao.upsert(it.toEntity()) }
        template.categories.forEachIndexed { index, t ->
            val parent = t.toEntity(sortOrder = index, defaultPersonId = personIds[t.defaultPerson])
            val parentId = categoryDao.upsert(parent)
            t.children.forEachIndexed { childIndex, child ->
                categoryDao.upsert(
                    child.toEntity(sortOrder = childIndex, defaultPersonId = personIds[child.defaultPerson])
                        .copy(parentId = parentId, icon = child.icon ?: parent.icon, color = child.color ?: parent.color),
                )
            }
        }
    }
}

enum class RemoveResult { DELETED, ARCHIVED }
