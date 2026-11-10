package com.eminallahverdi.balance.widget

import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.datastore.UserPreferences
import com.eminallahverdi.balance.core.datastore.WidgetBalanceMode
import com.eminallahverdi.balance.core.designsystem.theme.DarkBtColors
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountBalance
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.Transaction
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.TransactionKind
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/** The text width of a 2×1 on the owner's phone. */
private val ROOM = 106.dp

class WidgetStateTest {
    private val today = LocalDate.of(2026, 11, 1)
    private val card = account(1, "Card")
    private val cash = account(2, "Cash")
    private val savings = account(3, "Savings", showOnWidget = false)
    private val balances = listOf(AccountBalance(card, Money(100_00)), AccountBalance(cash, Money(50_50)), AccountBalance(savings, Money(9_000_00)))

    private fun balance(mode: WidgetBalanceMode, secondsLeft: Int = 0) =
        balanceWidgetState(balances, UserPreferences(widgetBalanceMode = mode), secondsLeft, DarkBtColors)

    @Test fun `balance counts only accounts shown on the widget`() {
        val state = balance(WidgetBalanceMode.ALWAYS_SHOW)
        assertThat(state.amount).isEqualTo("₼150.50")
        assertThat(state.caption).isEqualTo(BalanceCaption.Accounts("Card + Cash"))
    }

    @Test fun `a negative balance keeps its sign`() {
        val state = balanceWidgetState(listOf(AccountBalance(card, Money(-4_40))), UserPreferences(widgetBalanceMode = WidgetBalanceMode.ALWAYS_SHOW), 0, DarkBtColors)
        assertThat(state.amount).isEqualTo("−₼4.40")
    }

    @Test fun `tap and fingerprint stay hidden until revealed`() {
        for (mode in listOf(WidgetBalanceMode.TAP_TO_SHOW, WidgetBalanceMode.FINGERPRINT)) {
            assertThat(balance(mode).revealed).isFalse()
            assertThat(balance(mode).amount).isEqualTo(MoneyFormat.HIDDEN)
        }
        assertThat(balance(WidgetBalanceMode.TAP_TO_SHOW).caption).isEqualTo(BalanceCaption.TapToShow)
        assertThat(balance(WidgetBalanceMode.FINGERPRINT).caption).isEqualTo(BalanceCaption.TouchToShow)
    }

    @Test fun `a reveal shows the amount and counts down`() {
        val state = balance(WidgetBalanceMode.FINGERPRINT, secondsLeft = 4)
        assertThat(state.revealed).isTrue()
        assertThat(state.amount).isEqualTo("₼150.50")
        assertThat(state.caption).isEqualTo(BalanceCaption.HidesIn(4))
    }

    @Test fun `always hide wins over a reveal still running`() {
        val state = balance(WidgetBalanceMode.ALWAYS_HIDE, secondsLeft = 4)
        assertThat(state.amount).isEqualTo(MoneyFormat.HIDDEN)
        assertThat(state.caption).isEqualTo(BalanceCaption.Hidden)
    }

    @Test fun `an ordinary balance stays at full size`() {
        assertThat(amountTextSize("₼1,234.56", ROOM, fontScale = 1f).value).isEqualTo(20f)
        assertThat(amountTextSize(MoneyFormat.HIDDEN, ROOM, fontScale = 1f).value).isEqualTo(20f)
    }

    @Test fun `a long balance shrinks to fit the room`() {
        val amount = "−₼12,345,678.90"
        val size = amountTextSize(amount, ROOM, fontScale = 1f).value
        assertThat(size).isLessThan(20f)
        assertThat(size * amount.length * 0.46f).isAtMost(ROOM.value)
    }

    @Test fun `a larger system font leaves the amount the same width`() {
        val amount = "₼12,345.00"
        assertThat(amountTextSize(amount, ROOM, fontScale = 1.5f).value).isLessThan(amountTextSize(amount, ROOM, fontScale = 1f).value)
    }

    @Test fun `today counts expenses only`() {
        val items = listOf(details(TransactionKind.EXPENSE, 12_40), details(TransactionKind.EXPENSE, 4_40), details(TransactionKind.INCOME, 500_00), details(TransactionKind.TRANSFER, 20_00))
        val state = todayWidgetState(today, items, emptyList(), DarkBtColors)
        assertThat(state.spent).isEqualTo(Money(16_80))
        assertThat(state.entries).isEqualTo(2)
    }

    @Test fun `one expense counts once`() {
        val state = todayWidgetState(today, listOf(details(TransactionKind.EXPENSE, 1_00)), emptyList(), DarkBtColors)
        assertThat(state.entries).isEqualTo(1)
    }

    @Test fun `one-tap categories are the first four active expense categories`() {
        val categories = listOf(
            category(1, "Salary", CategoryType.INCOME),
            category(2, "Groceries"),
            category(3, "Fuel", parentId = 2),
            category(4, "Pets", isArchived = true),
            category(5, "Cafe"),
            category(6, "Transport"),
            category(7, "Work"),
            category(8, "Car"),
        )
        val names = todayWidgetState(today, emptyList(), categories, DarkBtColors).categories.map { it.name }
        assertThat(names).containsExactly("Groceries", "Cafe", "Transport", "Work").inOrder()
    }

    private fun account(id: Long, name: String, showOnWidget: Boolean = true) =
        Account(id, name, AccountType.DEBIT_CARD, icon = "credit_card", color = 0xFF8A94A3, showOnWidget = showOnWidget)

    private fun category(id: Long, name: String, type: CategoryType = CategoryType.EXPENSE, parentId: Long? = null, isArchived: Boolean = false) =
        Category(id, name, type, parentId, icon = "apps", color = 0xFF8A94A3, isArchived = isArchived)

    private fun details(kind: TransactionKind, minor: Long) = TransactionDetails(
        transaction = Transaction(id = 0, kind = kind, amount = Money(minor), date = today, accountId = card.id),
        account = card,
        toAccount = null,
        category = null,
        parentCategory = null,
        person = null,
        merchant = null,
    )
}
