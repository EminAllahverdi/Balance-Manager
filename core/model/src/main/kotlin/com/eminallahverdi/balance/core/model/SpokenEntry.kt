package com.eminallahverdi.balance.core.model

import java.text.Normalizer
import java.util.Locale

/** The languages a sentence can be spoken in. Each reads numbers in its own words. */
enum class SpokenLanguage(val tag: String, val label: String) {
    ENGLISH("en-US", "English"),
    AZERBAIJANI("az-AZ", "Azərbaycanca"),
}

/**
 * What a spoken sentence named, matched against what the user already has. Whatever it didn't name stays null, so quick
 * add fills it in as it always does. [categoryId] can be a sub-category.
 */
data class SpokenEntry(
    val amount: Money? = null,
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val categoryId: Long? = null,
    val personId: Long? = null,
    val accountId: Long? = null,
    val merchant: String? = null,
    /** 1 for "yesterday", 0 for "today", null when no day was said. */
    val daysAgo: Int? = null,
)

/** What a sentence is matched against: everything the user has named. */
data class SpokenNames(
    val categories: List<Category>,
    val people: List<Person>,
    val accounts: List<Account>,
    val merchants: List<Merchant>,
)

/**
 * Reads "15.40 Bravo groceries for Family from Card" or "Bravodan 25 manat 50 qəpik, Sam üçün, nağd". Recognisers write
 * most numbers as digits, so those come first, with number words as a fallback. Names match whole words, forgiving one
 * wrong letter in a long word, plurals, and Azerbaijani endings, so Bravodan is Bravo and kartla is kart.
 */
fun parseSpokenEntry(text: String, names: SpokenNames, language: SpokenLanguage): SpokenEntry {
    val words = tokens(text, language).toMutableList()
    val amount = takeAmount(words, language)
    val daysAgo = takeDay(words)
    val incomeSaid = words.any { it.folded in INCOME_WORDS }

    val accounts = names.accounts.filterNot { it.isArchived }
    val account = takeBest(words, accounts) { it.name } ?: accountByType(words, accounts)
    val people = names.people.filterNot { it.isArchived }
    val person = takeBest(words, people) { it.name } ?: if (words.any { it.folded in ME_WORDS }) people.minByOrNull { it.sortOrder } else null

    val categories = names.categories.filterNot { it.isArchived }
    // A sub-category says more than its parent, so it's tried first.
    val category = takeBest(words, categories.sortedByDescending { it.parentId != null }, partial = true) { it.name }
    val known = takeBest(words, names.merchants.sortedByDescending { it.useCount }) { it.name }
    val merchant = known?.name ?: newMerchant(words, language)

    val byMerchant = known?.let { m -> categories.firstOrNull { it.id == m.lastCategoryId } }
    val byHint = if (category == null && byMerchant == null && !incomeSaid) categoryFromWords(words.flatMap { listOf(it.lower, it.folded) }.toSet(), categories) else null
    val chosen = category ?: byMerchant ?: byHint
    val kind = when {
        chosen?.type == CategoryType.INCOME -> TransactionKind.INCOME
        chosen == null && incomeSaid -> TransactionKind.INCOME
        else -> TransactionKind.EXPENSE
    }
    val parent = chosen?.parentId?.let { id -> categories.firstOrNull { it.id == id } }
    return SpokenEntry(
        amount = amount,
        kind = kind,
        categoryId = chosen?.id,
        personId = person?.id ?: known?.lastPersonId?.takeIf { category == null } ?: chosen?.defaultPersonId ?: parent?.defaultPersonId,
        accountId = account?.id ?: known?.lastAccountId,
        merchant = merchant,
        daysAgo = daysAgo,
    )
}

/** A word as it was heard, lowercase, and folded to plain Latin letters so ə is e and ş is s. */
private class Word(val lower: String) {
    val folded: String = fold(lower)
}

private fun fold(text: String): String = Normalizer.normalize(
    text.replace('ə', 'e').replace('ı', 'i').replace('Ə', 'e'),
    Normalizer.Form.NFD,
).replace(Regex("""\p{Mn}+"""), "")

private fun tokens(text: String, language: SpokenLanguage): List<Word> {
    val lower = text.lowercase(Locale.forLanguageTag(language.tag))
        .replace(Regex("""(\d),(\d{3})(?!\d)"""), "$1$2") // 1,500 is fifteen hundred
        .replace(Regex("""(\d),(\d{1,2})(?!\d)"""), "$1.$2") // 15,40 is fifteen forty
        .replace("₼", " manat ").replace("$", " ")
    return Regex("""\d+(?:\.\d+)?|[\p{L}]+(?:['’-][\p{L}]+)*""").findAll(lower).map { Word(it.value.replace(Regex("""['’-]"""), "")) }.toList()
}

private val CURRENCY = setOf("manat", "manats", "azn", "dollar", "dollars", "man")
private val MINOR_UNITS = setOf("qepik", "qepiy", "gepik", "kepik", "cent", "cents", "kopek", "qapik")

/** Takes the first amount said, as digits or words, and the currency words around it. */
private fun takeAmount(words: MutableList<Word>, language: SpokenLanguage): Money? {
    val numbers = numberRuns(words, language)
    val first = numbers.firstOrNull() ?: return null
    var end = first.last
    var minor = 0L
    var major = first.value
    val decimal = first.decimal
    if (decimal != null) {
        major = decimal.first
        minor = decimal.second
    } else {
        // "15 manat 40 qəpik", "15 manat 40", "fifteen forty": the second number is the qəpik.
        var next = end + 1
        if (words.getOrNull(next)?.folded in CURRENCY) next++
        if (words.getOrNull(next)?.folded == "and" || words.getOrNull(next)?.folded == "ve") next++
        val second = numbers.getOrNull(1)?.takeIf { it.first == next && it.decimal == null && it.value in 1..99 }
        if (second != null) {
            minor = second.value
            end = second.last
        }
    }
    if (words.getOrNull(end + 1)?.folded in CURRENCY + MINOR_UNITS) end++
    if (words.getOrNull(end + 1)?.folded in MINOR_UNITS) end++
    val start = first.first.let { if (it > 0 && words[it - 1].folded in CURRENCY) it - 1 else it }
    repeat(end - start + 1) { words.removeAt(start) }
    return Money(major * 100 + minor).takeIf { it.minor > 0 }
}

/** A run of words that make one number: "15.40", or "twenty five", or "iyirmi beş". */
private class NumberRun(val first: Int, val last: Int, val value: Long, val decimal: Pair<Long, Long>?)

private fun numberRuns(words: List<Word>, language: SpokenLanguage): List<NumberRun> {
    val runs = mutableListOf<NumberRun>()
    val units = if (language == SpokenLanguage.AZERBAIJANI) AZ_NUMBERS else EN_NUMBERS
    var i = 0
    while (i < words.size) {
        val w = words[i].folded
        if (w.first().isDigit()) {
            val parts = w.split('.')
            val decimal = parts.getOrNull(1)?.let { parts[0].toLong() to it.padEnd(2, '0').take(2).toLong() }
            runs += NumberRun(i, i, parts[0].toLongOrNull() ?: 0, decimal)
            i++
            continue
        }
        if (w !in units) {
            i++
            continue
        }
        var total = 0L
        var current = 0L
        val start = i
        while (i < words.size) {
            val value = units[words[i].folded] ?: break
            when (value) {
                HUNDRED -> current = (if (current == 0L) 1 else current) * HUNDRED
                THOUSAND -> {
                    total += (if (current == 0L) 1 else current) * THOUSAND
                    current = 0
                }
                else -> {
                    // "fifteen forty" is two numbers: a word that can't add to the last one starts the next.
                    if (current % HUNDRED != 0L && value < HUNDRED && (current % 10 != 0L || value >= 10)) break
                    current += value
                }
            }
            i++
        }
        runs += NumberRun(start, i - 1, total + current, null)
    }
    return runs
}

private const val HUNDRED = 100L
private const val THOUSAND = 1000L

private val EN_NUMBERS = mapOf(
    "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L, "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L,
    "ten" to 10L, "eleven" to 11L, "twelve" to 12L, "thirteen" to 13L, "fourteen" to 14L, "fifteen" to 15L, "sixteen" to 16L,
    "seventeen" to 17L, "eighteen" to 18L, "nineteen" to 19L, "twenty" to 20L, "thirty" to 30L, "forty" to 40L, "fifty" to 50L,
    "sixty" to 60L, "seventy" to 70L, "eighty" to 80L, "ninety" to 90L, "hundred" to HUNDRED, "thousand" to THOUSAND,
)

private val AZ_NUMBERS = mapOf(
    "bir" to 1L, "iki" to 2L, "uc" to 3L, "dord" to 4L, "bes" to 5L, "alti" to 6L, "yeddi" to 7L, "sekkiz" to 8L, "doqquz" to 9L,
    "on" to 10L, "iyirmi" to 20L, "otuz" to 30L, "qirx" to 40L, "elli" to 50L, "altmis" to 60L, "yetmis" to 70L, "seksen" to 80L,
    "doxsan" to 90L, "yuz" to HUNDRED, "min" to THOUSAND,
)

private val YESTERDAY = setOf("yesterday", "dunen")
private val TODAY = setOf("today", "bugun")

private fun takeDay(words: MutableList<Word>): Int? {
    val index = words.indexOfFirst { it.folded in YESTERDAY || it.folded in TODAY }
    if (index < 0) return null
    return if (words.removeAt(index).folded in YESTERDAY) 1 else 0
}

private val INCOME_WORDS = setOf("income", "salary", "paycheck", "received", "earned", "refund", "cashback", "gelir", "maas", "medaxil", "emekhaqqi")
private val ME_WORDS = setOf("me", "myself", "mene", "ozume", "ozum")
private val CASH_WORDS = setOf("cash", "nagd", "nagdi")
private val CARD_WORDS = setOf("card", "kart", "kartla", "kartdan")

/** "Cash" or "kartla", when no account was named: the first account of that kind. */
private fun accountByType(words: MutableList<Word>, accounts: List<Account>): Account? {
    val cash = words.indexOfFirst { it.folded in CASH_WORDS }
    if (cash >= 0) accounts.firstOrNull { it.type == AccountType.CASH }?.let { words.removeAt(cash); return it }
    val card = words.indexOfFirst { it.folded in CARD_WORDS }
    if (card >= 0) accounts.firstOrNull { it.type == AccountType.DEBIT_CARD || it.type == AccountType.CREDIT_CARD }?.let { words.removeAt(card); return it }
    return null
}

/** Endings Azerbaijani adds to a name: Bravodan, Bravoda, Bravoya, Samin, Samla, Sam üçün is separate. */
private val AZ_ENDINGS = listOf("dan", "den", "da", "de", "ya", "ye", "a", "e", "nin", "nun", "in", "un", "la", "le", "ye", "y", "n")

/** Whether a heard word is a word of a name, allowing for plurals, endings and one wrong letter in a long word. */
private fun Word.isWord(name: String): Boolean {
    val w = folded
    if (w == name) return true
    if (name.length >= 3 && w.startsWith(name) && w.removePrefix(name) in AZ_ENDINGS) return true
    if (stem(w) == stem(name)) return true
    return name.length >= LONG_WORD && w.length >= LONG_WORD && editDistance(w, name) <= 1
}

private const val LONG_WORD = 5

private fun stem(word: String): String = when {
    word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
    word.endsWith("es") && word.length > 4 -> word.dropLast(2)
    word.endsWith("s") && word.length > 3 -> word.dropLast(1)
    else -> word
}

private fun editDistance(a: String, b: String): Int {
    if (kotlin.math.abs(a.length - b.length) > 1) return 2
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val row = IntArray(b.length + 1)
        row[0] = i
        for (j in 1..b.length) row[j] = minOf(previous[j] + 1, row[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        previous = row
    }
    return previous[b.length]
}

/** Words a name can leave out when it's spoken, like the & in Cafe & Restaurants. */
private val FILLER = setOf("and", "ve", "the", "of")

/**
 * The item whose name is said, the longest name first so "Kapital Bank" wins over "Bank". With [partial], one long word of
 * a name is enough when no whole name was said, so "restaurant" finds Cafe & Restaurants. The words are taken out, so the
 * same word can't name two things.
 */
private fun <T> takeBest(words: MutableList<Word>, items: List<T>, partial: Boolean = false, name: (T) -> String): T? {
    var best: Triple<T, Int, Int>? = null // item, start, length
    val named = items.map { item -> item to fold(name(item).lowercase(Locale.ROOT)).split(Regex("""[^\p{L}\d]+""")).filter { it.isNotEmpty() && it !in FILLER } }
    for ((item, parts) in named) {
        if (parts.isEmpty() || parts.none { it.length >= 3 }) continue
        for (start in words.indices) {
            if (start + parts.size > words.size) break
            if (parts.indices.all { words[start + it].isWord(parts[it]) }) {
                if (best == null || parts.size > best.third) best = Triple(item, start, parts.size)
                break
            }
        }
    }
    if (best == null && partial) {
        best = named.firstNotNullOfOrNull { (item, parts) ->
            parts.filter { it.length >= LONG_WORD }.firstNotNullOfOrNull { part ->
                words.indexOfFirst { it.isWord(part) }.takeIf { it >= 0 }?.let { Triple(item, it, 1) }
            }
        }
    }
    val (item, start, length) = best ?: return null
    repeat(length) { words.removeAt(start) }
    return item
}

private val EN_PLACE_WORDS = setOf("at", "from", "in")
private val STOP_WORDS = setOf(
    "for", "from", "at", "in", "on", "to", "the", "a", "an", "my", "with", "by", "paid", "spent", "bought", "buy", "pay", "and",
    "ucun", "ile", "men", "bu", "bir", "ve", "odedim", "aldim", "xercledim",
) + CURRENCY + MINOR_UNITS + CASH_WORDS + CARD_WORDS + ME_WORDS + INCOME_WORDS

/**
 * A shop not met before: the word after "at" or "from" in English, or a word with an Azerbaijani "from" or "at" ending,
 * like Woltdan. Without either, nothing is guessed.
 */
private fun newMerchant(words: List<Word>, language: SpokenLanguage): String? {
    val word = when (language) {
        SpokenLanguage.ENGLISH -> words.zipWithNext().firstOrNull { (a, b) -> a.folded in EN_PLACE_WORDS && b.folded !in STOP_WORDS && b.folded.length >= 2 }?.second?.lower
        SpokenLanguage.AZERBAIJANI -> words.firstNotNullOfOrNull { w ->
            listOf("dan", "den", "da", "de").firstOrNull { w.lower.endsWith(it) && w.lower.length - it.length >= 3 }
                ?.let { ending -> w.lower.dropLast(ending.length) }
                ?.takeIf { fold(it) !in STOP_WORDS }
        }
    } ?: return null
    return word.replaceFirstChar { it.titlecase(Locale.ROOT) }
}
