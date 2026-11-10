package com.eminallahverdi.balance.feature.accounts

import com.eminallahverdi.balance.core.common.pluralText
import com.eminallahverdi.balance.core.common.uiText
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.RecurringFrequency
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.Transaction
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.TransactionKind
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class DeleteAccountStateTest {
    private val old = Account(1, "Old card", AccountType.DEBIT_CARD, "credit_card", 0, isArchived = true)
    private val card = Account(2, "Card", AccountType.DEBIT_CARD, "credit_card", 0)
    private val cash = Account(3, "Cash", AccountType.CASH, "payments", 0)

    private fun row(kind: TransactionKind, minor: Long, from: Account = old, to: Account? = null) = TransactionDetails(
        transaction = Transaction(id = 0, kind = kind, amount = Money(minor), date = LocalDate.of(2026, 11, 2), accountId = from.id, toAccountId = to?.id),
        account = from,
        toAccount = to,
        category = null,
        parentCategory = null,
        person = null,
        merchant = null,
    )

    private fun payment(name: String) = RecurringPayment(
        id = 0, name = name, amount = Money(10_00), accountId = old.id, categoryId = 1, personId = null,
        frequency = RecurringFrequency.MONTHLY, nextDate = LocalDate.of(2026, 12, 1), anchorDay = 1,
    )

    private fun state(rows: List<TransactionDetails> = emptyList(), payments: List<RecurringPayment> = emptyList(), hideAmounts: Boolean = false) =
        DeleteAccountUiState(loading = false, account = old, rows = rows, payments = payments, targets = listOf(card, cash), hideAmounts = hideAmounts)

    @Test fun `the page only closes once the account is really gone`() {
        assertThat(DeleteAccountUiState().isGone).isFalse()
        assertThat(state().isGone).isFalse()
        assertThat(DeleteAccountUiState(loading = false, account = null).isGone).isTrue()
    }

    @Test fun `an account nothing uses says so, and a recurring payment counts as use`() {
        assertThat(state().isUnused).isTrue()
        assertThat(state().intro).isEqualTo(uiText(R.string.accounts_delete_intro_unused))
        assertThat(state(payments = listOf(payment("Rent"))).isUnused).isFalse()
        assertThat(state(rows = listOf(row(TransactionKind.EXPENSE, 5_00))).isUnused).isFalse()
    }

    @Test fun `it counts what's affected`() {
        assertThat(state(rows = listOf(row(TransactionKind.EXPENSE, 5_00))).deleteLabel).isEqualTo(pluralText(R.plurals.accounts_delete_account_and, 1, 1))
        val two = state(rows = listOf(row(TransactionKind.EXPENSE, 5_00), row(TransactionKind.INCOME, 6_00)))
        assertThat(two.deleteTitle).isEqualTo(pluralText(R.plurals.accounts_delete_title_rows, 2, 2, "Old card"))
        assertThat(two.intro).isEqualTo(pluralText(R.plurals.accounts_delete_intro_rows, 2, 2))
    }

    @Test fun `moving says what the other account gains or loses`() {
        val gains = state(rows = listOf(row(TransactionKind.INCOME, 50_00)))
        assertThat(gains.moveWarning(card)).containsExactly(uiText(R.string.accounts_balance_changes_by, "Card", "+₼50.00"))
        val loses = state(rows = listOf(row(TransactionKind.EXPENSE, 7_70)))
        assertThat(loses.moveWarning(card)).containsExactly(uiText(R.string.accounts_balance_changes_by, "Card", "−₼7.70"))
    }

    @Test fun `moving an account that holds nothing changes nothing`() {
        val even = state(rows = listOf(row(TransactionKind.INCOME, 5_00), row(TransactionKind.EXPENSE, 5_00)))
        assertThat(even.moveWarning(card)).containsExactly(uiText(R.string.accounts_nothing_else_changes))
    }

    @Test fun `transfers with the target are called out because they go`() {
        val one = state(rows = listOf(row(TransactionKind.TRANSFER, 10_00, from = old, to = card)))
        assertThat(one.moveWarning(card)).contains(pluralText(R.plurals.accounts_transfers_between_go, 1, 1, "Old card", "Card"))
        assertThat(one.moveWarning(cash).map { it.id }).doesNotContain(R.plurals.accounts_transfers_between_go)
        val two = state(rows = listOf(row(TransactionKind.TRANSFER, 10_00, from = old, to = card), row(TransactionKind.TRANSFER, 10_00, from = card, to = old)))
        assertThat(two.moveWarning(card)).containsExactly(pluralText(R.plurals.accounts_transfers_between_go, 2, 2, "Old card", "Card"))
    }

    @Test fun `recurring payments are named when they move or go`() {
        val one = state(payments = listOf(payment("Rent")))
        assertThat(one.moveWarning(card)).containsExactly(uiText(R.string.accounts_payment_moves_too, "Rent"))
        assertThat(one.deleteWarning).contains(uiText(R.string.accounts_payment_goes_too, "Rent"))
        val two = state(payments = listOf(payment("Rent"), payment("Gym")))
        assertThat(two.moveWarning(card)).containsExactly(pluralText(R.plurals.accounts_payments_move_too, 2, 2))
        assertThat(two.deleteWarning).contains(pluralText(R.plurals.accounts_payments_go_too, 2, 2))
    }

    @Test fun `deleting warns about past months and balances that change`() {
        val warning = state(rows = listOf(row(TransactionKind.TRANSFER, 100_00, from = old, to = card))).deleteWarning
        assertThat(warning).containsExactly(uiText(R.string.accounts_past_months_change), uiText(R.string.accounts_balance_changes_by, "Card", "−₼100.00"), uiText(R.string.accounts_cant_be_undone)).inOrder()
    }

    @Test fun `the eye hides balances but still says one changes`() {
        val hidden = state(rows = listOf(row(TransactionKind.TRANSFER, 100_00, from = old, to = card)), hideAmounts = true)
        assertThat(hidden.deleteWarning).contains(uiText(R.string.accounts_balance_changes_too, "Card"))
        assertThat(hidden.deleteWarning.flatMap { it.args }.joinToString()).doesNotContain("₼")
        assertThat(hidden.moveWarning(cash)).containsExactly(uiText(R.string.accounts_balance_changes_too, "Cash"))
    }
}
