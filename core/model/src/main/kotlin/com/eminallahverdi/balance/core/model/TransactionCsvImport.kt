package com.eminallahverdi.balance.core.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val DATE_FORMATS = listOf(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("d.M.uuuu"), DateTimeFormatter.ofPattern("d/M/uuuu"))
private val DELIMITERS = listOf(',', ';', '\t')
private const val AMOUNT_SEPARATORS = ".,  "
private val SIGNED_KINDS = setOf(TransactionKind.ADJUSTMENT, TransactionKind.INTERNAL, TransactionKind.DEBT)

/** One line of a file, before its names are matched to the app's accounts, categories and people. */
data class CsvEntry(
    val date: LocalDate,
    val kind: TransactionKind,
    /** Positive for EXPENSE, INCOME and TRANSFER; signed for the rest, as in [Transaction.amount]. */
    val amount: Money,
    val account: String? = null,
    val toAccount: String? = null,
    val category: String? = null,
    val subCategory: String? = null,
    val person: String? = null,
    val merchant: String? = null,
    val note: String = "",
)

/** What a file holds. [unreadable] counts lines that had something on them and couldn't be used. */
data class CsvImport(val entries: List<CsvEntry>, val unreadable: Int) {
    /** What the user sees before anything comes in. [accounts] and [categories] are what the app will already have. */
    fun preview(accounts: List<String>, categories: List<String>): CsvPreview {
        val years = entries.map { it.date.year }
        return CsvPreview(
            entries = entries.size,
            firstYear = years.min(),
            lastYear = years.max(),
            accounts = (accounts + entries.mapNotNull { it.account } + entries.mapNotNull { it.toAccount }).distinctBy(::nameKey),
            newCategories = entries.mapNotNull { it.category }.distinctBy(::nameKey).filter { name -> categories.none { nameKey(it) == nameKey(name) } },
            unreadable = unreadable,
        )
    }
}

data class CsvPreview(
    val entries: Int,
    val firstYear: Int,
    val lastYear: Int,
    val accounts: List<String>,
    val newCategories: List<String>,
    val unreadable: Int,
)

/** Names match whatever their case or stray spaces, so CARD in a file is the Card in the app. */
fun nameKey(name: String): String = name.trim().lowercase(Locale.ROOT)

/**
 * Reads a file like the ones Export CSV writes, or anyone's spreadsheet saved as CSV. Columns are found by name in any
 * order, and only Date and Amount have to be there, or this returns null. With no Type column, a negative amount is an
 * expense and a positive one is income. A line that can't be used is counted and left out, so the rest still comes in.
 */
fun parseTransactionCsv(text: String): CsvImport? {
    val rows = csvRows(text.removePrefix("﻿"))
    val header = rows.firstOrNull()?.map(::nameKey) ?: return null
    if ("date" !in header || "amount" !in header) return null
    val lines = rows.drop(1).filter { row -> row.any { it.isNotBlank() } }
    val entries = lines.mapNotNull { row -> entryFrom { column -> header.indexOf(column).takeIf { it >= 0 }?.let(row::getOrNull)?.let(::plainText)?.takeIf { it.isNotEmpty() } } }
    return CsvImport(entries, lines.size - entries.size)
}

private fun entryFrom(cell: (String) -> String?): CsvEntry? {
    val date = cell("date")?.let(::parseDate) ?: return null
    val amount = cell("amount")?.let(::parseSignedAmount)?.takeIf { !it.isZero } ?: return null
    val account = cell("account")
    val toAccount = cell("to account")
    val kind = kindOf(cell("type"), amount, toAccount) ?: return null
    val isTransfer = kind == TransactionKind.TRANSFER
    if (isTransfer && (account == null || toAccount == null || nameKey(account) == nameKey(toAccount))) return null
    return CsvEntry(
        date = date,
        kind = kind,
        amount = if (kind in SIGNED_KINDS) amount else amount.abs(),
        account = account,
        toAccount = toAccount.takeIf { isTransfer },
        category = cell("category"),
        subCategory = cell("sub-category"),
        person = cell("for whom"),
        merchant = cell("merchant"),
        note = cell("note").orEmpty(),
    )
}

private fun kindOf(type: String?, amount: Money, toAccount: String?): TransactionKind? = when {
    type != null -> TransactionKind.entries.firstOrNull { it.name.equals(type, ignoreCase = true) }
    toAccount != null -> TransactionKind.TRANSFER
    amount.isNegative -> TransactionKind.EXPENSE
    else -> TransactionKind.INCOME
}

private fun parseDate(text: String): LocalDate? = DATE_FORMATS.firstNotNullOfOrNull { format ->
    try {
        LocalDate.parse(text, format)
    } catch (_: DateTimeParseException) {
        null
    }
}

/** −7.70, -7.70, (7.70), 1,234.56 and 1 234,56 ₼ are all sums people's spreadsheets write. */
private fun parseSignedAmount(text: String): Money? {
    val cleaned = text.replace("₼", "").replace("AZN", "", ignoreCase = true).trim()
    val negative = cleaned.startsWith('-') || cleaned.startsWith('−') || (cleaned.startsWith('(') && cleaned.endsWith(')'))
    val digits = cleaned.trim('-', '−', '+', '(', ')').trim()
    if (digits.none(Char::isDigit) || digits.any { !it.isDigit() && it !in AMOUNT_SEPARATORS }) return null
    val amount = parseAmount(digits) ?: return null
    return if (negative) -amount else amount
}

/** Takes back the mark Export CSV adds so a spreadsheet doesn't run text as a formula. */
private fun plainText(cell: String): String {
    val text = cell.trim()
    return if (text.length > 1 && text[0] == '\'' && text[1] in FORMULA_STARTS) text.drop(1) else text
}

/** Splits a file by CSV's rules: quotes around anything holding a comma or a line break, and doubled quotes inside them. */
private fun csvRows(text: String): List<List<String>> {
    val firstLine = text.lineSequence().firstOrNull().orEmpty()
    val delimiter = DELIMITERS.maxBy { d -> firstLine.count { it == d } }
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                field.append('"')
                i++
            }
            c == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
            quoted -> field.append(c)
            c == delimiter -> {
                row.add(field.toString())
                field.clear()
            }
            c == '\n' -> {
                row.add(field.toString())
                field.clear()
                rows.add(row)
                row = mutableListOf()
            }
            c != '\r' -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) {
        row.add(field.toString())
        rows.add(row)
    }
    return rows
}
