package com.eminallahverdi.balance.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * Bumped whenever the snapshot's sections or their meaning change, so whoever reads it can tell. The guide in the
 * snapshot says the same, in words.
 */
const val ACCOUNTANT_FORMAT = 2

/** Full months shown before the one running: enough to see a trend, and January still has last year behind it. */
const val ACCOUNTANT_MONTHS_BEFORE = 6

/** Everything the accountant's copy is made from. Gathered by the app, turned into text here, so a test can pin it down. */
data class AccountantInput(
    val madeAt: LocalDateTime,
    val appVersion: String,
    /** Accounts that aren't archived, with today's balance. */
    val balances: List<AccountBalance>,
    /** Every entry in [accountantWindow]. */
    val entries: List<TransactionDetails>,
    val categories: List<Category>,
    val people: List<Person>,
    val debts: List<DebtBalance>,
    val recurring: List<RecurringPayment>,
    val planned: List<PlannedStatus>,
    val receipts: List<Receipt>,
) {
    val today: LocalDate get() = madeAt.toLocalDate()
}

/** The month running and the [ACCOUNTANT_MONTHS_BEFORE] full ones before it. */
fun accountantWindow(today: LocalDate): DateRange =
    DateRange(YearMonth.from(today).minusMonths(ACCOUNTANT_MONTHS_BEFORE.toLong()).atDay(1), today)

/** The months of [accountantWindow], oldest first. */
fun accountantMonths(today: LocalDate): List<YearMonth> =
    (ACCOUNTANT_MONTHS_BEFORE downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }

/** The entries of the window, as the transactions sheet: the same columns Export CSV writes, oldest first. */
fun accountantTransactionsCsv(input: AccountantInput): String = transactionsToCsv(input.entries)

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/**
 * The snapshot as a web page, which Google Drive turns into a Doc on upload. No AI writes it: fixed sections filled from
 * the same numbers Home, Reports and Loans & debts show, always in English, names as the owner typed them. Anything that
 * changes what money means in the app (a new kind of entry, how a debt counts) has to change this and its guide too.
 */
fun accountantSnapshotHtml(input: AccountantInput): String = buildString {
    val months = accountantMonths(input.today)
    val window = accountantWindow(input.today)
    append("<html><head><meta charset=\"utf-8\"><title>Cibim snapshot</title></head><body>")
    h(1, "Cibim snapshot")
    p(
        "Made ${DAY.format(input.madeAt)} at ${TIME.format(input.madeAt)}, phone time. " +
            "Covers ${DAY.format(window.start)} to ${DAY.format(window.endInclusive)}. " +
            "Cibim ${input.appVersion}, snapshot format $ACCOUNTANT_FORMAT.",
    )

    h(2, "How to read this")
    GUIDE.forEach { p(it) }

    h(2, "Balances today")
    val shown = input.balances.sortedBy { it.account.sortOrder }
    table(
        listOf("Account", "Type", "Balance", "In the total"),
        shown.map { listOf(it.account.name, typeName(it.account.type), money(it.balance), if (it.account.includeInTotal) "Yes" else "No") } +
            listOf(listOf("Total", "", money(shown.filter { it.account.includeInTotal }.map { it.balance }.sum()), "")),
    )

    val summaries = months.map { month ->
        val range = DateRange(month.atDay(1), minOf(month.atEndOfMonth(), input.today))
        month to SpendingCalculator.summarize(range, input.entries, input.people)
    }

    h(2, "Month by month")
    p("The month running is only so far. Saved is income less spending; transfers, debts and corrections are in neither.")
    table(
        listOf("Month", "Income", "Spent", "Saved"),
        summaries.map { (month, s) -> listOf(monthName(month, input.today), money(s.income), money(s.spent), money(s.income - s.spent)) },
    )

    h(2, "Spending by category")
    p("Each category with its sub-categories, by month. Paying a bank loan, and a share of a bill paid for others, are spending here like anything else.")
    categoryTable(this, summaries, input, sub = false)

    h(2, "Spending by sub-category")
    categoryTable(this, summaries, input, sub = true)

    h(2, "Income by category")
    val incomeCategories = summaries.flatMap { (_, s) -> s.incomeByCategory.map { it.key } }.distinctBy { it.id }.sortedBy { it.sortOrder }
    table(
        listOf("Category") + months.map { monthName(it, input.today) },
        incomeCategories.map { category -> listOf(category.name) + summaries.map { (_, s) -> money(s.incomeByCategory.firstOrNull { it.key.id == category.id }?.amount ?: Money.ZERO) } },
    )

    h(2, "Spending by person")
    p("Who each expense was for. Family means shared things.")
    table(
        listOf("Person") + months.map { monthName(it, input.today) },
        input.people.filterNot { it.isArchived }.map { person ->
            listOf(person.name) + summaries.map { (_, s) -> money(s.byPerson.firstOrNull { it.person.id == person.id }?.total ?: Money.ZERO) }
        },
    )

    h(2, "Money between the owner and other people")
    val open = input.debts.filterNot { it.isSettled }.sortedWith(compareBy({ it.debt.direction }, { it.debt.date }))
    if (open.isEmpty()) {
        p("Nothing open.")
    } else {
        table(
            listOf("Who", "Which way", "Kind", "Left", "Monthly", "Since", "Note"),
            open.map { b ->
                val d = b.debt
                listOf(
                    d.counterparty,
                    if (d.direction == DebtDirection.I_OWE) "Owner owes them" else "They owe the owner",
                    debtKind(d, input.categories),
                    money(b.left),
                    d.monthly?.let { money(it) } ?: "",
                    DAY.format(d.date),
                    d.note,
                )
            },
        )
    }

    h(2, "Bank loans")
    val loans = input.recurring.filter { it.loan != null && !it.isFinished }
    if (loans.isEmpty()) {
        p("None running.")
    } else {
        table(
            listOf("Loan", "Monthly", "Payments left", "Still to pay", "Next payment", "Yearly rate", "Borrowed"),
            loans.map { r ->
                val l = r.loan!!
                listOf(
                    r.name,
                    money(r.amount),
                    l.paymentsLeft.toString(),
                    money(r.stillToPay),
                    DAY.format(r.nextDate),
                    l.annualRateBp?.let { "${formatRate(it)}%" } ?: "",
                    l.principal?.let { money(it) } ?: "",
                )
            },
        )
    }

    h(2, "Recurring payments")
    val repeating = input.recurring.filter { it.loan == null }.sortedBy { it.nextDate }
    if (repeating.isEmpty()) {
        p("None.")
    } else {
        table(
            listOf("Payment", "Amount", "Every", "Next", "Category"),
            repeating.map { r -> listOf(r.name, money(r.amount), every(r.frequency), DAY.format(r.nextDate), categoryName(r.categoryId, input.categories)) },
        )
    }

    h(2, "Planned payments")
    val due = input.planned.toPayBy(YearMonth.from(input.today))
    p("One-off payments still to make this month, overdue ones included. Home takes them off the balance it shows as left.")
    if (due.isEmpty()) {
        p("None.")
    } else {
        table(listOf("Payment", "Amount", "Date"), due.map { listOf(it.payment.name, money(it.payment.amount), DAY.format(it.payment.date)) })
    }

    h(2, "Groceries from receipts")
    val inWindow = input.receipts.filter { it.date in window }
    if (inWindow.isEmpty()) {
        p("No receipts scanned in these months.")
    } else {
        p("Only what receipts were scanned for, by what it was, by month. Groceries spending above is the whole of it.")
        table(
            listOf("Group") + months.map { monthName(it, input.today) },
            ItemGroup.entries.mapNotNull { group ->
                val row = months.map { month -> inWindow.filter { YearMonth.from(it.date) == month }.flatMap { it.items }.filter { it.group == group }.map { it.total }.sum() }
                if (row.all { it.isZero }) null else listOf(group.label) + row.map { money(it) }
            },
        )
    }
    append("</body></html>")
}

/** What the accountant needs to know to read the numbers the way the app means them. */
private val GUIDE = listOf(
    "This is the household's money as Cibim, the owner's own app, has it. It's rewritten every night and whenever the owner backs up by hand, so the date above is when it was true. The sheet called Cibim transactions, in the same folder, has every entry of these months, one per row.",
    "Amounts are in Azerbaijani manats (₼, AZN), always with two decimals.",
    "Spending is expenses only, and income is income only. Transfers between the owner's accounts are neither. Corrections to a balance are neither. Debts between the owner and other people are neither: lending, borrowing and paying back only move money between accounts and people.",
    "A bill the owner paid for others counts only the owner's share as spending; what the others owe back is a debt. Something a friend paid for the owner becomes spending only once the owner pays it back.",
    "Bank loan payments are spending, in the loan's category. What's left of a loan is in Bank loans.",
    "In the transactions sheet, spending and transfers out of an account are negative, so the Amount column adds up. Type says which kind each row is: Expense, Income, Transfer, Adjustment, Internal or Debt.",
    "Every expense says who it was for. Family means shared things, like groceries.",
    "Only what the owner has logged or confirmed is here. Bank notifications the app read but the owner hasn't confirmed are left out, because until then they may be wrong, doubled, or not a payment at all.",
)

private fun every(frequency: RecurringFrequency): String = when (frequency) {
    RecurringFrequency.WEEKLY -> "Week"
    RecurringFrequency.MONTHLY -> "Month"
    RecurringFrequency.YEARLY -> "Year"
}

private fun categoryTable(out: StringBuilder, summaries: List<Pair<YearMonth, MonthSummary>>, input: AccountantInput, sub: Boolean) {
    val months = summaries.map { it.first }
    val expenses = input.entries.filter { it.transaction.kind == TransactionKind.EXPENSE }
    if (!sub) {
        val categories = summaries.flatMap { (_, s) -> s.byCategory.map { it.category } }.distinctBy { it.id }.sortedBy { it.sortOrder }
        out.table(
            listOf("Category") + months.map { monthName(it, input.today) },
            categories.map { c -> listOf(c.name) + summaries.map { (_, s) -> money(s.byCategory.firstOrNull { it.category.id == c.id }?.total ?: Money.ZERO) } } +
                listOf(listOf("All spending") + summaries.map { (_, s) -> money(s.spent) }),
        )
        return
    }
    // Only entries filed under a sub-category; the rest of a category is in the table above.
    val rows = expenses.filter { it.parentCategory != null && it.category != null }
    val subs = rows.map { it.category!! to it.parentCategory!! }.distinctBy { it.first.id }.sortedWith(compareBy({ it.second.sortOrder }, { it.first.sortOrder }))
    if (subs.isEmpty()) {
        out.p("Nothing filed under a sub-category in these months.")
        return
    }
    out.table(
        listOf("Sub-category") + months.map { monthName(it, input.today) },
        subs.map { (child, parent) ->
            listOf("${parent.name} · ${child.name}") + months.map { month ->
                money(rows.filter { it.category?.id == child.id && YearMonth.from(it.transaction.date) == month }.map { it.transaction.amount }.sum())
            }
        },
    )
}

private fun debtKind(d: Debt, categories: List<Category>): String = when {
    d.isPaidForOthers -> "Bill the owner paid for others · ${categoryName(d.categoryId, categories)}"
    d.isPaidForMe -> "They paid for the owner · ${categoryName(d.categoryId, categories)}"
    d.direction == DebtDirection.I_OWE -> "Owner borrowed"
    else -> "Owner lent"
}

private fun categoryName(id: Long?, categories: List<Category>): String {
    val category = categories.firstOrNull { it.id == id } ?: return ""
    val parent = categories.firstOrNull { it.id == category.parentId }
    return if (parent != null) "${parent.name} · ${category.name}" else category.name
}

private fun typeName(type: AccountType): String = type.name.lowercase(Locale.ROOT).split('_').joinToString(" ").replaceFirstChar { it.titlecase(Locale.ROOT) }

private fun monthName(month: YearMonth, today: LocalDate): String =
    MONTH.format(month) + if (month == YearMonth.from(today)) " (so far)" else ""

/** ₼1,234.50, always two decimals, with a minus below zero. */
internal fun money(amount: Money): String {
    val abs = amount.minor.absoluteValue
    val grouped = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val sign = if (amount.minor < 0) "−" else ""
    return "$sign₼$grouped.${(abs % 100).toString().padStart(2, '0')}"
}

private fun StringBuilder.h(level: Int, text: String) {
    append("<h$level>").append(escape(text)).append("</h$level>")
}

private fun StringBuilder.p(text: String) {
    append("<p>").append(escape(text)).append("</p>")
}

private fun StringBuilder.table(header: List<String>, rows: List<List<String>>) {
    append("<table border=\"1\" cellpadding=\"4\"><tr>")
    header.forEach { append("<th>").append(escape(it)).append("</th>") }
    append("</tr>")
    rows.forEach { row ->
        append("<tr>")
        row.forEach { append("<td>").append(escape(it)).append("</td>") }
        append("</tr>")
    }
    append("</table>")
}

private fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
