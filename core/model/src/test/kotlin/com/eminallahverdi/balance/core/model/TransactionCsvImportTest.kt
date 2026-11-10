package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class TransactionCsvImportTest {
    private val header = "Date,Type,Amount,Account,To account,Category,Sub-category,For whom,Merchant,Note\r\n"

    private fun entry(csv: String) = parseTransactionCsv(csv)!!.entries.single()

    @Test fun `reads what Export CSV writes`() {
        val card = Account(1, "Card", AccountType.DEBIT_CARD, "credit_card", 0)
        val cash = Account(2, "Cash", AccountType.CASH, "payments", 0)
        val food = Category(1, "Food", CategoryType.EXPENSE, null, "restaurant", 0)
        val coffee = Category(2, "Coffee", CategoryType.EXPENSE, 1, "local_cafe", 0)
        val me = Person(1, "Me", 0)
        val shop = Merchant(1, "=Odd, \"shop\"", null, null, null, 0)
        fun row(kind: TransactionKind, minor: Long, to: Account? = null, category: Category? = null, parent: Category? = null) = TransactionDetails(
            Transaction(id = 0, kind = kind, amount = Money(minor), date = LocalDate.of(2026, 11, 1), accountId = card.id, toAccountId = to?.id, note = "line one\nline two"),
            card, to, category, parent, me, shop,
        )
        val exported = transactionsToCsv(
            listOf(
                row(TransactionKind.EXPENSE, 7_70, category = coffee, parent = food),
                row(TransactionKind.INCOME, 100_00),
                row(TransactionKind.TRANSFER, 25_00, to = cash),
                row(TransactionKind.ADJUSTMENT, -3_00),
                row(TransactionKind.DEBT, 12_00),
            ),
        )

        val read = parseTransactionCsv("﻿$exported")!!

        assertThat(read.unreadable).isEqualTo(0)
        val spent = read.entries[0]
        assertThat(spent.kind).isEqualTo(TransactionKind.EXPENSE)
        assertThat(spent.amount).isEqualTo(Money(7_70))
        assertThat(spent.date).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(spent.account).isEqualTo("Card")
        assertThat(spent.category).isEqualTo("Food")
        assertThat(spent.subCategory).isEqualTo("Coffee")
        assertThat(spent.person).isEqualTo("Me")
        assertThat(spent.merchant).isEqualTo("=Odd, \"shop\"")
        assertThat(spent.note).isEqualTo("line one\nline two")
        assertThat(read.entries.map { it.kind }).containsExactly(
            TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.TRANSFER, TransactionKind.ADJUSTMENT, TransactionKind.DEBT,
        ).inOrder()
        assertThat(read.entries[2].amount).isEqualTo(Money(25_00))
        assertThat(read.entries[2].toAccount).isEqualTo("Cash")
        assertThat(read.entries[3].amount).isEqualTo(Money(-3_00))
        assertThat(read.entries[4].amount).isEqualTo(Money(12_00))
    }

    @Test fun `it needs a Date and an Amount column, and nothing else`() {
        assertThat(parseTransactionCsv("Date,Note\n2026-11-01,hello\n")).isNull()
        assertThat(parseTransactionCsv("Amount,Note\n5.00,hello\n")).isNull()
        assertThat(parseTransactionCsv("")).isNull()
        val bare = entry("Date,Amount\n2026-11-01,-5.00\n")
        assertThat(bare.amount).isEqualTo(Money(5_00))
        assertThat(bare.account).isNull()
        assertThat(bare.category).isNull()
    }

    @Test fun `columns are found by name in any order, whatever their case`() {
        val read = entry("merchant, AMOUNT ,date\nBakery,-3.50,2026-11-01\n")
        assertThat(read.merchant).isEqualTo("Bakery")
        assertThat(read.amount).isEqualTo(Money(3_50))
    }

    @Test fun `with no Type column the sign decides`() {
        assertThat(entry("Date,Amount\n2026-11-01,-5.00\n").kind).isEqualTo(TransactionKind.EXPENSE)
        assertThat(entry("Date,Amount\n2026-11-01,5.00\n").kind).isEqualTo(TransactionKind.INCOME)
        assertThat(entry("Date,Amount,Account,To account\n2026-11-01,5.00,Card,Cash\n").kind).isEqualTo(TransactionKind.TRANSFER)
    }

    @Test fun `dates come in the usual shapes`() {
        listOf("2026-11-02", "02.11.2026", "2.11.2026", "02/11/2026").forEach { date ->
            assertThat(entry("Date,Amount\n$date,1.00\n").date).isEqualTo(LocalDate.of(2026, 11, 2))
        }
    }

    @Test fun `amounts come in the usual shapes`() {
        fun amount(text: String) = entry("Date,Amount,Type\n2026-11-01,\"$text\",Adjustment\n").amount
        assertThat(amount("1,234.56")).isEqualTo(Money(1_234_56))
        assertThat(amount("1 234,56")).isEqualTo(Money(1_234_56))
        assertThat(amount("1 234,56")).isEqualTo(Money(1_234_56))
        assertThat(amount("12,4")).isEqualTo(Money(12_40))
        assertThat(amount("₼7.70")).isEqualTo(Money(7_70))
        assertThat(amount("7.70 AZN")).isEqualTo(Money(7_70))
        assertThat(amount("−7.70")).isEqualTo(Money(-7_70))
        assertThat(amount("(7.70)")).isEqualTo(Money(-7_70))
        assertThat(amount("+7.70")).isEqualTo(Money(7_70))
    }

    @Test fun `a spreadsheet that separates with semicolons or tabs works too`() {
        assertThat(entry("Date;Amount;Note\n2026-11-01;-5,50;a, b\n").note).isEqualTo("a, b")
        assertThat(entry("Date;Amount;Note\n2026-11-01;-5,50;a, b\n").amount).isEqualTo(Money(5_50))
        assertThat(entry("Date\tAmount\n2026-11-01\t-5.50\n").amount).isEqualTo(Money(5_50))
    }

    @Test fun `lines that can't be used are counted and the rest still come in`() {
        val read = parseTransactionCsv(
            header +
                "2026-11-01,Expense,-5.00,Card,,,,,,\r\n" +
                "not a date,Expense,-5.00,Card,,,,,,\r\n" +
                "2026-11-01,Expense,lots,Card,,,,,,\r\n" +
                "2026-11-01,Expense,0.00,Card,,,,,,\r\n" +
                "2026-11-01,Nonsense,-5.00,Card,,,,,,\r\n" +
                "2026-11-01,Transfer,-5.00,Card,,,,,,\r\n" +
                "2026-11-01,Transfer,-5.00,Card,Card,,,,,\r\n" +
                "2026-11-01\r\n",
        )!!
        assertThat(read.entries).hasSize(1)
        assertThat(read.unreadable).isEqualTo(7)
    }

    @Test fun `blank lines aren't counted as unreadable`() {
        val read = parseTransactionCsv("Date,Amount\n2026-11-01,5.00\n\n,\n")!!
        assertThat(read.entries).hasSize(1)
        assertThat(read.unreadable).isEqualTo(0)
    }

    @Test fun `only a real formula mark is taken off`() {
        assertThat(entry("Date,Amount,Merchant\n2026-11-01,5.00,'+1 shop\n").merchant).isEqualTo("+1 shop")
        assertThat(entry("Date,Amount,Merchant\n2026-11-01,5.00,'Tis Shop\n").merchant).isEqualTo("'Tis Shop")
    }

    @Test fun `the preview says what's coming, and only names what's new`() {
        val read = parseTransactionCsv(
            "Date,Amount,Account,To account,Category\n" +
                "2021-03-01,-5.00,Card,,food\n" +
                "2026-11-01,-5.00,Savings,,Pets\n" +
                "2024-01-01,5.00,Card,Wallet,\n" +
                "oops,5.00,Card,,\n",
        )!!
        val preview = read.preview(accounts = listOf("Card", "Cash"), categories = listOf("Food"))
        assertThat(listOf(preview.entries, preview.firstYear, preview.lastYear)).containsExactly(3, 2021, 2026).inOrder()
        assertThat(preview.accounts).containsExactly("Card", "Cash", "Savings", "Wallet").inOrder()
        assertThat(preview.newCategories).containsExactly("Pets")
        assertThat(preview.unreadable).isEqualTo(1)
    }

    @Test fun `a small file counts one entry in one year`() {
        val preview = parseTransactionCsv("Date,Amount\n2026-11-01,5.00\n")!!.preview(emptyList(), emptyList())
        assertThat(listOf(preview.entries, preview.firstYear, preview.lastYear)).containsExactly(1, 2026, 2026).inOrder()
        assertThat(preview.unreadable).isEqualTo(0)
    }
}
