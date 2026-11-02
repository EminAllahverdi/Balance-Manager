package com.eminallahverdi.balance.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.common.MoneyFormat
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

    val transactionCount: String get() = if (rows.size == 1) "1 transaction" else "${rows.size} transactions"

    val intro: String
        get() = when {
            isUnused -> "Nothing uses this account any more, so deleting it changes nothing else."
            rows.isEmpty() -> "Only these recurring payments use this account."
            else -> "$transactionCount ${if (rows.size == 1) "uses" else "use"} this account. Tap one to change it, or move them all to another account."
        }

    val deleteLabel: String get() = if (rows.isEmpty()) "Delete account" else "Delete account and $transactionCount"

    val deleteTitle: String get() = if (rows.isEmpty()) "Delete ${account?.name}?" else "Delete ${account?.name} and $transactionCount?"

    /** What goes with the account, and which balances change, because money that moved to or from it goes too. */
    val deleteWarning: String
        get() {
            val id = account?.id ?: return ""
            val changes = balanceChangesFromDeleting(id, rows.map { it.transaction })
            return buildList {
                if (rows.isNotEmpty()) add("Past months change.")
                targets.forEach { other -> changes[other.id]?.let { add(balanceLine(other, it)) } }
                paymentsLine("go too", "goes too")?.let(::add)
                add("This can't be undone.")
            }.joinToString(" ")
        }

    fun moveTitle(target: Account): String = if (rows.isEmpty()) "Move to ${target.name}?" else "Move $transactionCount to ${target.name}?"

    /** A move can't lose money: [target] takes on this account's balance, and only what would then go from it to itself goes. */
    fun moveWarning(target: Account): String {
        val id = account?.id ?: return ""
        val entries = rows.map { it.transaction }
        val gained = balanceFrom(id, entries)
        val between = transfersBetween(id, target.id, entries).size
        return listOfNotNull(
            balanceLine(target, gained).takeIf { !gained.isZero },
            when (between) {
                0 -> null
                1 -> "1 transfer between ${account.name} and ${target.name} goes, because it would be from an account to itself."
                else -> "$between transfers between ${account.name} and ${target.name} go, because they'd be from an account to itself."
            },
            paymentsLine("move too", "moves too"),
        ).joinToString(" ").ifEmpty { "Nothing else changes." }
    }

    /** The eye hides what's in an account, so it only says that this one changes. */
    private fun balanceLine(other: Account, change: Money): String =
        if (hideAmounts) "${other.name}'s balance changes too." else "${other.name}'s balance changes by ${change.withSign()}."

    private fun Money.withSign(): String = if (isNegative) MoneyFormat.format(this, signed = true) else "+" + MoneyFormat.format(this)

    private fun paymentsLine(many: String, one: String): String? = when (payments.size) {
        0 -> null
        1 -> "The recurring payment ${payments.single().name} $one."
        else -> "${payments.size} recurring payments $many."
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
