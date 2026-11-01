package com.eminallahverdi.balance.importer.moneymanager

import com.eminallahverdi.balance.core.model.AccountType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val GREY = "8A94A3"
private val json = Json { ignoreUnknownKeys = true }

/**
 * How a backup's categories and tags become this app's. It's a JSON file the user keeps on their device, never in the repo,
 * because it names their family. Without one, categories keep their names and tags become sub-categories.
 *
 * Every rule that matches an entry applies, in file order, setting only what it names. General rules go first and later
 * ones refine them. A rule with an empty `set` marks a tag as saying nothing, so it doesn't become a sub-category.
 *
 *     { "when": { "type": "Expense", "category": "Food", "tag": "Takeaway" }, "set": { "category": "Eating out", "sub": "Delivery" } }
 *     { "when": { "tag": "Sam" }, "set": { "person": "Sam" } }
 *     { "when": { "category": "Food", "note": "pizza" }, "set": { "merchant": "Pizza Place" } }
 *     { "when": { "type": "Income", "category": "Card offset" }, "set": { "kind": "INTERNAL", "note": "Card offset" } }
 */
@Serializable
data class ImportMapping(
    val defaultAccount: String? = null,
    val people: List<MappedPerson> = emptyList(),
    val accounts: List<MappedAccount> = emptyList(),
    val categories: List<MappedCategory> = emptyList(),
    val rules: List<MappingRule> = emptyList(),
) {
    companion object {
        fun parse(text: String): ImportMapping = json.decodeFromString(text)
    }
}

@Serializable
data class MappedPerson(val name: String, val color: String = GREY)

/** [name] is the backup's. [rename] is what to call it here. */
@Serializable
data class MappedAccount(
    val name: String,
    val rename: String? = null,
    val type: AccountType = AccountType.DEBIT_CARD,
    val icon: String = "payments",
    val color: String = GREY,
    val showOnWidget: Boolean = true,
)

/** A category to create, or to give a default person. Existing ones keep their icon and colour. */
@Serializable
data class MappedCategory(
    val name: String,
    val type: String = "Expense",
    val icon: String = "more_horiz",
    val color: String = GREY,
    val person: String? = null,
)

@Serializable
data class MappingRule(@SerialName("when") val match: RuleMatch, val set: RuleChange = RuleChange())

@Serializable
data class RuleMatch(val type: String? = null, val category: String? = null, val tag: String? = null, val note: String? = null) {
    fun matches(entry: BackupEntry): Boolean =
        (type == null || type.equals(if (entry.isIncome) "Income" else "Expense", ignoreCase = true)) &&
            (category == null || category == entry.category) &&
            (tag == null || tag in entry.tags) &&
            (note == null || entry.note.contains(note, ignoreCase = true))
}

/** [kind] is a [com.eminallahverdi.balance.core.model.TransactionKind]. [note] fills in only when the entry has none. */
@Serializable
data class RuleChange(
    val kind: String? = null,
    val category: String? = null,
    val sub: String? = null,
    val person: String? = null,
    val merchant: String? = null,
    val note: String? = null,
)

/** "5B9DFF" as the ARGB long the app stores. */
internal fun hexColor(hex: String): Long = 0xFF000000 or hex.removePrefix("#").toLong(radix = 16)
