package com.eminallahverdi.balance.core.data

import androidx.room.withTransaction
import com.eminallahverdi.balance.core.database.BalanceDatabase
import com.eminallahverdi.balance.core.database.entity.AccountEntity
import com.eminallahverdi.balance.core.database.entity.CategoryEntity
import com.eminallahverdi.balance.core.database.entity.MerchantEntity
import com.eminallahverdi.balance.core.database.entity.PersonEntity
import com.eminallahverdi.balance.core.database.entity.TransactionEntity
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.CsvEntry
import com.eminallahverdi.balance.core.model.CsvImport
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.core.model.nameKey
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val NEUTRAL_COLOUR = 0xFF8A94A3
private const val NEUTRAL_ICON = "more_horiz"
private const val FALLBACK_EXPENSE = "Other"
private const val FALLBACK_INCOME = "Other income"
private val PERSON_COLOURS = listOf(0xFFFF7AB6, 0xFFFFB547, 0xFFA78BFA, 0xFF4ADE9B, 0xFFFF7A66, 0xFF6FC7D9)

/**
 * Writes a parsed file, adding whatever it names that isn't here yet. An entry with no account goes to the first one, one
 * with no category to Other or Other income, and one with no person to its category's default and then the owner.
 */
@Singleton
class CsvImporter @Inject constructor(private val db: BalanceDatabase) {

    suspend fun write(import: CsvImport) = db.withTransaction {
        val entries = import.entries.sortedBy { it.date }
        val accounts = accountIds(entries)
        val people = personIds(entries)
        val known = db.categoryDao().getAll().toMutableList()
        val rows = entries.map { it to categoryFor(it, known) }
        val firstAccount = db.accountDao().getAll().first().id
        val owner = db.personDao().getAll().first().id
        fun accountOf(entry: CsvEntry) = entry.account?.let { accounts[nameKey(it)] } ?: firstAccount
        fun personOf(entry: CsvEntry, category: CategoryEntity?): Long? {
            val parent = category?.parentId?.let { id -> known.firstOrNull { it.id == id } }
            return entry.person?.let { people[nameKey(it)] }
                ?: if (category == null) null else category.defaultPersonId ?: parent?.defaultPersonId ?: owner
        }
        val merchants = rows.filter { (entry, _) -> entry.merchant != null }.groupBy { (entry, _) -> nameKey(entry.merchant!!) }.mapValues { (_, uses) ->
            val (last, category) = uses.last()
            db.merchantDao().insert(
                MerchantEntity(
                    name = last.merchant!!,
                    lastCategoryId = category?.id,
                    lastAccountId = accountOf(last),
                    lastPersonId = personOf(last, category),
                    useCount = uses.size,
                ),
            )
        }
        val now = Instant.now().toEpochMilli()
        db.transactionDao().insertAll(
            rows.mapIndexed { order, (entry, category) ->
                TransactionEntity(
                    kind = entry.kind.name,
                    amountMinor = entry.amount.minor,
                    date = entry.date.toEpochDay(),
                    accountId = accountOf(entry),
                    toAccountId = entry.toAccount?.let { accounts[nameKey(it)] },
                    categoryId = category?.id,
                    personId = personOf(entry, category),
                    merchantId = entry.merchant?.let { merchants[nameKey(it)] },
                    note = entry.note,
                    // Keeps the file's order within a day, which is how lists sort.
                    createdAt = now + order,
                    updatedAt = now + order,
                )
            },
        )
    }

    private suspend fun accountIds(entries: List<CsvEntry>): Map<String, Long> {
        val dao = db.accountDao()
        val ids = dao.getAll().associate { nameKey(it.name) to it.id }.toMutableMap()
        (entries.mapNotNull { it.account } + entries.mapNotNull { it.toAccount }).forEach { name ->
            ids.getOrPut(nameKey(name)) {
                dao.upsert(AccountEntity(name = name, type = AccountType.DEBIT_CARD.name, icon = "credit_card", color = NEUTRAL_COLOUR, sortOrder = ids.size))
            }
        }
        return ids
    }

    private suspend fun personIds(entries: List<CsvEntry>): Map<String, Long> {
        val dao = db.personDao()
        val ids = dao.getAll().associate { nameKey(it.name) to it.id }.toMutableMap()
        entries.mapNotNull { it.person }.forEach { name ->
            ids.getOrPut(nameKey(name)) {
                dao.upsert(PersonEntity(name = name, color = PERSON_COLOURS[ids.size % PERSON_COLOURS.size], sortOrder = ids.size))
            }
        }
        return ids
    }

    /** Only spending and income have a category. A name can be a sub-category's, but a top-level one wins. */
    private suspend fun categoryFor(entry: CsvEntry, known: MutableList<CategoryEntity>): CategoryEntity? {
        val type = when (entry.kind) {
            TransactionKind.EXPENSE -> CategoryType.EXPENSE
            TransactionKind.INCOME -> CategoryType.INCOME
            else -> return null
        }
        val name = entry.category ?: if (type == CategoryType.EXPENSE) FALLBACK_EXPENSE else FALLBACK_INCOME
        val sameName = known.filter { it.type == type.name && nameKey(it.name) == nameKey(name) }
        val category = sameName.firstOrNull { it.parentId == null }
            ?: sameName.firstOrNull { entry.subCategory == null }
            ?: add(known, name, type, parent = null)
        val sub = entry.subCategory ?: return category
        return known.firstOrNull { it.parentId == category.id && nameKey(it.name) == nameKey(sub) } ?: add(known, sub, type, parent = category)
    }

    private suspend fun add(known: MutableList<CategoryEntity>, name: String, type: CategoryType, parent: CategoryEntity?): CategoryEntity {
        val category = CategoryEntity(
            name = name,
            type = type.name,
            parentId = parent?.id,
            icon = parent?.icon ?: NEUTRAL_ICON,
            color = parent?.color ?: NEUTRAL_COLOUR,
            sortOrder = known.size,
        )
        return category.copy(id = db.categoryDao().upsert(category)).also(known::add)
    }
}
