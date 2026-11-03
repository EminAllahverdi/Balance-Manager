package com.eminallahverdi.balance.core.model

import java.time.Instant

/** A payment or incoming sum read from a bank's notification, before the user has decided about it. */
data class BankAlert(val amount: Money, val kind: TransactionKind, val merchant: String, val cardEnding: String = "")

enum class PendingStatus { WAITING, ADDED, SKIPPED }

/** A ready-made entry waiting on the To confirm page. [merchant] is the bank's own text, like BRAVO SUPERMARKET. */
data class PendingEntry(
    val id: Long,
    val externalId: String,
    /** The bank app's name, for the card. */
    val source: String,
    val amount: Money,
    val kind: TransactionKind,
    val merchant: String,
    val postedAt: Instant,
    val status: PendingStatus = PendingStatus.WAITING,
    /** The notification's title, which can say what a payment was when the text doesn't. */
    val title: String = "",
    /** The card's last four digits, which say which account this was when an account has them saved. */
    val cardEnding: String = "",
)

/**
 * Reads a notification from any bank, or returns null when it isn't a payment: a login code, an offer, a balance on its own.
 *
 * Nothing here knows about a particular bank, because no two word these alike. This looks for what every one has: a sum in
 * manats, and words around it that mean money moved. A card payment in another currency is read from the manat figure banks
 * send with it, which includes their commission. Taking cash out is a transfer, not spending, and a plus sign means money
 * came in. Anything it can't read stays off the page, and the bank's own notification is untouched.
 */
fun parseBankNotification(title: String, text: String, appName: String = ""): BankAlert? =
    readBankNotification(title.replace(INVISIBLE, ""), text.replace(INVISIBLE, ""), appName)

private fun readBankNotification(title: String, text: String, appName: String): BankAlert? {
    val message = "$title\n$text"
    if (SECURITY_WORDS.containsMatchIn(message) || OFFER_WORDS.containsMatchIn(message)) return null
    val sum = AMOUNT.findAll(message).firstOrNull { !it.isSideFigure(message) } ?: return null
    val amount = parseAmount(sum.groups["before"]?.value ?: sum.groups["after"]?.value ?: return null) ?: return null
    if (amount.isZero || !MONEY_MOVED_WORDS.containsMatchIn(message)) return null
    val kind = when {
        INCOMING_WORDS.containsMatchIn(message) || message.substring(0, sum.range.first).trimEnd().endsWith('+') -> TransactionKind.INCOME
        CASH_WORDS.containsMatchIn(message) -> TransactionKind.TRANSFER
        else -> TransactionKind.EXPENSE
    }
    val titleMerchant = if (title.trim().equals(appName.trim(), ignoreCase = true)) "" else merchantIn(title, sum.value)
    val merchant = merchantIn(text, sum.value).ifBlank { titleMerchant }
    return BankAlert(amount, kind, merchant, CARD_ENDING.find(message)?.groupValues?.get(1).orEmpty())
}

/** A balance, a commission or an exchange rate: a sum in manats, but not what was paid. */
private fun MatchResult.isSideFigure(message: String): Boolean {
    val before = message.substring((range.first - WORDS_BEFORE_A_SUM).coerceAtLeast(0), range.first)
    return SIDE_FIGURE_WORDS.containsMatchIn(before.split(',', ';', '\n').last()) || EXCHANGE_RATE.containsMatchIn(before)
}

internal fun parseAmount(text: String): Money? {
    val digits = text.replace(" ", "").replace(" ", "")
    val lastSeparator = digits.indexOfLast { it == '.' || it == ',' }
    val hasDecimals = lastSeparator >= 0 && digits.length - lastSeparator - 1 in 1..2
    val whole = (if (hasDecimals) digits.substring(0, lastSeparator) else digits).filter(Char::isDigit)
    val fraction = if (hasDecimals) digits.substring(lastSeparator + 1).padEnd(2, '0') else "00"
    return Money.parse("$whole.$fraction")
}

/** Whichever clause isn't the sum, the card, the balance or the date. "Merchant: X" wins when a bank spells it out. */
private fun merchantIn(text: String, sum: String): String {
    MERCHANT_LABEL.find(text)?.let { return it.groupValues[1].trim() }
    return text.split(CLAUSE_END)
        .map { clause -> clause.replace(sum, " ").replace(FOREIGN_SUM, " ").replace(PHONE_NUMBER, " ").replace(LEADING_WORDS, " ").replace(PUNCTUATION, " ").trim().trim('.', '-').trim() }
        .firstOrNull { clause -> clause.any(Char::isLetter) && !NOT_A_MERCHANT.containsMatchIn(clause) }
        ?.let(::withoutCardNoise)
        .orEmpty()
}

/**
 * ANTHROPIC* CLAUDE SUB is Claude and Spotify P48A9CB5BF is Spotify. What's before a star is who took the payment for the
 * shop, and a payment reference changes every time, so leaving it in would make every charge a new merchant.
 */
private fun withoutCardNoise(name: String): String {
    val shop = name.substringAfter('*', name).trim().ifEmpty { name }
    val words = shop.split(WHITESPACE).filterNot(::isPaymentReference).dropLastWhile { BILLING_WORD.matches(it) }
    return words.joinToString(" ").ifEmpty { name }
}

private fun isPaymentReference(word: String): Boolean =
    word.length >= SHORTEST_REFERENCE && word.count(Char::isDigit) >= DIGITS_IN_A_REFERENCE && word.any(Char::isLetter)

private const val WORDS_BEFORE_A_SUM = 20
private const val SHORTEST_REFERENCE = 8
private const val DIGITS_IN_A_REFERENCE = 3
private const val FOREIGN_CURRENCY = """USD|EUR|GBP|TRY|RUB|\$|€|£|₺|₽"""

// Word edges spelled out, because \b treats ö and ə as letters on Android but not on the JVM the tests run on.
private const val START = """(?<![\p{L}\d])"""
private const val END = """(?![\p{L}\d])"""

private fun words(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

/** Text-direction marks and the like, which banks put inside a sum. They don't show on screen, but they'd split -15.24₼ apart. */
private val INVISIBLE = Regex("""\p{Cf}""")

// Separators only count in proper thousands, so a card number beside a sum never joins it.
private const val NUMBER = """(?<![\d*.,])(?:\d{1,3}(?:[  ,.]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)"""

private val AMOUNT = words(
    """(?:(?:₼|${START}AZN$END|${START}manat$END)\s*(?<after>$NUMBER))|(?:(?<before>$NUMBER)\s*(?:₼|AZN$END|man$END|manat$END))""",
)
private val FOREIGN_SUM = words("""(?:\d[\d.,]*\s*(?:$FOREIGN_CURRENCY))|(?:(?:$FOREIGN_CURRENCY)\s*\d[\d.,]*)""")
private val EXCHANGE_RATE = words("""${START}1(?:[.,]0+)?\s*(?:$FOREIGN_CURRENCY)\s*=\s*$""")

/**
 * The number a top-up was for, as in Nar 77:1234567, which is a payment to Nar. Only a number written the way phone
 * numbers are counts: with a country code, a leading 0 or a gap after the operator's code. A bare run of digits is a
 * reference, and keeps its line out of shop names.
 */
private val PHONE_NUMBER = Regex("""(?<![\d*])(?:(?:\+?994|0)[\s-]*\(?\d{2}\)?[\s:-]*|\d{2}[\s:-]+)\d{3}[\s-]?\d{2}[\s-]?\d{2}(?!\d)""")
private val CLAUSE_END = Regex("""[,;\n|]|\.\s""")
private val PUNCTUATION = Regex("""[:()–—]""")
private val WHITESPACE = Regex("""\s+""")
private val CARD_ENDING = Regex("""\*+\s*(\d{4})(?!\d)""")
private val BILLING_WORD = words("""sub|subscription|membership""")

private val SECURITY_WORDS = words("""$START(otp|kod|kodu|kodunuz|code|şifrə|sifre|parol|password|пароль|код)$END""")
private val OFFER_WORDS = words("""(təklif|kampaniya|endirim|keşbek|kesbek|cashback|promo|$START(offer|bonus)|предлож|акци|скидк)""")
private val SIDE_FIGURE_WORDS = words(
    """(balans|balance|qalıq|qaliq|mövcud|movcud|баланс|остаток|доступно|limit|лимит|komissiya|commission|комисси|kurs|məzənnə|mezenne|курс|$START(fee|rate)$END)""",
)

// Cash out of a card, wherever it was taken. The place in the notification isn't a shop.
private const val CASH_OUT = """n[aə][ğg]dl[aə][şs]d[ıi]r|bankomat|${START}atm$END|withdraw|снятие|наличн"""
private val CASH_WORDS = words(CASH_OUT)

/** A notification with a sum in it but none of these is a statement, not something that happened. */
private val MONEY_MOVED_WORDS = words(
    """(ödəniş|odenis|alış|alis|məxaric|mexaric|çıxarış|çıxarıl|çıxıl|cixaris|$CASH_OUT|köçürmə|kocurme|mədaxil|medaxil|daxil ol|daxilolma|ödənildi|tutul|silin|hesab|kart|""" +
        """payment|purchase|paid|transfer|received|credited|debited|deposit|spent|charged|card|account|""" +
        """оплата|покупка|списан|перевод|зачисл|пополнен|поступлен|карт|счет|счёт|\*+\s*\d{3,4})""",
)
private val INCOMING_WORDS = words("""(mədaxil|medaxil|daxil ol|daxilolma|received|credited|deposit|incoming|зачисл|пополнен|поступлен)""")

/** What comes before a shop's name in a clause, and is no part of it. */
private val LEADING_WORDS = words(
    """$START(kartla |kartdan )?(ödəniş\p{L}*|odenis\p{L}*|alış|alis|məxaric|mexaric|mədaxil|medaxil|köçürmə\p{L}*|kocurme\p{L}*|payment|purchase|paid|spent|at|оплата|покупка)$END""",
)
private val NOT_A_MERCHANT = words(
    """(\*+\s*\d{2,4}|$START\d{4,}$END|\d{1,2}[./:]\d{2}|${START}(kart|card|карт|hesab|account|счет|счёт)|balans|balance|qalıq|баланс|остаток|$START(bank|банк|bildiriş|notification|əməliyyat|transaction|silindi|tutuldu)$END)""",
)
private val MERCHANT_LABEL = words("""$START(?:obyekt|ticarət nöqtəsi|ticarət obyekti|merchant|place|торговая точка|магазин)\s*:\s*([^,;\n|]+)""")
