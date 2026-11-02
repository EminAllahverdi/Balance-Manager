package com.eminallahverdi.balance.importer.moneymanager

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.eminallahverdi.balance.core.common.IoDispatcher
import com.eminallahverdi.balance.core.database.BalanceDatabase
import com.eminallahverdi.balance.core.database.dao.CategoryDao
import com.eminallahverdi.balance.core.database.entity.AccountEntity
import com.eminallahverdi.balance.core.database.entity.CategoryEntity
import com.eminallahverdi.balance.core.database.entity.MerchantEntity
import com.eminallahverdi.balance.core.database.entity.PersonEntity
import com.eminallahverdi.balance.core.database.entity.TransactionEntity
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.CategoryType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val DEFAULT_ICON = "more_horiz"
private const val DEFAULT_COLOR = 0xFF8A94A3

/** What an import would do, for the user to see before anything changes. */
data class ImportPreview(val plan: ImportPlan, val newCategories: List<String>)

/**
 * Brings a Money Manager backup into the app. Accounts, entries, loans and reminders are replaced, so running it again
 * corrects the last run instead of adding to it. People and categories stay, and whatever's missing is added.
 */
@Singleton
class MoneyManagerImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: BalanceDatabase,
    private val preferences: UserPreferencesRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun prepare(backupFile: Uri, mappingFile: Uri?): ImportPreview = withContext(io) {
        val resolver = context.contentResolver
        val backup = resolver.openInputStream(backupFile)?.use { readBackup(context, it) } ?: throw IOException("Can't open the backup")
        val mapping = mappingFile?.let { uri ->
            resolver.openInputStream(uri)?.use { ImportMapping.parse(it.readBytes().decodeToString()) } ?: throw IOException("Can't open the mapping")
        } ?: ImportMapping()
        val plan = planImport(backup, mapping)
        val existing = db.categoryDao().getAll().filter { it.parentId == null }.map { it.name.lowercase() to it.type }.toSet()
        val added = plan.entries.mapNotNull { entry -> entry.category?.let { it to entry.categoryType.name } }
            .distinct()
            .filter { (name, type) -> (name.lowercase() to type) !in existing }
            .map { it.first }
        ImportPreview(plan, added)
    }

    suspend fun import(plan: ImportPlan) = withContext(io) {
        val defaultAccount = db.withTransaction {
            val maintenance = db.maintenanceDao()
            val cardEndings = db.accountDao().getAll().associate { it.name to it.cardEnding }
            maintenance.clearTransactions()
            maintenance.clearDebts()
            maintenance.clearRecurring()
            maintenance.clearPending()
            maintenance.clearMerchants()
            maintenance.clearAccounts()
            val accounts = plan.accounts.mapIndexed { order, account ->
                account.source.uid to db.accountDao().upsert(account.toEntity(order).copy(cardEnding = cardEndings[account.name].orEmpty()))
            }.toMap()
            val people = addPeople(plan)
            val categories = Categories(db.categoryDao(), people, plan.categories)
            categories.applyDefaultPeople()

            val fallbackPerson = people.values.firstOrNull()
            val entries = plan.entries.map { entry ->
                val category = entry.category?.let { categories.resolve(it, entry.sub, entry.categoryType) }
                entry to category
            }
            val merchants = addMerchants(entries, accounts, people)
            db.transactionDao().insertAll(
                entries.map { (entry, category) ->
                    TransactionEntity(
                        kind = entry.kind.name,
                        amountMinor = entry.amount,
                        date = entry.date.toEpochDay(),
                        accountId = accounts.getValue(entry.accountUid),
                        toAccountId = entry.toAccountUid?.let(accounts::getValue),
                        categoryId = category?.id,
                        personId = entry.person?.let(people::get) ?: category?.let { categories.defaultPerson(it) ?: fallbackPerson },
                        merchantId = entry.merchant?.let(merchants::get),
                        note = entry.note,
                        externalId = entry.externalId,
                        createdAt = entry.created.toEpochMilli(),
                        updatedAt = entry.created.toEpochMilli(),
                    )
                },
            )
            accounts[plan.defaultAccountUid]
        }
        defaultAccount?.let { preferences.setDefaultExpenseAccount(it) }
    }

    private suspend fun addPeople(plan: ImportPlan): Map<String, Long> {
        val dao = db.personDao()
        val existing = dao.getAll()
        val result = LinkedHashMap<String, Long>()
        existing.forEach { result[it.name] = it.id }
        plan.people.forEachIndexed { order, person ->
            if (result.keys.none { it.equals(person.name, ignoreCase = true) }) {
                result[person.name] = dao.upsert(PersonEntity(name = person.name, color = hexColor(person.color), sortOrder = existing.size + order))
            }
        }
        return result
    }

    /** Written with their last use, so quick add and bank notifications know them from the first day. */
    private suspend fun addMerchants(entries: List<Pair<PlannedEntry, CategoryEntity?>>, accounts: Map<String, Long>, people: Map<String, Long>): Map<String, Long> {
        val dao = db.merchantDao()
        return entries.filter { it.first.merchant != null }.groupBy { it.first.merchant!! }.mapValues { (name, uses) ->
            val (last, category) = uses.last()
            dao.insert(
                MerchantEntity(
                    name = name,
                    lastCategoryId = category?.id,
                    lastAccountId = accounts[last.accountUid],
                    lastPersonId = last.person?.let(people::get),
                    useCount = uses.size,
                ),
            )
        }
    }
}

private fun PlannedAccount.toEntity(order: Int) = AccountEntity(
    name = name,
    type = (mapped?.type ?: AccountType.DEBIT_CARD).name,
    icon = mapped?.icon ?: "payments",
    color = mapped?.color?.let(::hexColor) ?: DEFAULT_COLOR,
    includeInTotal = !source.ignoreInBalance,
    showOnWidget = mapped?.showOnWidget ?: true,
    sortOrder = order,
    // Closed accounts keep their history, but stay out of the way.
    isArchived = source.isRemoved,
)

/** The app's categories by name, adding any an entry needs that aren't there yet. */
private class Categories(private val dao: CategoryDao, private val people: Map<String, Long>, private val mapped: List<MappedCategory>) {
    private val known = mutableListOf<CategoryEntity>()

    private fun same(a: String, b: String) = a.equals(b, ignoreCase = true)

    private fun mappedFor(name: String, type: CategoryType) = mapped.firstOrNull { same(it.name, name) && same(it.type, type.label) }

    private val CategoryType.label get() = if (this == CategoryType.INCOME) "Income" else "Expense"

    suspend fun applyDefaultPeople() {
        known += dao.getAll()
        known.toList().filter { it.parentId == null }.forEach { category ->
            val person = mappedFor(category.name, CategoryType.valueOf(category.type))?.person?.let(people::get) ?: return@forEach
            replace(category, category.copy(defaultPersonId = person))
        }
    }

    suspend fun resolve(name: String, sub: String?, type: CategoryType): CategoryEntity {
        val parent = find(name, type.name, parentId = null) ?: add(name, type)
        if (sub == null) return parent
        return find(sub, type.name, parent.id) ?: addSub(sub, parent)
    }

    fun defaultPerson(category: CategoryEntity): Long? =
        category.defaultPersonId ?: known.firstOrNull { it.id == category.parentId }?.defaultPersonId

    private suspend fun find(name: String, type: String, parentId: Long?): CategoryEntity? {
        val match = known.firstOrNull { same(it.name, name) && it.type == type && it.parentId == parentId } ?: return null
        if (!match.isArchived) return match
        return match.copy(isArchived = false).also { replace(match, it) }
    }

    private suspend fun replace(old: CategoryEntity, new: CategoryEntity) {
        dao.upsert(new)
        known[known.indexOf(old)] = new
    }

    private suspend fun add(name: String, type: CategoryType): CategoryEntity {
        val style = mappedFor(name, type)
        val category = CategoryEntity(
            name = name,
            type = type.name,
            icon = style?.icon ?: DEFAULT_ICON,
            color = style?.color?.let(::hexColor) ?: DEFAULT_COLOR,
            defaultPersonId = style?.person?.let(people::get),
            sortOrder = known.count { it.parentId == null },
        )
        return category.copy(id = dao.upsert(category)).also { known += it }
    }

    private suspend fun addSub(name: String, parent: CategoryEntity): CategoryEntity {
        val sub = CategoryEntity(
            name = name,
            type = parent.type,
            parentId = parent.id,
            icon = parent.icon,
            color = parent.color,
            sortOrder = known.count { it.parentId == parent.id },
        )
        return sub.copy(id = dao.upsert(sub)).also { known += it }
    }
}
