package com.eminallahverdi.balance.core.data

import androidx.room.withTransaction
import com.eminallahverdi.balance.core.database.BalanceDatabase
import com.eminallahverdi.balance.core.database.dao.AccountDao
import com.eminallahverdi.balance.core.database.dao.CategoryDao
import com.eminallahverdi.balance.core.database.dao.MerchantDao
import com.eminallahverdi.balance.core.database.dao.PersonDao
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.Merchant
import com.eminallahverdi.balance.core.model.Person
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
) {
    val people: Flow<List<Person>> = personDao.observeAll().map { list -> list.map { it.toModel() } }
    val categories: Flow<List<Category>> = categoryDao.observeAll().map { list -> list.map { it.toModel() } }
    val accounts: Flow<List<Account>> = accountDao.observeAll().map { list -> list.map { it.toModel() } }
    val merchants: Flow<List<Merchant>> = merchantDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun savePerson(person: Person): Long = personDao.upsert(person.toEntity())
    suspend fun saveCategory(category: Category): Long = categoryDao.upsert(category.toEntity())
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
