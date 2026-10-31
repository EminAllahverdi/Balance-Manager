package com.eminallahverdi.balance.feature.accounts

import com.eminallahverdi.balance.core.model.Money

private const val MAX_LENGTH = 12

/** Keeps only what can be part of an amount: a leading minus, digits, one separator and two decimals. */
internal fun cleanAmountInput(text: String): String {
    val sign = if (text.startsWith("-")) "-" else ""
    val body = text.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
    val separator = body.indexOf('.')
    val cleaned = if (separator < 0) {
        body
    } else {
        body.substring(0, separator + 1) + body.substring(separator + 1).replace(".", "").take(2)
    }
    return sign + cleaned.take(MAX_LENGTH)
}

internal fun flipSign(text: String): String = if (text.startsWith("-")) text.drop(1) else "-$text"

/** The typed amount, or null until there's a digit: "-" and "." alone aren't a balance of zero. */
internal fun parseAmountInput(text: String): Money? = if (text.any(Char::isDigit)) Money.parse(text) else null
