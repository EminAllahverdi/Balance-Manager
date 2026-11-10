package com.eminallahverdi.balance.feature.confirm

import com.eminallahverdi.balance.core.common.rawText
import com.eminallahverdi.balance.core.common.uiText
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
        assertThat(row().title).isEqualTo(rawText("Groceries · Bravo"))
        assertThat(row("Spotify", apps, subscriptions).title).isEqualTo(rawText("Subscriptions · Apps · Spotify"))
    }

    @Test fun `a merchant with no category yet shows only its name, and asks for one`() {
        assertThat(row(category = null).title).isEqualTo(rawText("Bravo"))
        assertThat(row(category = null).caption).containsExactly(uiText(R.string.confirm_pick_a_category))
    }

    @Test fun `a payment with no merchant still has a title`() {
        assertThat(row("", category = null).title).isEqualTo(uiText(R.string.confirm_bank_payment))
    }

    @Test fun `money coming in isn't called a payment`() {
        val incoming = row("", category = null)
        val item = incoming.item.copy(entry = entry.copy(kind = TransactionKind.INCOME))
        assertThat(incoming.copy(item = item).title).isEqualTo(uiText(R.string.confirm_money_received))
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
        assertThat(row().caption).containsExactly(rawText("Me"), rawText("Card")).inOrder()
    }

    private val wallet = Account(2, "Cash", AccountType.CASH, "account_balance_wallet", 0)

    private fun withdrawal(toAccount: Account?) = ConfirmRow(
        PendingItem(entry.copy(kind = TransactionKind.TRANSFER), EntryGuess("", null, null, card.id, isKnown = false, toAccountId = toAccount?.id)),
        category = null,
        parent = null,
        person = null,
        account = card,
        toAccount = toAccount,
    )

    @Test fun `a withdrawal shows both accounts and needs no category`() {
        val row = withdrawal(wallet)
        assertThat(row.title).isEqualTo(uiText(R.string.confirm_cash_withdrawal))
        assertThat(row.caption).containsExactly(rawText("Card → Cash"))
        assertThat(row.canConfirm).isTrue()
    }

    @Test fun `a withdrawal with no cash account goes through Edit`() {
        val row = withdrawal(null)
        assertThat(row.caption).containsExactly(uiText(R.string.confirm_pick_account_to_move))
        assertThat(row.canConfirm).isFalse()
    }
}
