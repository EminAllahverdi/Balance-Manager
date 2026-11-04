package com.eminallahverdi.balance.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/** What a shop's receipt held, item by item. [fiscalId] is the 12-character code e-kassa prints, so one can't be added twice. */
data class Receipt(
    val id: Long,
    val store: String,
    val date: LocalDate,
    val total: Money,
    val fiscalId: String?,
    val items: List<ReceiptItem>,
)

/** One line of a receipt. [quantityMilli] is thousandths, so 0.756 kg is 756 and 2 pieces are 2000. */
data class ReceiptItem(
    val name: String,
    val quantityMilli: Long,
    val price: Money,
    val total: Money,
    val group: ItemGroup = groupOf(name),
) {
    /** The same product however a till abbreviates or sizes it, so months compare like with like. */
    val key: String get() = itemKey(name)
}

/** Where a grocery item belongs, for a report to say what the money went on. */
enum class ItemGroup(val label: String) {
    DAIRY("Dairy and eggs"),
    MEAT_FISH("Meat and fish"),
    BREAD("Bread and bakery"),
    FRUIT_VEG("Fruit and vegetables"),
    STAPLES("Staples"),
    DRINKS("Drinks"),
    SWEETS("Sweets and snacks"),
    HOUSEHOLD("Household"),
    OTHER("Other"),
}

/**
 * Words that give an item away, in Azerbaijani, Russian and English, written without Azerbaijani letters because names
 * are folded before they're matched. A word matches the start of a word on the receipt, so süd finds Südlü.
 */
private val GROUP_WORDS: List<Pair<ItemGroup, List<String>>> = listOf(
    ItemGroup.DAIRY to listOf("sud", "qatiq", "pendir", "kere", "kefir", "ayran", "yogurt", "smetan", "xama", "kesmik", "yumurta", "молок", "сыр", "кефир", "творог", "сметан", "яйц", "milk", "cheese", "yoghurt", "butter", "egg"),
    ItemGroup.MEAT_FISH to listOf("et ", "toyuq", "mal eti", "qoyun", "kolbasa", "sosiska", "sosis", "baliq", "qiyme", "kotlet", "мяс", "курин", "колбас", "сосиск", "рыб", "фарш", "chicken", "beef", "lamb", "fish", "sausage", "ham"),
    ItemGroup.BREAD to listOf("corek", "lavas", "bulka", "baton", "tendir", "xamir", "хлеб", "батон", "лаваш", "bread", "bun", "croissant"),
    ItemGroup.FRUIT_VEG to listOf("alma", "armud", "banan", "pomidor", "xiyar", "kartof", "sogan", "limon", "portagal", "mandarin", "kelem", "yerkoku", "goyerti", "badimcan", "biber", "uzum", "qarpiz", "yemis", "sarimsaq", "яблок", "банан", "помидор", "огур", "картоф", "лук", "лимон", "apple", "banana", "tomato", "cucumber", "potato", "onion", "lemon", "orange"),
    ItemGroup.STAPLES to listOf("duyu", "makaron", "un ", "seker", "duz", "yag", "cay", "qehve", "mercimek", "noxud", "lobya", "grecka", "qarabasaq", "рис", "макарон", "мук", "сахар", "соль", "масло", "чай", "кофе", "гречк", "rice", "pasta", "flour", "sugar", "salt", "oil", "tea", "coffee"),
    ItemGroup.DRINKS to listOf("su ", "sirab", "badamli", "qax", "cola", "kola", "fanta", "sprite", "pepsi", "sok", "sire", "nektar", "pive", "limonad", "вода", "сок", "пиво", "лимонад", "water", "juice", "beer", "soda"),
    ItemGroup.SWEETS to listOf("sokolad", "konfet", "pecenye", "peceniye", "tort", "dondurma", "cips", "vafli", "kek", "шоколад", "конфет", "печень", "торт", "морожен", "чипс", "chocolate", "candy", "cookie", "biscuit", "cake", "ice cream", "chips"),
    ItemGroup.HOUSEHOLD to listOf("kagiz", "salfet", "sabun", "sampun", "yuyucu", "ariel", "persil", "fairy", "domestos", "tualet", "bez", "paket", "бумаг", "салфет", "мыло", "шампун", "порош", "paper", "napkin", "soap", "shampoo", "detergent", "bag"),
)

/** Folds ə to e, ş to s and the like, so a till that drops the letters still matches. */
private fun fold(text: String): String = Normalizer.normalize(
    text.lowercase(Locale.ROOT).replace('ə', 'e').replace('ı', 'i'),
    Normalizer.Form.NFD,
).replace(Regex("""\p{Mn}+"""), "")

fun groupOf(name: String): ItemGroup {
    // Spaces around the name let a word like "et " match whole, not inside "paket".
    val text = " " + readFold(name).replace(Regex("""[^\p{L}]+"""), " ").trim() + " "
    return GROUP_WORDS.firstOrNull { (_, words) -> words.any { word -> text.contains(" ${readFold(word)}") } }?.first ?: ItemGroup.OTHER
}

/** "Süd Atena 3.2% 1L" and "SUD ATENA 3,2% 1 L" are both "sud atena": sizes, percentages and codes don't count. */
fun itemKey(name: String): String =
    readFold(name).split(Regex("""[^\p{L}]+""")).filter { it.length >= 2 && it !in UNITS }.take(KEY_WORDS).joinToString(" ")

private const val KEY_WORDS = 3
/** After q is read as g, so kq is kg and qr is gr. */
private val UNITS = setOf("kg", "gr", "ml", "lt", "litr", "ad", "pc", "pcs", "eded", "шт", "кг", "гр", "мл")

/** A word recognised on a picture, with where it sits. Text recognition gives these per line, often per column. */
data class TextPiece(val left: Int, val top: Int, val right: Int, val bottom: Int, val text: String)

/**
 * Puts a receipt's lines back together. Text recognition reads columns separately, so a name and its numbers arrive apart;
 * pieces whose middles are at the same height are one row, read left to right.
 */
fun rowsFrom(pieces: List<TextPiece>): List<String> {
    val rows = mutableListOf<MutableList<TextPiece>>()
    for (piece in pieces.sortedBy { (it.top + it.bottom) / 2 }) {
        val middle = (piece.top + piece.bottom) / 2
        val row = rows.lastOrNull()?.takeIf { row -> row.any { abs(middle - (it.top + it.bottom) / 2) <= (it.bottom - it.top) / 2 } }
        if (row != null) row += piece else rows += mutableListOf(piece)
    }
    return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
}

private fun abs(value: Int) = if (value < 0) -value else value

/** What a receipt's text gave. Anything it couldn't find is null, for the user to fill in. */
data class ParsedReceipt(
    val store: String?,
    val date: LocalDate?,
    val total: Money?,
    val fiscalId: String?,
    val items: List<ReceiptItem>,
) {
    /** The items add up to the total, so nothing was missed or misread. */
    val isComplete: Boolean get() = total != null && items.isNotEmpty() && items.map { it.total }.sum() == total
}

private val NUMBER = Regex("""(?<![\d.,])\d{1,6}(?:[.,]\d{1,3})?(?![\d.,]*\d)""")
private val DATE = Regex("""(\d{2})[./-](\d{2})[./-](\d{4})""")
private val FISCAL = Regex("""(?i)fis[kc]al\s*id\s*[:№]?\s*([A-Za-z0-9]{12})\b""")
/** The shop itself, as e-kassa and the printed receipt name it, ahead of the company that owns it. */
private val OBJECT_NAME = Regex("""(?iu)(?:object\s*name|obyekt(?:in)?\s*ad[ıi1l]?)\s*:\s*(.*\p{L}.*)""")
/** A number with a 1 that text recognition read as l, I, L or |, alone between spaces, like I.00, or 1l.60 for 1.60. */
private val MISREAD_NUMBER = Regex("""(?<!\S)(?=[\d.,lIL|]*\d)(?=[\d.,]*[lIL|])[\d.,lIL|]+(?!\S)""")
private val LOOKS_LIKE_ONE = Regex("[lIL|]")
private val NUMBERISH = Regex("""(?=.*\d)[\d.,lIL|]+""")
/** A number split at its point, like 3. 16 for 3.16. */
private val SPLIT_NUMBER = Regex("""(?<!\S)([\dlIL|]{1,4}[.,])\s(\d{2})(?!\S)""")
private const val MOST_PIECES = 99
private val TOTAL_WORDS = listOf("cemi", "yekun", "umumi", "total", "итого", "odenilecek", "odenis")

/** Rows about tax, payment and change, by their first word, so Kartof stays an item while Kart goes. */
private val SKIP_WORDS = setOf("edv", "ндс", "vat", "nagd", "nagdsiz", "kart", "qaliq", "qaytarilan", "bonus", "endirim", "cashback", "kassir", "kassa")

/** What can sit between a quantity, its price and its total: spaces, x, =, @, and a unit. */
private val BETWEEN_NUMBERS = Regex("""(?i)^[\s xX*×=@]*(?:(?:kg|kq|qr|gr|ad|ed|eded|ədəd|əd|шт|l|lt)\.?)?[\s xX*×=@]*$""")
private val STORE_MARKERS = listOf("mmc", "asc", "qsc", "ltd", "llc", "ооо", "supermarket", "market", "magaza")

/**
 * Reads the text of an e-kassa receipt, the one the tax service shows or a photo of the printed one. An item is a row whose
 * quantity times price is its total, give or take a qəpik: that holds on every till, whatever its columns, and keeps
 * prices, codes and VAT out. A name on the row above its numbers is taken too. Items stop at the total.
 */
fun parseReceiptText(rows: List<String>): ParsedReceipt {
    val items = mutableListOf<ReceiptItem>()
    var total: Money? = null
    var date: LocalDate? = null
    var fiscalId: String? = null
    var pendingName: String? = null
    var store: String? = null
    var shop: String? = null
    var inItems = true
    for (raw in rows) {
        val row = raw.trim()
        if (row.isEmpty()) continue
        val folded = fold(row)
        FISCAL.find(row)?.let { fiscalId = it.groupValues[1] }
        if (date == null) DATE.find(row)?.let { m -> date = runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull() }
        if (shop == null) OBJECT_NAME.find(row)?.let { shop = prettyMerchant(it.groupValues[1]) }
        if (store == null && STORE_MARKERS.any { folded.contains(it) } && !folded.contains("voen")) store = cleanStore(row)
        val firstWord = folded.split(Regex("""[^\p{L}]+""")).firstOrNull { it.isNotEmpty() }
        if (TOTAL_WORDS.any { firstWord == it || folded.contains(" $it") } && NUMBER.find(row) != null) {
            if (total == null) total = NUMBER.findAll(row).lastOrNull()?.value?.toMoney()
            inItems = false
            continue
        }
        if (!inItems || firstWord in SKIP_WORDS) continue
        val item = readingsOf(row).firstNotNullOfOrNull { itemIn(it, pendingName) }
        if (item != null) {
            items += item
            pendingName = null
        } else if (row.any { it.isLetter() }) {
            // A name with its numbers on the next row. Anything else with letters is replaced by the next such row.
            pendingName = row
        }
    }
    return ParsedReceipt(shop ?: store, date, total, fiscalId, items)
}

/**
 * The row as it was read, then as text recognition likely misread it: a number split at its point put back together, and
 * each number holding an l, I, L or | read both ways, the l as a 1, as in I.00, or as a second look at the 1 beside it, as
 * in 1l.60. Only a row whose quantity, price and total agree is taken, which picks the right one.
 */
private fun readingsOf(row: String): List<String> {
    val joined = SPLIT_NUMBER.replace(row, "$1$2")
    return listOf(row, joined).distinct().flatMap { text -> listOf(text) + onesIn(text) }.distinct()
}

/** Only the numbers a row ends with, where a till prints quantity, price and total, so a size in the name, like 1L, stays. */
private fun onesIn(row: String): List<String> {
    val numbersStart = Regex("""\S+""").findAll(row).toList().asReversed().takeWhile { NUMBERISH.matches(it.value) }.lastOrNull()?.range?.first ?: return emptyList()
    val misread = MISREAD_NUMBER.findAll(row).filter { it.range.first >= numbersStart }.toList().takeLast(3)
    if (misread.isEmpty()) return emptyList()
    return (0 until (1 shl misread.size)).map { ways: Int ->
        var text = row
        for (i in misread.indices.reversed()) {
            val m = misread[i]
            val asOne = (ways shr i) and 1 == 0
            text = text.replaceRange(m.range, m.value.replace(LOOKS_LIKE_ONE, if (asOne) "1" else ""))
        }
        text
    }
}

/**
 * A row with a quantity, price and total that agree and sit together, with the name before them or on the row above.
 * Totals come last on a till's row, so windows are tried from the end. A quantity can be missing: some tills leave out a
 * quantity of one, and text recognition often misses a digit standing alone, so a whole number of pieces is worked out
 * from the price and the total.
 */
private fun itemIn(row: String, nameAbove: String?): ReceiptItem? {
    val numbers = NUMBER.findAll(row).toList()
    for (take in listOf(3, 2)) {
        for (start in numbers.size - take downTo 0) {
            val window = numbers.subList(start, start + take)
            if (window.zipWithNext().any { (a, b) -> !BETWEEN_NUMBERS.matches(row.substring(a.range.last + 1, b.range.first)) }) continue
            val price = window[take - 2].value.toDecimal()
            val sum = window[take - 1].value.toDecimal()
            if (price.signum() <= 0) continue
            val quantity = if (take == 3) window[0].value.toDecimal() else sum.divide(price, 0, RoundingMode.HALF_UP)
            if (quantity.signum() <= 0 || quantity.multiply(price).subtract(sum).abs() > TOLERANCE) continue
            // A worked-out count needs prices written as money, so numbers in a header, like 104 and 1248, don't make an item.
            if (take == 2 && quantity > BigDecimal.ONE && (quantity > BigDecimal(MOST_PIECES) || window.any { !it.value.contains('.') && !it.value.contains(',') })) continue
            val before = row.substring(0, window.first().range.first).trim().trimEnd('x', 'X', '*', '×', '=').trim().replace(TRAILING_UNIT, "").trimEnd(',')
            val name = before.takeIf { it.any(Char::isLetter) } ?: nameAbove?.trim() ?: continue
            return ReceiptItem(
                name = name,
                quantityMilli = quantity.movePointRight(3).setScale(0, RoundingMode.HALF_UP).toLong(),
                price = price.toMoney(),
                total = sum.toMoney(),
            )
        }
    }
    return null
}

/**
 * What a till prints between a name and its numbers for pieces: (pc), ədəd, əd, with a quantity after it that couldn't be
 * read. Text recognition often reads ədəd as ddəd or ddod, so any short word shaped like it goes too. Weights stay, as kq
 * tells a weighed item apart.
 */
private val TRAILING_UNIT = Regex("""(?iu)\s+(?:\(?\s*(?:pcs?|шт)\s*\)?|\S?d[əoea]d|ad|əd)\.?\)?(?:\s+[\d.,lIL|]+)?$""")

private val TOLERANCE = BigDecimal("0.011")

private fun String.toDecimal(): BigDecimal = BigDecimal(replace(',', '.'))
private fun String.toMoney(): Money = toDecimal().toMoney()
private fun BigDecimal.toMoney(): Money = Money(movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong())

/** "BRAVO SUPERMARKET" MMC reads as Bravo Supermarket. */
private fun cleanStore(row: String): String {
    val inQuotes = Regex("""["“«]([^"”»]+)["”»]""").find(row)?.groupValues?.get(1)
    val name = (inQuotes ?: row.replace(Regex("""(?i)\b(mmc|asc|qsc|ltd|llc)\b"""), "")).trim()
    return prettyMerchant(name)
}

/** The fiscal ID in an e-kassa QR code's link, or a typed one. Short IDs are 12 letters and digits. */
fun fiscalIdFrom(scanned: String): String? {
    val text = scanned.trim()
    Regex("""[?&]doc=([A-Za-z0-9]{12,46})""").find(text)?.let { return it.groupValues[1] }
    return text.takeIf { Regex("""[A-Za-z0-9]{12}""").matches(it) || Regex("""[A-Za-z0-9]{42,46}""").matches(it) }
}

/** Text recognition mixes up q and g on receipts, so Toyuq can come back as Toyug: names are compared with both as g. */
private fun readFold(text: String): String = fold(text).replace('q', 'g')
