package com.eminallahverdi.balance.feature.accounts

import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountBalance
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Money
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccountsStateTest {
    private fun balance(id: Long, minor: Long, inTotal: Boolean = true) =
        AccountBalance(Account(id, "a$id", AccountType.CASH, "payments", 0, includeInTotal = inTotal), Money(minor))

    @Test fun `total leaves out accounts not in total`() {
        val state = AccountsUiState(balances = listOf(balance(1, 1000), balance(2, -250), balance(3, 9999, inTotal = false)))
        assertThat(state.total).isEqualTo(Money(750))
        assertThat(state.included).hasSize(2)
    }

    @Test fun `default falls back to the first account like quick add does`() {
        val balances = listOf(balance(1, 0), balance(2, 0))
        assertThat(AccountsUiState(balances = balances).defaultAccountId).isEqualTo(1L)
        assertThat(AccountsUiState(balances = balances, preferredAccountId = 2).defaultAccountId).isEqualTo(2L)
        assertThat(AccountsUiState(balances = balances, preferredAccountId = 9).defaultAccountId).isEqualTo(1L)
    }

    @Test fun `the last account can't be removed or transferred from`() {
        val one = AccountsUiState(balances = listOf(balance(1, 0)))
        assertThat(one.canRemoveAccount).isFalse()
        assertThat(one.canTransfer).isFalse()
        val two = AccountsUiState(balances = listOf(balance(1, 0), balance(2, 0)))
        assertThat(two.canRemoveAccount).isTrue()
        assertThat(two.canTransfer).isTrue()
    }

    @Test fun `input keeps only what can be an amount`() {
        assertThat(cleanAmountInput("1 234,567")).isEqualTo("1234.56")
        assertThat(cleanAmountInput("-12.5x")).isEqualTo("-12.5")
        assertThat(cleanAmountInput("1.2.3")).isEqualTo("1.23")
        assertThat(cleanAmountInput("5-")).isEqualTo("5")
    }

    @Test fun `sign flips both ways`() {
        assertThat(flipSign("12.50")).isEqualTo("-12.50")
        assertThat(flipSign("-12.50")).isEqualTo("12.50")
        assertThat(flipSign("")).isEqualTo("-")
    }

    @Test fun `a sign or separator alone is not an amount`() {
        assertThat(parseAmountInput("")).isNull()
        assertThat(parseAmountInput("-")).isNull()
        assertThat(parseAmountInput(".")).isNull()
        assertThat(parseAmountInput("0")).isEqualTo(Money.ZERO)
        assertThat(parseAmountInput("-4.40")).isEqualTo(Money(-440))
    }
}
