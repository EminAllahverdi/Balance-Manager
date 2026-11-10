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
)

/**
 * Works out a pending entry, trying what's most likely first: a merchant the user confirmed, one they've used by another
 * spelling, a recurring payment or loan by name or by amount, then the words themselves. None of it depends on which bank
 * sent the notification. The card named in the notification beats every guess at the account.
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
): EntryGuess {
    val active = accounts.filterNot { it.isArchived }
    val cardAccountId = active.firstOrNull { entry.cardEnding.isNotEmpty() && it.cardEnding == entry.cardEnding }?.id
    if (entry.kind == TransactionKind.TRANSFER) {
        val from = cardAccountId ?: defaultAccountId
        val cash = active.firstOrNull { it.type == AccountType.CASH && it.id != from }?.id
        return EntryGuess("", categoryId = null, personId = null, accountId = from, isKnown = false, toAccountId = cash)
    }
    val words = wordsIn("${entry.merchant} ${entry.title}")
    val confirmed = aliases[entry.merchant.lowercase(Locale.ROOT)]?.let { id -> merchants.firstOrNull { it.id == id } }
    val merchant = confirmed
        ?: merchants.firstOrNull { it.name.equals(prettyMerchant(entry.merchant), ignoreCase = true) }
        ?: merchants.filter { it.lastCategoryId != null }.sortedByDescending { it.useCount }.firstOrNull { nameIsIn(it.name, words) }
    val remembered = categories.firstOrNull { it.id == merchant?.lastCategoryId && !it.isArchived }
    val expense = entry.kind == TransactionKind.EXPENSE
    val payment = if (expense && remembered == null) paymentFor(entry, words, recurring) else null
    val category = remembered
        ?: categories.firstOrNull { it.id == payment?.categoryId && !it.isArchived }
        ?: if (expense) categoryFromWords(words, categories) else null
    return EntryGuess(
        merchantName = merchant?.name ?: payment?.name ?: prettyMerchant(entry.merchant),
        categoryId = category?.id,
        personId = merchant?.lastPersonId ?: payment?.personId ?: category?.defaultPersonId ?: categories.firstOrNull { it.id == category?.parentId }?.defaultPersonId,
        accountId = cardAccountId ?: merchant?.lastAccountId ?: payment?.accountId ?: defaultAccountId,
        isKnown = confirmed != null && remembered != null,
        isSuggested = category != null && !(confirmed != null && remembered != null),
    )
}

/** BRAVO SUPERMARKET reads as Bravo Supermarket, until the user names it something else. */
fun prettyMerchant(raw: String): String =
    raw.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }

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

private fun wordsIn(text: String): Set<String> = text.lowercase(Locale.ROOT).split(Regex("""[^\p{L}\d]+""")).filter { it.isNotEmpty() }.toSet()

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
