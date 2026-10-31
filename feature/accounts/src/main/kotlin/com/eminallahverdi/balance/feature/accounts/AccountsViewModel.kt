package com.eminallahverdi.balance.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.designsystem.theme.CategoryPalette
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountBalance
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val RECENT_TRANSFERS = 3

sealed interface AccountsSheet {
    data class Editor(val account: Account, val isNew: Boolean) : AccountsSheet
    data class Adjust(val accountId: Long, val amountText: String = "") : AccountsSheet
}

data class AccountsUiState(
    val balances: List<AccountBalance> = emptyList(),
    val archived: List<Account> = emptyList(),
    val transfers: List<TransactionDetails> = emptyList(),
    val preferredAccountId: Long? = null,
    val hideAmounts: Boolean = false,
    val sheet: AccountsSheet? = null,
) {
    val included: List<AccountBalance> get() = balances.filter { it.account.includeInTotal }
    val total: Money get() = included.map { it.balance }.sum()

    /** Quick add falls back to the first account until one is chosen, so the badge does too. */
    val defaultAccountId: Long?
        get() = balances.firstOrNull { it.account.id == preferredAccountId }?.account?.id ?: balances.firstOrNull()?.account?.id

    val canTransfer: Boolean get() = balances.size > 1

    /** Quick add needs an account to save to, so the last one stays. */
    val canRemoveAccount: Boolean get() = balances.size > 1

    fun balanceOf(accountId: Long): Money = balances.firstOrNull { it.account.id == accountId }?.balance ?: Money.ZERO
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val transactions: TransactionRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    private val sheet = MutableStateFlow<AccountsSheet?>(null)

    val state: StateFlow<AccountsUiState> = combine(
        transactions.balances,
        catalog.accounts,
        transactions.observeTransfers(RECENT_TRANSFERS),
        prefs.preferences,
        sheet,
    ) { balances, accounts, transfers, p, open ->
        AccountsUiState(
            balances = balances,
            archived = accounts.filter { it.isArchived },
            transfers = transfers,
            preferredAccountId = p.defaultExpenseAccountId,
            hideAmounts = p.hideAmountsInApp,
            sheet = open,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun newAccount() {
        val draft = Account(
            id = 0,
            name = "",
            type = AccountType.DEBIT_CARD,
            icon = "credit_card",
            color = CategoryPalette.first(),
            sortOrder = state.value.balances.size,
        )
        sheet.update { AccountsSheet.Editor(draft, isNew = true) }
    }

    fun edit(account: Account) = sheet.update { AccountsSheet.Editor(account, isNew = false) }
    fun update(account: Account) = sheet.update { (it as? AccountsSheet.Editor)?.copy(account = account) ?: it }

    fun saveEditor() {
        val draft = sheet.value as? AccountsSheet.Editor ?: return
        sheet.update { null }
        viewModelScope.launch { catalog.saveAccount(draft.account.copy(name = draft.account.name.trim())) }
    }

    /** Removes what's being edited. An account with transactions is archived instead, so history keeps it. */
    fun removeEditing() {
        val draft = sheet.value as? AccountsSheet.Editor ?: return
        sheet.update { null }
        viewModelScope.launch { catalog.removeAccount(draft.account) }
    }

    fun setInTotal(account: Account, included: Boolean) { viewModelScope.launch { catalog.saveAccount(account.copy(includeInTotal = included)) } }
    fun setOnWidget(account: Account, shown: Boolean) { viewModelScope.launch { catalog.saveAccount(account.copy(showOnWidget = shown)) } }
    fun restore(account: Account) { viewModelScope.launch { catalog.saveAccount(account.copy(isArchived = false)) } }

    /** The same setting as Settings' "Default expense account". */
    fun makeDefault(account: Account) { viewModelScope.launch { prefs.setDefaultExpenseAccount(account.id) } }

    fun openAdjust() {
        val account = state.value.defaultAccountId ?: return
        sheet.update { AccountsSheet.Adjust(account) }
    }

    fun selectAdjustAccount(id: Long) = sheet.update { (it as? AccountsSheet.Adjust)?.copy(accountId = id) ?: it }
    fun setAdjustAmount(text: String) = sheet.update { (it as? AccountsSheet.Adjust)?.copy(amountText = cleanAmountInput(text)) ?: it }
    fun flipAdjustSign() = sheet.update { (it as? AccountsSheet.Adjust)?.copy(amountText = flipSign(it.amountText)) ?: it }

    fun saveAdjust() {
        val s = state.value
        val draft = sheet.value as? AccountsSheet.Adjust ?: return
        val target = parseAmountInput(draft.amountText) ?: return
        sheet.update { null }
        viewModelScope.launch { transactions.adjustBalance(draft.accountId, s.balanceOf(draft.accountId), target) }
    }

    fun closeSheet() = sheet.update { null }
}
