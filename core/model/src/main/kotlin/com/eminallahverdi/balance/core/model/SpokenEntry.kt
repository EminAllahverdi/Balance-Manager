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
    /** A debt, when the words for one were said: lent, borrowed, paid back, paid for me or for others. */
    val debt: DebtType? = null,
    /** Who the debt is with, as said. Several for a bill paid for others: "Sam and Alex". */
    val counterparty: String? = null,
)

/** What a sentence is matched against: everything the user has named. */
data class SpokenNames(
    val categories: List<Category>,
    val people: List<Person>,
    val accounts: List<Account>,
    val merchants: List<Merchant>,
    /** Entries per category id, so when two share a name, like Fuel under Car and under Motorbike, the one in use wins. */
    val categoryUse: Map<Long, Int> = emptyMap(),
    /** People there are debts with, so "Samə borc verdim" finds Sam, whatever ending the sentence gave the name. */
    val debtNames: List<String> = emptyList(),
)

/**
 * Reads "15.40 Bravo groceries for Family from Card" or "Bravodan 25 manat 50 qəpik, Sam üçün, nağd". Recognisers write
 * most numbers as digits, so those come first, with number words as a fallback. Names match whole words, forgiving one
 * wrong letter in a long word, plurals, and Azerbaijani endings, so Bravodan is Bravo, kartla is kart and yanacağa is
 * yanacaq. A category can also be said in the other language, so yanacaq finds Fuel.
 */
fun parseSpokenEntry(text: String, names: SpokenNames, language: SpokenLanguage): SpokenEntry {
    val words = tokens(text, language).toMutableList()
    val amount = takeAmount(words, language)
    val daysAgo = takeDay(words)
    val incomeSaid = words.any { it.isOneOf(INCOME_WORDS) }

    // A debt first: its name is someone outside the household, so it's taken before people are looked for.
    val debt = debtSaid(words)
    if (debt != null) {
        val who = takeCounterparty(words, text, debt, names.debtNames)
        val accounts = names.accounts.filterNot { it.isArchived }
        val account = takeBest(words, accounts) { it.name } ?: accountByType(words, accounts)
        val categories = names.categories.filterNot { it.isArchived }
        // Only a bill, or a share of one, is in a category: lending and paying back aren't spending.
        val category = if (debt.hasCategory) takeCategory(words, categories, names.categoryUse) else null
        return SpokenEntry(amount = amount, kind = TransactionKind.DEBT, categoryId = category?.id, accountId = account?.id, daysAgo = daysAgo, debt = debt, counterparty = who)
    }

    val accounts = names.accounts.filterNot { it.isArchived }
    val account = takeBest(words, accounts) { it.name } ?: accountByType(words, accounts)
    val people = names.people.filterNot { it.isArchived }
    val person = takeBest(words, people) { it.name } ?: if (words.any { it.isOneOf(ME_WORDS) }) people.minByOrNull { it.sortOrder } else null

    val categories = names.categories.filterNot { it.isArchived }
    val category = takeCategory(words, categories, names.categoryUse)
    val known = takeBest(words, names.merchants.sortedByDescending { it.useCount }) { it.name }
    val merchant = known?.name ?: newMerchant(words, language)

    val byMerchant = known?.let { m -> categories.firstOrNull { it.id == m.lastCategoryId } }
    val byHint = if (category == null && byMerchant == null && !incomeSaid) {
        categoryFromWords(words.flatMap { listOf(it.lower, it.folded) }.toSet(), categories)?.let { mostUsedNamed(it, categories, names.categoryUse) }
    } else {
        null
    }
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
private class Word(val lower: String, val azerbaijani: Boolean) {
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
    val azerbaijani = language == SpokenLanguage.AZERBAIJANI
    return Regex("""\d+(?:\.\d+)?|[\p{L}]+(?:['’-][\p{L}]+)*""").findAll(lower).flatMap { match ->
        // Wolt-dan and Sam's are one word, kafe-restoran and top-up are two.
        match.value.replace(Regex("""['’]"""), "").split('-').fold(mutableListOf<String>()) { pieces, piece ->
            if (pieces.isNotEmpty() && isEndings(fold(piece))) pieces[pieces.lastIndex] += piece else pieces += piece
            pieces
        }
    }.map { Word(it, azerbaijani) }.toList()
}

private val CURRENCY = setOf("manat", "manats", "manatliq", "manatlik", "azn", "dollar", "dollars", "man")
private val MINOR_UNITS = setOf("qepik", "qepiy", "gepik", "kepik", "cent", "cents", "kopek", "qapik")

/**
 * Takes the amount said, as digits or words, and the currency words around it. The number said with manat is the amount,
 * so "bir kofe 3 manat" is 3. Without one, the first number in digits, as recognisers write amounts that way.
 */
private fun takeAmount(words: MutableList<Word>, language: SpokenLanguage): Money? {
    val numbers = numberRuns(words, language)
    val at = numbers.indexOfFirst { words.getOrNull(it.last + 1)?.isOneOf(CURRENCY) == true }.takeIf { it >= 0 }
        ?: numbers.indexOfFirst { it.digits }.takeIf { it >= 0 }
        ?: 0
    val first = numbers.getOrNull(at) ?: return null
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
        if (words.getOrNull(next)?.isOneOf(CURRENCY) == true) next++
        if (words.getOrNull(next)?.folded == "and" || words.getOrNull(next)?.folded == "ve") next++
        val second = numbers.getOrNull(at + 1)?.takeIf { it.first == next && it.decimal == null && it.value in 1..99 }
        if (second != null) {
            minor = second.value
            end = second.last
        }
    }
    if (words.getOrNull(end + 1)?.isOneOf(CURRENCY + MINOR_UNITS) == true) end++
    if (words.getOrNull(end + 1)?.isOneOf(MINOR_UNITS) == true) end++
    val start = first.first.let { if (it > 0 && words[it - 1].isOneOf(CURRENCY)) it - 1 else it }
    repeat(end - start + 1) { words.removeAt(start) }
    return Money(major * 100 + minor).takeIf { it.minor > 0 }
}

/** A run of words that make one number: "15.40", or "twenty five", or "iyirmi beş". */
private class NumberRun(val first: Int, val last: Int, val value: Long, val decimal: Pair<Long, Long>?, val digits: Boolean)

private fun numberRuns(words: List<Word>, language: SpokenLanguage): List<NumberRun> {
    val runs = mutableListOf<NumberRun>()
    val units = if (language == SpokenLanguage.AZERBAIJANI) AZ_NUMBERS else EN_NUMBERS
    var i = 0
    while (i < words.size) {
        val w = words[i].folded
        if (w.first().isDigit()) {
            val parts = w.split('.')
            val decimal = parts.getOrNull(1)?.let { parts[0].toLong() to it.padEnd(2, '0').take(2).toLong() }
            runs += NumberRun(i, i, parts[0].toLongOrNull() ?: 0, decimal, digits = true)
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
        runs += NumberRun(start, i - 1, total + current, null, digits = false)
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
    val index = words.indexOfFirst { it.isOneOf(YESTERDAY) || it.isOneOf(TODAY) }
    if (index < 0) return null
    return if (words.removeAt(index).isOneOf(YESTERDAY)) 1 else 0
}

private val INCOME_WORDS = setOf("income", "salary", "paycheck", "received", "earned", "refund", "cashback", "gelir", "maas", "medaxil", "emekhaqqi")
private val ME_WORDS = setOf("me", "myself", "mene", "menim", "ozume", "ozum")
private val CASH_WORDS = setOf("cash", "nagd", "nagdi")
private val CARD_WORDS = setOf("card", "kart", "kartla", "kartdan")

/** "Cash" or "kartla", when no account was named: the first account of that kind. */
private fun accountByType(words: MutableList<Word>, accounts: List<Account>): Account? {
    val cash = words.indexOfFirst { it.isOneOf(CASH_WORDS) }
    if (cash >= 0) accounts.firstOrNull { it.type == AccountType.CASH }?.let { words.removeAt(cash); return it }
    val card = words.indexOfFirst { it.isOneOf(CARD_WORDS) }
    if (card >= 0) accounts.firstOrNull { it.type == AccountType.DEBIT_CARD || it.type == AccountType.CREDIT_CARD }?.let { words.removeAt(card); return it }
    return null
}

/** Whether the word is one of [set], or in Azerbaijani one of them with endings, as maaşımı is maaş. */
private fun Word.isOneOf(set: Set<String>): Boolean = folded in set || azerbaijani && set.any { it.length >= 3 && inflects(folded, it) }

/**
 * Endings Azerbaijani adds to a noun, folded to plain letters: plural, whose, and where to or from. Up to three come in a
 * row, as in maşınımızdan.
 */
private val AZ_ENDINGS = listOf(
    "lar", "ler",
    "imiz", "umuz", "iniz", "unuz", "miz", "muz", "niz", "nuz", "im", "um", "m",
    "si", "su", "i", "u",
    "ndan", "nden", "dan", "den", "nda", "nde", "da", "de",
    "nin", "nun", "in", "un", "n", "ni", "nu",
    "ya", "ye", "na", "ne", "a", "e",
    "yla", "yle", "la", "le",
)

private const val MOST_ENDINGS = 3
private const val VOWELS = "aeiou"

private fun isEndings(rest: String, depth: Int = 0): Boolean =
    rest.isEmpty() || depth < MOST_ENDINGS && AZ_ENDINGS.any { rest.startsWith(it) && isEndings(rest.substring(it.length), depth + 1) }

/**
 * Whether [heard] is [name] with Azerbaijani endings, as Bravodan or maşınımı. A last q or k softens before a vowel, so
 * yanacağa is yanacaq and çörəyə is çörək.
 */
private fun inflects(heard: String, name: String): Boolean {
    if (heard.length > name.length && heard.startsWith(name) && isEndings(heard.substring(name.length))) return true
    val soft = when (name.last()) {
        'q' -> name.dropLast(1) + "g"
        'k' -> name.dropLast(1) + "y"
        else -> return false
    }
    return heard.length > soft.length && heard.startsWith(soft) && heard[soft.length] in VOWELS && isEndings(heard.substring(soft.length))
}

/** How well a heard word fits a word of a name. Higher is better. */
private const val NO_MATCH = 0
private const val NEAR = 1 // one letter off, in a long word
private const val TRANSLATED = 2 // the same thing in the other language
private const val INFLECTED = 3 // a plural, or an Azerbaijani ending
private const val EXACT = 4

private fun Word.fit(part: String): Int = when {
    folded == part -> EXACT
    stem(folded) == stem(part) -> INFLECTED
    azerbaijani && part.length >= 3 && inflects(folded, part) -> INFLECTED
    isNear(part) -> NEAR
    else -> NO_MATCH
}

private fun Word.fit(part: String, translate: Boolean): Int {
    val own = fit(part)
    if (!translate || own >= TRANSLATED) return own
    return if (otherWords(part).any { fit(it) >= INFLECTED }) TRANSLATED else own
}

/** One wrong letter in a long word, with or without Azerbaijani endings after it. */
private fun Word.isNear(part: String): Boolean {
    if (part.length < LONG_WORD || folded.length < LONG_WORD) return false
    if (editDistance(folded, part) <= 1) return true
    if (!azerbaijani) return false
    return (part.length - 1..part.length + 1).any { cut ->
        cut in 1 until folded.length && isEndings(folded.substring(cut)) && editDistance(folded.substring(0, cut), part) <= 1
    }
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

/**
 * Words for the same thing in English and Azerbaijani, folded, so "yanacağa" finds a category called Fuel and "groceries"
 * one called Ərzaq. Plain words only: no shop or person is named here.
 */
private val SAME_THING = listOf(
    setOf("groceries", "grocery", "erzaq"),
    setOf("fuel", "petrol", "benzin", "yanacaq"),
    setOf("car", "masin", "avtomobil"),
    setOf("motorbike", "motorcycle", "moto", "motosiklet"),
    setOf("service", "servis", "temir"),
    setOf("repair", "repairs", "temir"),
    setOf("parking", "dayanacaq", "parkovka"),
    setOf("taxi", "taksi"),
    setOf("transport", "neqliyyat"),
    setOf("bus", "avtobus"),
    setOf("pharmacy", "medicine", "aptek", "derman"),
    setOf("health", "saglamliq"),
    setOf("doctor", "hekim", "doktor"),
    setOf("dentist", "stomatoloq"),
    setOf("restaurant", "restaurants", "restoran"),
    setOf("cafe", "kafe"),
    setOf("coffee", "kofe", "qehve"),
    setOf("lunch", "nahar"),
    setOf("delivery", "catdirilma"),
    setOf("bread", "corek"),
    setOf("fruit", "fruits", "meyve"),
    setOf("vegetables", "terevez"),
    setOf("sweets", "sirniyyat"),
    setOf("cigarettes", "siqaret"),
    setOf("rent", "kiraye"),
    setOf("utilities", "kommunal"),
    setOf("electricity", "isiq", "elektrik"),
    setOf("phone", "mobile", "telefon", "mobil"),
    setOf("clothes", "clothing", "geyim", "paltar"),
    setOf("shoes", "ayaqqabi"),
    setOf("education", "tehsil"),
    setOf("school", "mekteb"),
    setOf("course", "courses", "kurs"),
    setOf("books", "book", "kitab"),
    setOf("gifts", "gift", "hediyye"),
    setOf("kids", "children", "child", "usaq"),
    setOf("household", "meiset"),
    setOf("furniture", "mebel"),
    setOf("entertainment", "eylence"),
    setOf("cinema", "movies", "kino"),
    setOf("travel", "trip", "seyahet"),
    setOf("hotel", "otel"),
    setOf("tickets", "ticket", "bilet"),
    setOf("sport", "sports", "idman"),
    setOf("beauty", "gozellik"),
    setOf("haircut", "barber", "berber"),
    setOf("pets", "pet", "heyvan"),
    setOf("salary", "maas", "emekhaqqi"),
    setOf("bonus", "mukafat"),
    setOf("loan", "kredit"),
    setOf("debt", "borc"),
    setOf("tax", "taxes", "vergi"),
    setOf("insurance", "sigorta"),
    setOf("subscriptions", "subscription", "abune", "abunelik"),
    setOf("charity", "sedeqe", "xeyriyye"),
    setOf("shopping", "alisveris"),
    setOf("home", "house", "ev"),
    setOf("family", "aile"),
)

private val OTHER_WORDS: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
    for (group in SAME_THING) for (word in group) getOrPut(word) { mutableSetOf() } += group - word
}

private fun otherWords(part: String): Set<String> = OTHER_WORDS[part] ?: OTHER_WORDS[stem(part)] ?: emptySet()

/** Words a name can leave out when it's spoken, like the & in Cafe & Restaurants. */
private val FILLER = setOf("and", "ve", "the", "of")

private fun partsOf(name: String): List<String> =
    fold(name.lowercase(Locale.ROOT)).split(Regex("""[^\p{L}\d]+""")).filter { it.isNotEmpty() && it !in FILLER }

/** Where a name was said: the item's place in its list, the words it covers, how well they fit, and whether all of it was said. */
private class Found<T>(val item: T, val index: Int, val start: Int, val length: Int, val fit: Int, val whole: Boolean) {
    val end: Int get() = start + length

    /** Said as written and said with endings count the same here, so "motosikletə" names Motosiklet as firmly as "motosiklet". */
    val level: Int get() = if (whole) minOf(fit, INFLECTED) else NO_MATCH

    fun overlaps(other: Found<*>) = start < other.end && other.start < end
}

/** Every item whose name was said, each at the place it fits best. With [partial], one long word of a longer name will do. */
private fun <T> find(words: List<Word>, items: List<T>, translate: Boolean, partial: Boolean, name: (T) -> String): List<Found<T>> =
    items.mapIndexedNotNull { index, item ->
        val parts = partsOf(name(item))
        if (parts.none { it.length >= 3 }) return@mapIndexedNotNull null
        wholeName(words, parts, translate)?.let { (start, fit) -> Found(item, index, start, parts.size, fit, whole = true) }
            ?: if (partial && parts.size > 1) partOfName(words, parts, translate)?.let { (start, fit) -> Found(item, index, start, 1, fit, whole = false) } else null
    }

/** Where every word of the name is said in a row, and how well the weakest one fits. */
private fun wholeName(words: List<Word>, parts: List<String>, translate: Boolean): Pair<Int, Int>? {
    var best: Pair<Int, Int>? = null
    for (start in 0..words.size - parts.size) {
        val fit = parts.indices.minOf { words[start + it].fit(parts[it], translate) }
        if (fit > (best?.second ?: NO_MATCH)) best = start to fit
    }
    return best
}

/** One long word of a longer name, as "restaurant" for Cafe & Restaurants. */
private fun partOfName(words: List<Word>, parts: List<String>, translate: Boolean): Pair<Int, Int>? =
    parts.filter { it.length >= LONG_WORD }
        .flatMap { part -> words.indices.map { it to words[it].fit(part, translate) } }
        .filter { it.second > NO_MATCH }
        .maxByOrNull { it.second }

/** A name said as written beats another heard in the same words only with an ending, a letter off or translated. */
private fun <T> List<Found<T>>.withoutWeaker(): List<Found<T>> =
    filterNot { f -> any { o -> o.whole && f.whole && o.start == f.start && o.length == f.length && o.fit > f.fit } }

private fun MutableList<Word>.takeOut(found: Found<*>) = repeat(found.length) { removeAt(found.start) }

/**
 * The item whose name is said, the longest name first so "Kapital Bank" wins over "Bank", then the best fit, then the first
 * in the list. Its words are taken out, so the same word can't name two things.
 */
private fun <T> takeBest(words: MutableList<Word>, items: List<T>, name: (T) -> String): T? {
    val best = find(words, items, translate = false, partial = false, name).withoutWeaker()
        .maxWithOrNull(compareBy<Found<T>>({ it.length }, { it.fit }, { -it.index })) ?: return null
    words.takeOut(best)
    return best.item
}

/**
 * The category said. A sub-category said with its parent wins, so "motorbike fuel" is Fuel under Motorbike even when Car has
 * a Fuel too. Otherwise the one that covers the most words, a whole name said rather than translated or a letter off, a main
 * category over a sub-category of another, and then the one used most.
 */
private fun takeCategory(words: MutableList<Word>, categories: List<Category>, use: Map<Long, Int>): Category? {
    val said = find(words, categories, translate = true, partial = true) { it.name }.withoutWeaker()

    class Choice(val found: Found<Category>, val parent: Found<Category>?) {
        val covers = found.length + (parent?.length ?: 0)
        val level = minOf(found.level, parent?.level ?: found.level)
        val rank = when {
            parent != null -> 2
            found.item.parentId == null -> 1
            else -> 0
        }
    }
    val best = said
        .map { f -> Choice(f, f.item.parentId?.let { id -> said.firstOrNull { it.item.id == id && !it.overlaps(f) } }) }
        .maxWithOrNull(compareBy<Choice>({ it.covers }, { it.level }, { it.rank }, { use[it.found.item.id] ?: 0 }, { -it.found.index }))
        ?: return null
    listOfNotNull(best.found, best.parent).sortedByDescending { it.start }.forEach { words.takeOut(it) }
    return best.found.item
}

/** Of the categories called what a hint found, the one used most. */
private fun mostUsedNamed(hinted: Category, categories: List<Category>, use: Map<Long, Int>): Category =
    categories.filter { it.type == hinted.type && it.name.equals(hinted.name, ignoreCase = true) }.maxByOrNull { use[it.id] ?: 0 } ?: hinted

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
            listOf("dan", "den", "da", "de").firstOrNull { w.folded.endsWith(it) && w.folded.length - it.length >= 3 }
                ?.let { ending -> w.lower.dropLast(ending.length) }
                ?.takeIf { fold(it) !in STOP_WORDS }
        }
    } ?: return null
    return word.replaceFirstChar { it.titlecase(Locale.ROOT) }
}

/**
 * The words for each kind of debt, in English and Azerbaijani, over the folded words of the sentence. They're tried in this
 * order, so "paid for me" is a share someone paid before "paid for" can be a bill the user paid.
 */
private val DEBT_WORDS: List<Pair<DebtType, Regex>> = listOf(
    DebtType.PAID_BACK to Regex("""\b(?:paid|pay|pays|gave|give|gives|sent|send)(?: \w+){0,2} back\b|\breturned\b|\bqaytar\w*"""),
    DebtType.PAID_FOR_ME to Regex("""\b(?:paid|pays|covered|treated)(?: for)? me\b|\bmenim (?:ucun|yerime|evezime)\b|\bevezime\b"""),
    DebtType.PAID_FOR_OTHERS to Regex("""\bpaid for\b|\bucun odedim\b|\bhami ucun\b"""),
    DebtType.LENT to Regex("""\b(?:lent|lend|loaned)\b|\bborc(?:a)? ver\w*"""),
    DebtType.BORROWED to Regex("""\b(?:borrowed|borrow)\b|\bborc (?:al|got)\w*"""),
)

private fun debtSaid(words: List<Word>): DebtType? {
    val said = words.joinToString(" ") { it.folded }
    return DEBT_WORDS.firstOrNull { (_, pattern) -> pattern.containsMatchIn(said) }?.first
}

/** Words that are never someone's name, whatever their place in the sentence. */
private val NOT_A_NAME = setOf(
    "i", "me", "my", "we", "us", "to", "from", "the", "a", "an", "back", "paid", "pay", "gave", "sent", "and", "for", "of", "money", "lunch",
    "at", "in", "on", "with", "by", "today", "yesterday", "dinner", "breakfast", "coffee", "everyone", "them", "him", "her", "our", "their",
    "men", "mene", "meni", "menim", "biz", "pul", "pulu", "borc", "ve", "ile", "da", "de", "bu", "o", "bir", "geri", "ucun",
) + CURRENCY + MINOR_UNITS

/**
 * Who a debt is with. Someone there are debts with already is found whatever ending the sentence gave them. Otherwise
 * it's the word in the place the language puts them: after "lent" or "from", before "paid back" or "qaytardı", in the
 * dative before "borc verdim" (Samə), in the ablative before "borc aldım" (Samdan), and every name for a bill.
 */
private fun takeCounterparty(words: MutableList<Word>, text: String, debt: DebtType, known: List<String>): String? {
    val found = generateSequence { takeBest(words, known) { it } }.take(MOST_NAMES).toList()
    if (found.isNotEmpty()) return if (debt == DebtType.PAID_FOR_OTHERS) joinNames(found) else found.first()
    val heard = Regex("""[\p{L}][\p{L}'’-]*|\d+""").findAll(text).map { it.value }.toList()
    val lower = heard.map { fold(it.lowercase(Locale.ROOT)) }
    fun at(i: Int): String? = heard.getOrNull(i)?.takeIf { lower[i] !in NOT_A_NAME && !lower[i].first().isDigit() }
    fun firstAfter(i: Int): String? = (i + 1 until heard.size).firstNotNullOfOrNull { at(it) }
    fun lastBefore(i: Int): String? = (i - 1 downTo 0).firstNotNullOfOrNull { at(it) }
    fun index(vararg keys: String): Int = lower.indexOfFirst { word -> keys.any { word == it || it.endsWith("*") && word.startsWith(it.dropLast(1)) } }
    val name = when (debt) {
        DebtType.LENT -> index("lent", "lend", "loaned").takeIf { it >= 0 }?.let { firstAfter(it) }
            ?: index("borc", "borca").takeIf { it >= 0 }?.let { lastBefore(it) }?.withoutEnding(listOf("ya", "ye", "yə"), listOf("a", "e", "ə"))
        DebtType.BORROWED -> index("from").takeIf { it >= 0 }?.let { firstAfter(it) }
            ?: index("borc").takeIf { it >= 0 }?.let { lastBefore(it) }?.withoutEnding(listOf("dan", "den", "dən"), emptyList())
        DebtType.PAID_BACK -> index("paid", "pays", "gave", "gives", "sent", "qaytar*").takeIf { it >= 0 }?.let { lastBefore(it) ?: firstAfter(it) }
        DebtType.PAID_FOR_ME -> index("paid", "pays", "covered", "treated", "menim", "evezime").takeIf { it >= 0 }?.let { lastBefore(it) }
        DebtType.PAID_FOR_OTHERS -> return billNames(heard, lower)
    } ?: return null
    return name.replaceFirstChar { it.titlecase(Locale.ROOT) }
}

/** Everyone a bill was paid for: the names after "paid for" in English, or before "üçün" in Azerbaijani. */
private fun billNames(heard: List<String>, lower: List<String>): String? {
    val joins = setOf("and", "ve", ",")
    // "paid for Sam and Alex", or with the amount between: "paid 20 manat for Sam and Alex".
    val paid = lower.indexOf("paid")
    val english = if (paid >= 0) (paid + 1 until minOf(lower.size, paid + BILL_GAP)).firstOrNull { lower[it] == "for" } ?: -1 else -1
    val names = if (english >= 0) {
        (english + 1 until heard.size).asSequence().map { heard[it] to lower[it] }
            .takeWhile { (_, word) -> word in joins || (word !in NOT_A_NAME && !word.first().isDigit()) }
            .filter { (_, word) -> word !in joins }.map { it.first }.toList()
    } else {
        val ucun = lower.indexOf("ucun").takeIf { it > 0 } ?: return null
        (ucun - 1 downTo 0).asSequence().map { heard[it] to lower[it] }
            .takeWhile { (_, word) -> word in joins || (word !in NOT_A_NAME && !word.first().isDigit()) }
            .filter { (_, word) -> word !in joins }.map { it.first }.toList().reversed()
    }
    return names.takeIf { it.isNotEmpty() }?.map { n -> n.replaceFirstChar { it.titlecase(Locale.ROOT) } }?.let(::joinNames)
}

/** "Leylaya" is Leyla and "Samə" is Sam: a vowel-ending name takes a y before the ending, a consonant-ending one doesn't. */
private fun String.withoutEnding(afterVowel: List<String>, afterConsonant: List<String>): String {
    val lower = lowercase(Locale.ROOT)
    afterVowel.firstOrNull { lower.endsWith(it) && length > it.length + 1 }?.let { return dropLast(it.length) }
    afterConsonant.firstOrNull { lower.endsWith(it) && length > it.length + 1 }?.let { return dropLast(it.length) }
    return this
}

private fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names.single()
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}

private const val MOST_NAMES = 6

/** How far after "paid" its "for" can be, with an amount and currency between. */
private const val BILL_GAP = 5
