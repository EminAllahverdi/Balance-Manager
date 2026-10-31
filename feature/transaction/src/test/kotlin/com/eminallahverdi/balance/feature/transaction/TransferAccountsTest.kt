package com.eminallahverdi.balance.feature.transaction

import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.TransactionKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransferAccountsTest {
    private val accounts = listOf(1L, 2L, 3L).map { Account(it, "a$it", AccountType.CASH, "payments", 0) }
    private val transfer = QuickAddUiState(kind = TransactionKind.TRANSFER, amountText = "25", accounts = accounts, accountId = 1, toAccountId = 2)

    @Test fun `picking the other side swaps them`() {
        val swapped = transfer.withAccount(2)
        assertThat(swapped.accountId).isEqualTo(2L)
        assertThat(swapped.toAccountId).isEqualTo(1L)
        assertThat(swapped.canSave).isTrue()
    }

    @Test fun `picking a third account leaves To alone`() {
        val moved = transfer.withAccount(3)
        assertThat(moved.accountId).isEqualTo(3L)
        assertThat(moved.toAccountId).isEqualTo(2L)
    }

    @Test fun `matching accounts are separated`() {
        val fixed = transfer.copy(accountId = 2).withDistinctAccounts()
        assertThat(fixed.toAccountId).isEqualTo(1L)
        assertThat(fixed.canSave).isTrue()
    }

    @Test fun `different accounts are left alone`() {
        assertThat(transfer.withDistinctAccounts()).isEqualTo(transfer)
    }
}
