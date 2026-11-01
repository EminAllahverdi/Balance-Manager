package com.eminallahverdi.balance.feature.confirm

import com.eminallahverdi.balance.core.data.PendingItem
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.EntryGuess
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.PendingEntry
import com.eminallahverdi.balance.core.model.PendingStatus
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.TransactionKind
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class ConfirmRowTest {
    private val entry = PendingEntry(1, "x", "Bank", Money(12_40), TransactionKind.EXPENSE, "BRAVO SUPERMARKET", Instant.EPOCH)
    private val groceries = Category(1, "Groceries", CategoryType.EXPENSE, icon = "shopping_cart", color = 0)
    private val apps = Category(2, "Apps", CategoryType.EXPENSE, parentId = 3, icon = "apps", color = 0)
    private val subscriptions = Category(3, "Subscriptions", CategoryType.EXPENSE, icon = "subscriptions", color = 0)
    private val card = Account(1, "Card", AccountType.DEBIT_CARD, "payments", 0)
    private val me = Person(1, "Me", 0)

    private fun row(
        merchantName: String = "Bravo",
        category: Category? = groceries,
        parent: Category? = null,
        account: Account? = card,
        status: PendingStatus = PendingStatus.WAITING,
    ) = ConfirmRow(
        PendingItem(entry.copy(status = status), EntryGuess(merchantName, category?.id, me.id, account?.id, isKnown = false)),
        category,
        parent,
        me,
        account,
    )

    @Test fun `the title reads from the category down to the merchant`() {
        assertThat(row().title).isEqualTo("Groceries · Bravo")
        assertThat(row("Spotify", apps, subscriptions).title).isEqualTo("Subscriptions · Apps · Spotify")
    }

    @Test fun `a merchant with no category yet shows only its name, and asks for one`() {
        assertThat(row(category = null).title).isEqualTo("Bravo")
        assertThat(row(category = null).caption).isEqualTo("Pick a category")
    }

    @Test fun `a payment with no merchant still has a title`() {
        assertThat(row("", category = null).title).isEqualTo("Bank payment")
    }

    @Test fun `money coming in isn't called a payment`() {
        val incoming = row("", category = null)
        val item = incoming.item.copy(entry = entry.copy(kind = TransactionKind.INCOME))
        assertThat(incoming.copy(item = item).title).isEqualTo("Money received")
    }

    @Test fun `only an entry with a category and an account can be confirmed in one tap`() {
        assertThat(row().canConfirm).isTrue()
        assertThat(row(category = null).canConfirm).isFalse()
        assertThat(row(account = null).canConfirm).isFalse()
    }

    @Test fun `only waiting entries count as new`() {
        val state = ConfirmUiState(rows = listOf(row(), row(status = PendingStatus.ADDED)))
        assertThat(state.waiting).isEqualTo(1)
    }

    @Test fun `the caption shows who and from where`() {
        assertThat(row().caption).isEqualTo("Me · Card")
    }
}
