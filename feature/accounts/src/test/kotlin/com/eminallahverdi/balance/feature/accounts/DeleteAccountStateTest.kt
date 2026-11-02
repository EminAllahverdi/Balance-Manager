package com.eminallahverdi.balance.feature.accounts

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
        assertThat(state().intro).contains("Nothing uses this account")
        assertThat(state(payments = listOf(payment("Rent"))).isUnused).isFalse()
        assertThat(state(rows = listOf(row(TransactionKind.EXPENSE, 5_00))).isUnused).isFalse()
    }

    @Test fun `it counts what's affected`() {
        assertThat(state(rows = listOf(row(TransactionKind.EXPENSE, 5_00))).deleteLabel).isEqualTo("Delete account and 1 transaction")
        val two = state(rows = listOf(row(TransactionKind.EXPENSE, 5_00), row(TransactionKind.INCOME, 6_00)))
        assertThat(two.deleteTitle).isEqualTo("Delete Old card and 2 transactions?")
        assertThat(two.intro).startsWith("2 transactions use this account.")
    }

    @Test fun `moving says what the other account gains or loses`() {
        val gains = state(rows = listOf(row(TransactionKind.INCOME, 50_00)))
        assertThat(gains.moveWarning(card)).isEqualTo("Card's balance changes by +₼50.00.")
        val loses = state(rows = listOf(row(TransactionKind.EXPENSE, 7_70)))
        assertThat(loses.moveWarning(card)).isEqualTo("Card's balance changes by −₼7.70.")
    }

    @Test fun `moving an account that holds nothing changes nothing`() {
        val even = state(rows = listOf(row(TransactionKind.INCOME, 5_00), row(TransactionKind.EXPENSE, 5_00)))
        assertThat(even.moveWarning(card)).isEqualTo("Nothing else changes.")
    }

    @Test fun `transfers with the target are called out because they go`() {
        val one = state(rows = listOf(row(TransactionKind.TRANSFER, 10_00, from = old, to = card)))
        assertThat(one.moveWarning(card)).contains("1 transfer between Old card and Card goes")
        assertThat(one.moveWarning(cash)).doesNotContain("transfer")
        val two = state(rows = listOf(row(TransactionKind.TRANSFER, 10_00, from = old, to = card), row(TransactionKind.TRANSFER, 10_00, from = card, to = old)))
        assertThat(two.moveWarning(card)).isEqualTo("2 transfers between Old card and Card go, because they'd be from an account to itself.")
    }

    @Test fun `recurring payments are named when they move or go`() {
        val one = state(payments = listOf(payment("Rent")))
        assertThat(one.moveWarning(card)).isEqualTo("The recurring payment Rent moves too.")
        assertThat(one.deleteWarning).contains("The recurring payment Rent goes too.")
        val two = state(payments = listOf(payment("Rent"), payment("Gym")))
        assertThat(two.moveWarning(card)).isEqualTo("2 recurring payments move too.")
        assertThat(two.deleteWarning).contains("2 recurring payments go too.")
    }

    @Test fun `deleting warns about past months and balances that change`() {
        val warning = state(rows = listOf(row(TransactionKind.TRANSFER, 100_00, from = old, to = card))).deleteWarning
        assertThat(warning).isEqualTo("Past months change. Card's balance changes by −₼100.00. This can't be undone.")
    }

    @Test fun `the eye hides balances but still says one changes`() {
        val hidden = state(rows = listOf(row(TransactionKind.TRANSFER, 100_00, from = old, to = card)), hideAmounts = true)
        assertThat(hidden.deleteWarning).contains("Card's balance changes too.")
        assertThat(hidden.deleteWarning).doesNotContain("₼")
        assertThat(hidden.moveWarning(cash)).isEqualTo("Cash's balance changes too.")
    }
}
