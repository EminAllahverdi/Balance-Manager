package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class TransactionCsvTest {
    private val card = Account(1, "Card", AccountType.DEBIT_CARD, "payments", 0)
    private val cash = Account(2, "Cash", AccountType.CASH, "payments", 0)
    private val me = Person(1, "Me", 0)
    private val transport = Category(10, "Transport", CategoryType.EXPENSE, icon = "local_taxi", color = 0)
    private val taxi = Category(11, "Taxi", CategoryType.EXPENSE, parentId = 10, icon = "local_taxi", color = 0)

    private fun entry(
        minor: Long = 7_70,
        kind: TransactionKind = TransactionKind.EXPENSE,
        day: Int = 1,
        category: Category? = taxi,
        parent: Category? = transport,
        toAccount: Account? = null,
        merchant: String? = "Bolt",
        note: String = "",
    ) = TransactionDetails(
        transaction = Transaction(1, kind, Money(minor), LocalDate.of(2026, 11, day), accountId = card.id, note = note),
        account = card,
        toAccount = toAccount,
        category = category,
        parentCategory = parent,
        person = me,
        merchant = merchant?.let { Merchant(1, it) },
    )

    private fun rows(vararg entries: TransactionDetails) = transactionsToCsv(entries.toList()).trimEnd().split("\r\n")

    @Test fun `the first line names the columns`() {
        assertThat(rows().single()).isEqualTo("Date,Type,Amount,Account,To account,Category,Sub-category,For whom,Merchant,Note")
    }

    @Test fun `an expense is negative and splits its category`() {
        assertThat(rows(entry())[1]).isEqualTo("2026-11-01,Expense,-7.70,Card,,Transport,Taxi,Me,Bolt,")
    }

    @Test fun `a top-level category has no sub-category`() {
        assertThat(rows(entry(category = transport, parent = null))[1]).isEqualTo("2026-11-01,Expense,-7.70,Card,,Transport,,Me,Bolt,")
    }

    @Test fun `income is positive`() {
        assertThat(rows(entry(50_00, TransactionKind.INCOME))[1]).startsWith("2026-11-01,Income,50.00,")
    }

    @Test fun `a transfer shows both accounts`() {
        val transfer = entry(100_00, TransactionKind.TRANSFER, category = null, parent = null, toAccount = cash, merchant = null)
        assertThat(rows(transfer)[1]).isEqualTo("2026-11-01,Transfer,-100.00,Card,Cash,,,Me,,")
    }

    @Test fun `signed kinds keep their sign`() {
        assertThat(rows(entry(-15_00, TransactionKind.DEBT))[1]).contains(",-15.00,")
        assertThat(rows(entry(50_00, TransactionKind.DEBT))[1]).contains(",50.00,")
    }

    @Test fun `large sums have no separators, and pennies keep two digits`() {
        assertThat(rows(entry(1_234_05))[1]).contains(",-1234.05,")
    }

    @Test fun `commas, quotes and line breaks are quoted`() {
        assertThat(rows(entry(note = "Lunch, with \"Sam\""))[1]).endsWith(",\"Lunch, with \"\"Sam\"\"\"")
        assertThat(transactionsToCsv(listOf(entry(note = "two\nlines")))).contains("\"two\nlines\"")
    }

    @Test fun `text a spreadsheet would run as a formula stays plain`() {
        assertThat(rows(entry(merchant = "=HYPERLINK(1)"))[1]).contains(",'=HYPERLINK(1),")
        assertThat(rows(entry(merchant = "+1"))[1]).contains(",'+1,")
        assertThat(rows(entry(merchant = "@SUM"))[1]).contains(",'@SUM,")
        assertThat(rows(entry(note = "-1"))[1]).endsWith(",'-1")
    }

    @Test fun `azerbaijani letters come through untouched`() {
        assertThat(rows(entry(merchant = "Şəki Çörək"))[1]).contains(",Şəki Çörək,")
    }

    @Test fun `oldest comes first`() {
        val output = rows(entry(day = 9), entry(day = 2))
        assertThat(output[1]).startsWith("2026-11-02")
        assertThat(output[2]).startsWith("2026-11-09")
    }
}
