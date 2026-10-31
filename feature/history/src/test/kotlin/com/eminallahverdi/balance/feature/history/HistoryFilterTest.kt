package com.eminallahverdi.balance.feature.history

import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.Merchant
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.Transaction
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.TransactionKind
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class HistoryFilterTest {
    private val card = Account(1, "Card", AccountType.DEBIT_CARD, "payments", 0)
    private val cash = Account(2, "Cash", AccountType.CASH, "payments", 0)
    private val me = Person(1, "Me", 0)
    private val transport = Category(10, "Transport", CategoryType.EXPENSE, icon = "local_taxi", color = 0xFF111111)
    private val taxi = Category(11, "Taxi", CategoryType.EXPENSE, parentId = 10, icon = "local_taxi", color = 0xFF111111)
    private val bolt = Merchant(1, "Bolt")

    private fun row(
        amount: Long = 770,
        kind: TransactionKind = TransactionKind.EXPENSE,
        category: Category? = taxi,
        parent: Category? = transport,
        toAccount: Account? = null,
        note: String = "",
    ) = TransactionDetails(
        transaction = Transaction(1, kind, Money(amount), LocalDate.of(2026, 10, 31), accountId = card.id, toAccountId = toAccount?.id, note = note),
        account = card,
        toAccount = toAccount,
        category = category,
        parentCategory = parent,
        person = me,
        merchant = bolt,
    )

    @Test fun `search finds merchant, category, note and amount`() {
        val r = row(amount = 123450, note = "airport")
        assertThat(HistoryFilter(query = "BOLT").matches(r)).isTrue()
        assertThat(HistoryFilter(query = "taxi").matches(r)).isTrue()
        assertThat(HistoryFilter(query = "airport").matches(r)).isTrue()
        assertThat(HistoryFilter(query = "1234.50").matches(r)).isTrue()
        assertThat(HistoryFilter(query = "1234,5").matches(r)).isTrue()
        assertThat(HistoryFilter(query = "zara").matches(r)).isFalse()
    }

    @Test fun `category filter includes its sub-categories`() {
        assertThat(HistoryFilter(categoryId = transport.id).matches(row())).isTrue()
        assertThat(HistoryFilter(categoryId = taxi.id).matches(row())).isTrue()
        assertThat(HistoryFilter(categoryId = 99).matches(row())).isFalse()
    }

    @Test fun `account filter finds both sides of a transfer`() {
        val transfer = row(kind = TransactionKind.TRANSFER, category = null, parent = null, toAccount = cash)
        assertThat(HistoryFilter(accountId = cash.id).matches(transfer)).isTrue()
        assertThat(HistoryFilter(accountId = card.id).matches(transfer)).isTrue()
        assertThat(HistoryFilter(accountId = cash.id).matches(row())).isFalse()
    }

    @Test fun `all filters must match`() {
        assertThat(HistoryFilter(personId = me.id, query = "bolt").matches(row())).isTrue()
        assertThat(HistoryFilter(personId = 2, query = "bolt").matches(row())).isFalse()
    }

    @Test fun `total is what was spent, leaving out income and transfers`() {
        val rows = listOf(row(770), row(1000, TransactionKind.INCOME), row(500, TransactionKind.TRANSFER, toAccount = cash))
        assertThat(rows.headlineTotal()).isEqualTo(Money(770))
    }

    @Test fun `total falls back to income when nothing was spent`() {
        assertThat(listOf(row(1000, TransactionKind.INCOME)).headlineTotal()).isEqualTo(Money(1000))
    }

    @Test fun `the eye leaves spending visible and hides the rest`() {
        assertThat(row(kind = TransactionKind.EXPENSE).isSensitive).isFalse()
        assertThat(row(kind = TransactionKind.INCOME).isSensitive).isTrue()
        assertThat(row(kind = TransactionKind.TRANSFER).isSensitive).isTrue()
        assertThat(row(kind = TransactionKind.ADJUSTMENT).isSensitive).isTrue()
    }

    @Test fun `a total counts as spending only when something was spent`() {
        assertThat(listOf(row(770), row(1000, TransactionKind.INCOME)).hasSpending()).isTrue()
        assertThat(listOf(row(1000, TransactionKind.INCOME)).hasSpending()).isFalse()
    }

    @Test fun `amounts carry their sign`() {
        assertThat(row(770).signedAmount()).isEqualTo("−₼7.70")
        assertThat(row(5000, TransactionKind.INCOME).signedAmount()).isEqualTo("+₼50.00")
        assertThat(row(500, TransactionKind.TRANSFER).signedAmount()).isEqualTo("₼5.00")
    }

    @Test fun `rows describe themselves`() {
        assertThat(row().title).isEqualTo("Bolt")
        assertThat(row().meta).isEqualTo("Transport · Taxi · Card")
        assertThat(row(kind = TransactionKind.TRANSFER, toAccount = cash).meta).isEqualTo("Card → Cash")
    }
}
