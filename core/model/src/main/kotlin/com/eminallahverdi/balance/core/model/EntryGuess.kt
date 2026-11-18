package com.eminallahverdi.balance.core.model

import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private const val DAYS_FROM_A_PAYMENT = 10L
private const val SHORTEST_WORD_TO_MATCH = 4

/**
 * What a pending entry would be saved as. [isKnown] means the user confirmed this merchant themselves, the only case
 * auto-confirm acts on. [isSuggested] means the category was worked out, not remembered, so the card says so.
 * [toAccountId] is only set on a transfer, which has neither a merchant nor a category.
 */
data class EntryGuess(
    val merchantName: String,
    val categoryId: Long?,
    val personId: Long?,
    val accountId: Long?,
    val isKnown: Boolean,
    val isSuggested: Boolean = false,
    val toAccountId: Long? = null,
    /** Why the category is what it is, shown with a suggestion. */
    val reason: GuessReason? = null,
)

/**
 * Works out a pending entry, trying what's most likely first: what the owner usually files this shop as, by any of its
 * spellings, or else what it was last time; a recurring payment or loan by name or by amount; the words themselves; and
 * for a shop never seen, the owner's other payments of about this amount, at about this time of day when that's known.
 * [history] is the owner's recent entries, for the first and the last of those. None of it depends on which bank sent the
 * notification. The card named in the notification beats every guess at the account.
 *
 * Cash taken out goes from the card to the cash account, whichever shop's machine it came from, so it's never spending
 * and auto-confirm never acts on it.
 */
fun guessFor(
    entry: PendingEntry,
    aliases: Map<String, Long>,
    merchants: List<Merchant>,
    categories: List<Category>,
    defaultAccountId: Long?,
    recurring: List<RecurringPayment> = emptyList(),
    accounts: List<Account> = emptyList(),
    history: List<TransactionDetails> = emptyList(),
    zone: ZoneId = ZoneId.systemDefault(),
): EntryGuess {
    val active = accounts.filterNot { it.isArchived }
    val cardAccountId = active.firstOrNull { entry.cardEnding.isNotEmpty() && it.cardEnding == entry.cardEnding }?.id
    if (entry.kind == TransactionKind.TRANSFER) {
        val from = cardAccountId ?: defaultAccountId
        val cash = active.firstOrNull { it.type == AccountType.CASH && it.id != from }?.id
        return EntryGuess("", categoryId = null, personId = null, accountId = from, isKnown = false, toAccountId = cash)
    }
    val words = wordsIn("${entry.merchant} ${entry.title}")
    val confirmed = aliases[entry.merchant.caseKey()]?.let { id -> merchants.firstOrNull { it.id == id } }
    val merchant = confirmed
        ?: merchants.firstOrNull { it.name.equals(prettyMerchant(entry.merchant), ignoreCase = true) }
        ?: merchants.filter { it.lastCategoryId != null }.sortedByDescending { it.useCount }.firstOrNull { nameIsIn(it.name, words) }
    val expense = entry.kind == TransactionKind.EXPENSE
    fun live(id: Long?) = categories.firstOrNull { it.id == id && !it.isArchived }
    // What the shop is usually filed as wins over what it was last time, so one odd purchase doesn't steer the next.
    val usual = if (expense) usualFiling(merchant?.name ?: prettyMerchant(entry.merchant), history)?.takeIf { live(it.categoryId) != null } else null
    val remembered = live(usual?.categoryId) ?: live(merchant?.lastCategoryId)
    val payment = if (expense && remembered == null) paymentFor(entry, words, recurring) else null
    val fromPayment = live(payment?.categoryId)
    val fromWords = if (expense && remembered == null && fromPayment == null) categoryFromWords(words, categories) else null
    val alike = if (expense && remembered == null && fromPayment == null && fromWords == null) {
        alikeFiling(entry.amount, entry.postedAt, history, zone)?.takeIf { live(it.categoryId) != null }
    } else {
        null
    }
    val category = remembered ?: fromPayment ?: fromWords ?: live(alike?.categoryId)
    val shopName = merchant?.name ?: usual?.shop
    val reason = when {
        category == null -> null
        remembered != null -> GuessReason.UsualAt(shopName ?: prettyMerchant(entry.merchant), usual?.times ?: 1)
        fromPayment != null -> GuessReason.Recurring(payment!!.name)
        fromWords != null -> GuessReason.Words
        else -> alike?.reason
    }
    val known = confirmed != null && remembered != null
    return EntryGuess(
        merchantName = shopName ?: payment?.name ?: prettyMerchant(entry.merchant),
        categoryId = category?.id,
        personId = usual?.personId ?: merchant?.lastPersonId ?: payment?.personId ?: alike?.personId
            ?: category?.defaultPersonId ?: categories.firstOrNull { it.id == category?.parentId }?.defaultPersonId,
        accountId = cardAccountId ?: usual?.accountId ?: merchant?.lastAccountId ?: payment?.accountId ?: defaultAccountId,
        isKnown = known,
        isSuggested = category != null && !known,
        reason = reason,
    )
}

/**
 * BRAVO SUPERMARKET reads as Bravo Supermarket, until the user names it something else. A word keeps its own capital, so
 * İNŞAAT reads as İnşaat, not Inşaat.
 */
fun prettyMerchant(raw: String): String =
    raw.trim().split(Regex("\\s+")).joinToString(" ") { word ->
        if (word.isEmpty()) word else word.first().let { if (it.isUpperCase()) it.toString() else it.titlecase(Locale.ROOT) } + word.drop(1).caseKey()
    }

/** A recurring payment or loan this looks like: its name in the text, or its amount within days of when it's due. */
private fun paymentFor(entry: PendingEntry, words: Set<String>, recurring: List<RecurringPayment>): RecurringPayment? {
    val active = recurring.filterNot { it.isFinished }
    val day = entry.postedAt.atZone(ZoneId.systemDefault()).toLocalDate()
    return active.firstOrNull { nameIsIn(it.name, words) }
        ?: active.firstOrNull { it.amountDue == entry.amount && abs(ChronoUnit.DAYS.between(day, it.nextDate)) <= DAYS_FROM_A_PAYMENT }
}

/** The user's own category names first, sub-categories before their parents, then the words any shopper would recognise. */
internal fun categoryFromWords(words: Set<String>, categories: List<Category>): Category? {
    val spendable = categories.filter { it.type == CategoryType.EXPENSE && !it.isArchived && !it.name.equals("Other", ignoreCase = true) && !it.name.equals("Digər", ignoreCase = true) }
    spendable.sortedByDescending { it.parentId != null }.firstOrNull { nameIsIn(it.name, words) }?.let { return it }
    val hint = CATEGORY_HINTS.firstOrNull { hint -> hint.words.any { word -> words.any { it.matches(word) } } } ?: return null
    return hint.names.firstNotNullOfOrNull { name -> spendable.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        ?: spendable.firstOrNull { it.icon == hint.icon }
}

private fun wordsIn(text: String): Set<String> = text.caseKey().split(Regex("""[^\p{L}\d]+""")).filter { it.isNotEmpty() }.toSet()

/** Every word of [name] is in the text. Short names stay out of it, as they'd match by chance. */
private fun nameIsIn(name: String, words: Set<String>): Boolean {
    val parts = wordsIn(name)
    return parts.isNotEmpty() && parts.any { it.length >= SHORTEST_WORD_TO_MATCH } && parts.all { it in words }
}

/** Long words match their endings too, as in APTEKI. Short ones match whole, or NAR would be in NARGIZ. */
private fun String.matches(hint: String): Boolean = if (hint.length < SHORTEST_WORD_TO_MATCH) this == hint else startsWith(hint)

private class CategoryHint(val icon: String, val names: List<String>, val words: List<String>)

/**
 * Words that give a payment away, whichever bank wrote it. Categories can be renamed, so each points at likely names,
 * in both of the starter template's languages, and then an icon. Adding a word here is all it takes to teach a new one.
 */
private val CATEGORY_HINTS = listOf(
    CategoryHint("local_taxi", listOf("Taxi", "Transport", "Taksi", "Nəqliyyat"), listOf("yango", "bolt", "uber", "taxi", "taksi", "такси")),
    CategoryHint("directions_bus", listOf("Public transport", "Transport", "İctimai nəqliyyat", "Nəqliyyat"), listOf("bakikart", "metro", "avtobus", "bus")),
    CategoryHint("local_gas_station", listOf("Fuel", "Yanacaq"), listOf("azpetrol", "socar", "lukoil", "petrol", "benzin", "yanacaq", "fuel")),
    CategoryHint("local_cafe", listOf("Coffee shop", "Kofe"), listOf("coffee", "kofe", "kafe", "cafe", "starbucks", "espresso", "кофе")),
    CategoryHint("restaurant", listOf("Delivery", "Çatdırılma"), listOf("wolt", "yemeksepeti", "foody")),
    CategoryHint("restaurant", listOf("Restaurant", "Cafe & Restaurants", "Restoran", "Kafe və restoranlar"), listOf("restoran", "restaurant", "pizza", "burger", "kfc", "mcdonald", "dominos", "ресторан")),
    CategoryHint("local_pharmacy", listOf("Pharmacy", "Health", "Aptek", "Sağlamlıq"), listOf("aptek", "pharmacy", "zeytun", "derman", "аптек")),
    CategoryHint("smartphone", listOf("Mobile top-up", "Mobil balans"), listOf("azercell", "azerfon", "bakcell", "nar")),
    CategoryHint("apps", listOf("Apps", "Subscriptions", "Tətbiqlər", "Abunəliklər"), listOf("spotify", "netflix", "youtube", "icloud", "apple", "google", "adobe", "openai", "chatgpt", "anthropic", "claude")),
    CategoryHint("bolt", listOf("Electricity", "Utilities", "İşıq", "Kommunal xidmətlər"), listOf("azerishiq", "azərişıq")),
    CategoryHint("bolt", listOf("Gas", "Utilities", "Qaz", "Kommunal xidmətlər"), listOf("azerigaz", "azəriqaz")),
    CategoryHint("bolt", listOf("Water", "Utilities", "Su", "Kommunal xidmətlər"), listOf("azersu", "azərsu")),
    CategoryHint("hotel", listOf("Hotel", "Travel", "Otel", "Səyahət"), listOf("hotel", "booking", "airbnb", "otel", "отель")),
    CategoryHint("flight", listOf("Travel", "Səyahət"), listOf("azal", "airline", "flight", "turkish")),
    CategoryHint("sports_esports", listOf("Cinema", "Entertainment", "Kino", "Əyləncə"), listOf("cinema", "kino", "кино")),
    CategoryHint("shopping_bag", listOf("Shopping", "Alış-veriş"), listOf("trendyol", "amazon", "temu", "aliexpress", "zara", "lcwaikiki", "mango")),
    CategoryHint("shopping_cart", listOf("Groceries", "Ərzaq"), listOf("supermarket", "market", "bravo", "araz", "neptun", "bazarstore", "oba", "grocery", "erzaq", "супермаркет", "продукт")),
)
