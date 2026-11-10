package com.eminallahverdi.balance.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.common.UiText
import com.eminallahverdi.balance.core.common.pluralText
import com.eminallahverdi.balance.core.common.uiText
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.balanceChangesFromDeleting
import com.eminallahverdi.balance.core.model.balanceFrom
import com.eminallahverdi.balance.core.model.transfersBetween
import com.eminallahverdi.balance.feature.history.HistoryDay
import com.eminallahverdi.balance.feature.history.byDay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeleteAccountUiState(
    val loading: Boolean = true,
    /** Null once it's been deleted. */
    val account: Account? = null,
    val rows: List<TransactionDetails> = emptyList(),
    val days: List<HistoryDay> = emptyList(),
    val payments: List<RecurringPayment> = emptyList(),
    /** The accounts in use, which can take these entries and whose balances matter here. */
    val targets: List<Account> = emptyList(),
    val hideAmounts: Boolean = false,
) {
    val isGone: Boolean get() = !loading && account == null

    /** Nothing depends on it, so deleting it needs no warning. */
    val isUnused: Boolean get() = rows.isEmpty() && payments.isEmpty()

    private val count: Int get() = rows.size

    val intro: UiText
        get() = when {
            isUnused -> uiText(R.string.accounts_delete_intro_unused)
            rows.isEmpty() -> uiText(R.string.accounts_delete_intro_payments)
            else -> pluralText(R.plurals.accounts_delete_intro_rows, count, count)
        }

    val deleteLabel: UiText get() = if (rows.isEmpty()) uiText(R.string.accounts_delete_account) else pluralText(R.plurals.accounts_delete_account_and, count, count)

    val deleteTitle: UiText
        get() = if (rows.isEmpty()) uiText(R.string.accounts_delete_title, account?.name.orEmpty()) else pluralText(R.plurals.accounts_delete_title_rows, count, count, account?.name.orEmpty())

    /** What goes with the account, and which balances change, because money that moved to or from it goes too. */
    val deleteWarning: List<UiText>
        get() {
            val id = account?.id ?: return emptyList()
            val changes = balanceChangesFromDeleting(id, rows.map { it.transaction })
            return buildList {
                if (rows.isNotEmpty()) add(uiText(R.string.accounts_past_months_change))
                targets.forEach { other -> changes[other.id]?.let { add(balanceLine(other, it)) } }
                paymentsLine(R.string.accounts_payment_goes_too, R.plurals.accounts_payments_go_too)?.let(::add)
                add(uiText(R.string.accounts_cant_be_undone))
            }
        }

    fun moveTitle(target: Account): UiText =
        if (rows.isEmpty()) uiText(R.string.accounts_move_title, target.name) else pluralText(R.plurals.accounts_move_title_rows, count, count, target.name)

    /** A move can't lose money: [target] takes on this account's balance, and only what would then go from it to itself goes. */
    fun moveWarning(target: Account): List<UiText> {
        val account = account ?: return emptyList()
        val entries = rows.map { it.transaction }
        val gained = balanceFrom(account.id, entries)
        val between = transfersBetween(account.id, target.id, entries).size
        return listOfNotNull(
            balanceLine(target, gained).takeIf { !gained.isZero },
            pluralText(R.plurals.accounts_transfers_between_go, between, between, account.name, target.name).takeIf { between > 0 },
            paymentsLine(R.string.accounts_payment_moves_too, R.plurals.accounts_payments_move_too),
        ).ifEmpty { listOf(uiText(R.string.accounts_nothing_else_changes)) }
    }

    /** The eye hides what's in an account, so it only says that this one changes. */
    private fun balanceLine(other: Account, change: Money): UiText =
        if (hideAmounts) uiText(R.string.accounts_balance_changes_too, other.name) else uiText(R.string.accounts_balance_changes_by, other.name, change.withSign())

    private fun Money.withSign(): String = if (isNegative) MoneyFormat.format(this, signed = true) else "+" + MoneyFormat.format(this)

    private fun paymentsLine(one: Int, many: Int): UiText? = when (payments.size) {
        0 -> null
        1 -> uiText(one, payments.single().name)
        else -> pluralText(many, payments.size, payments.size)
    }
}

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val transactions: TransactionRepository,
    recurring: RecurringRepository,
    prefs: UserPreferencesRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val accountId: Long = checkNotNull(savedState[ARG_ACCOUNT])

    val state: StateFlow<DeleteAccountUiState> = combine(
        transactions.observeForAccount(accountId),
        catalog.accounts,
        recurring.payments,
        prefs.preferences,
    ) { rows, accounts, payments, p ->
        DeleteAccountUiState(
            loading = false,
            account = accounts.firstOrNull { it.id == accountId },
            rows = rows,
            days = rows.byDay(),
            payments = payments.filter { it.accountId == accountId },
            targets = accounts.filter { !it.isArchived && it.id != accountId },
            hideAmounts = p.hideAmountsInApp,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeleteAccountUiState())

    fun moveAll(target: Account) { viewModelScope.launch { catalog.moveAccountEntries(accountId, target.id) } }
    fun deleteAccount() { viewModelScope.launch { catalog.deleteAccount(accountId) } }
    fun deleteEntry(id: Long) { viewModelScope.launch { transactions.delete(id) } }

    companion object {
        const val ARG_ACCOUNT = "accountId"
    }
}
