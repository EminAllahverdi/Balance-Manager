package com.eminallahverdi.balance.feature.transaction

import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.Transaction
import com.eminallahverdi.balance.core.model.TransactionKind
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class TransferAccountsTest {
    private val accounts = listOf(1L, 2L, 3L).map { Account(it, "a$it", AccountType.CASH, "payments", 0) }
    private val transfer = QuickAddUiState(kind = TransactionKind.TRANSFER, amountText = "25", accounts = accounts, accountId = 1, toAccountId = 2)

    @Test fun `an archived account is only offered to an entry already on it`() {
        val all = accounts + Account(4, "closed", AccountType.CASH, "payments", 0, isArchived = true)
        val onIt = Transaction(id = 9, kind = TransactionKind.EXPENSE, amount = Money(1_00), date = LocalDate.of(2026, 11, 2), accountId = 4)
        val toIt = onIt.copy(kind = TransactionKind.TRANSFER, accountId = 1, toAccountId = 4)
        assertThat(pickableAccounts(all, editing = null).map { it.id }).containsExactly(1L, 2L, 3L)
        assertThat(pickableAccounts(all, onIt).map { it.id }).containsExactly(1L, 2L, 3L, 4L)
        assertThat(pickableAccounts(all, toIt).map { it.id }).containsExactly(1L, 2L, 3L, 4L)
    }

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
