package com.eminallahverdi.balance.core.model

import java.util.Locale
import kotlin.math.absoluteValue

private const val LINE_END = "\r\n"
private val HEADER = listOf("Date", "Type", "Amount", "Account", "To account", "Category", "Sub-category", "For whom", "Merchant", "Note")

/** Every entry as a spreadsheet, oldest first. Spending is negative, so the Amount column adds up. */
fun transactionsToCsv(entries: List<TransactionDetails>): String = buildString {
    append(HEADER.joinToString(",")).append(LINE_END)
    entries.sortedWith(compareBy({ it.transaction.date }, { it.transaction.createdAt })).forEach { entry ->
        val t = entry.transaction
        val row = listOf(
            t.date.toString(),
            t.kind.name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) },
            plainAmount(if (t.kind == TransactionKind.EXPENSE || t.kind == TransactionKind.TRANSFER) -t.amount else t.amount),
            field(entry.account.name),
            field(entry.toAccount?.name),
            field((entry.parentCategory ?: entry.category)?.name),
            field(entry.category?.name?.takeIf { entry.parentCategory != null }),
            field(entry.person?.name),
            field(entry.merchant?.name),
            field(t.note),
        )
        append(row.joinToString(",")).append(LINE_END)
    }
}

/** 1234.50, with no symbol or thousands separator, so a spreadsheet reads it as a number. */
private fun plainAmount(money: Money): String {
    val minor = money.minor.absoluteValue
    return "${if (money.isNegative) "-" else ""}${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"
}

/**
 * Quotes what needs it. Merchants can come from bank notifications, so anything a spreadsheet would run as a formula is
 * made plain text first.
 */
private fun field(text: String?): String {
    val value = text.orEmpty().let { if (it.firstOrNull() in FORMULA_STARTS) "'$it" else it }
    return if (value.any { it in NEEDS_QUOTES }) "\"${value.replace("\"", "\"\"")}\"" else value
}

internal val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
private val NEEDS_QUOTES = setOf(',', '"', '\n', '\r')
