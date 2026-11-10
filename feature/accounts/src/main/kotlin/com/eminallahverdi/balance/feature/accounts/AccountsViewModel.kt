package com.eminallahverdi.balance.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.designsystem.theme.CategoryPalette
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountBalance
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.RecurringPayment
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
private const val SUMMARY_NAMES = 3

sealed interface AccountsSheet {
    /** [isDefault] is whether new expenses start on it. It can only be turned on: one account is always the default. */
    data class Editor(val account: Account, val isNew: Boolean, val isDefault: Boolean, val wasDefault: Boolean) : AccountsSheet
    data class Adjust(val accountId: Long, val amountText: String = "") : AccountsSheet
}

/** The default just moved to [account] from [previousId], which Undo puts back. */
data class DefaultChange(val account: Account, val previousId: Long?)

data class AccountsUiState(
    val balances: List<AccountBalance> = emptyList(),
    val archived: List<Account> = emptyList(),
    val transfers: List<TransactionDetails> = emptyList(),
    val preferredAccountId: Long? = null,
    val hideAmounts: Boolean = false,
    val sheet: AccountsSheet? = null,
    val defaultChange: DefaultChange? = null,
    val recurring: List<RecurringPayment> = emptyList(),
) {
    /** Up to three names and how many, or None yet, for the row that opens them. */
    val recurringSummary: String
        get() = if (recurring.isEmpty()) "None yet" else recurring.take(SUMMARY_NAMES).joinToString(", ") { it.name } + " · ${recurring.size}"

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
    recurring: RecurringRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    private val sheet = MutableStateFlow<AccountsSheet?>(null)
    private val defaultChange = MutableStateFlow<DefaultChange?>(null)

    val state: StateFlow<AccountsUiState> = combine(
        transactions.balances,
        catalog.accounts,
        transactions.observeTransfers(RECENT_TRANSFERS),
        prefs.preferences,
        combine(sheet, defaultChange, recurring.payments) { open, change, payments -> Triple(open, change, payments) },
    ) { balances, accounts, transfers, p, (open, change, payments) ->
        AccountsUiState(
            balances = balances,
            archived = accounts.filter { it.isArchived },
            transfers = transfers,
            preferredAccountId = p.defaultExpenseAccountId,
            hideAmounts = p.hideAmountsInApp,
            sheet = open,
            defaultChange = change,
            recurring = payments.filterNot { it.isFinished },
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
        // The first account there is starts as the default, since there's nothing else to start on.
        val first = state.value.balances.isEmpty()
        sheet.update { AccountsSheet.Editor(draft, isNew = true, isDefault = first, wasDefault = first) }
    }

    fun edit(account: Account) {
        val isDefault = account.id == state.value.defaultAccountId
        sheet.update { AccountsSheet.Editor(account, isNew = false, isDefault = isDefault, wasDefault = isDefault) }
    }

    fun update(account: Account) = sheet.update { (it as? AccountsSheet.Editor)?.copy(account = account) ?: it }

    /** Only on: the default moves to another account by turning it on there. */
    fun setEditorDefault(on: Boolean) = sheet.update { editor -> (editor as? AccountsSheet.Editor)?.let { if (it.wasDefault) it else it.copy(isDefault = on) } ?: editor }

    fun saveEditor() {
        val draft = sheet.value as? AccountsSheet.Editor ?: return
        sheet.update { null }
        viewModelScope.launch {
            val id = catalog.saveAccount(draft.account.copy(name = draft.account.name.trim()))
            val saved = if (draft.isNew) id else draft.account.id
            if (draft.isDefault && !draft.wasDefault) prefs.setDefaultExpenseAccount(saved)
        }
    }

    /** Removes what's being edited. An account with transactions is archived instead, so history keeps it. */
    fun removeEditing() {
        val draft = sheet.value as? AccountsSheet.Editor ?: return
        sheet.update { null }
        viewModelScope.launch { catalog.removeAccount(draft.account) }
    }

    fun restore(account: Account) { viewModelScope.launch { catalog.saveAccount(account.copy(isArchived = false)) } }

    /** New expenses start on [account] from now on. What it was before is kept a moment, for Undo. */
    fun makeDefault(account: Account) {
        val previous = state.value.defaultAccountId
        if (previous == account.id) return
        defaultChange.value = DefaultChange(account, previous)
        viewModelScope.launch { prefs.setDefaultExpenseAccount(account.id) }
    }

    fun undoDefault() {
        val change = defaultChange.value ?: return
        defaultChange.value = null
        change.previousId?.let { id -> viewModelScope.launch { prefs.setDefaultExpenseAccount(id) } }
    }

    fun dismissDefaultChange() = defaultChange.update { null }

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
