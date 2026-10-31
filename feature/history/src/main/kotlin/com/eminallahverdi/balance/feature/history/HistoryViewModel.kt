package com.eminallahverdi.balance.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.TransactionDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryDay(val date: LocalDate, val total: Money, val totalIsSpending: Boolean, val rows: List<TransactionDetails>)

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter(),
    val days: List<HistoryDay> = emptyList(),
    val count: Int = 0,
    val total: Money = Money.ZERO,
    val totalIsSpending: Boolean = true,
    val shares: List<Pair<Long, Long>> = emptyList(),
    val people: List<Person> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val hideAmounts: Boolean = false,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    catalog: CatalogRepository,
    private val transactions: TransactionRepository,
    prefs: UserPreferencesRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val filter = MutableStateFlow(initialFilter(savedState[ARG_MONTH], savedState[ARG_CATEGORY], savedState[ARG_PERSON]))

    private val rows = filter.map { it.range }.distinctUntilChanged().flatMapLatest { transactions.observeDetails(it) }

    val state: StateFlow<HistoryUiState> = combine(
        rows,
        filter,
        combine(catalog.people, catalog.categories, catalog.accounts) { p, c, a -> Triple(p, c, a) },
        prefs.preferences,
    ) { all, f, (people, categories, accounts), p ->
        val matching = all.filter(f::matches)
        HistoryUiState(
            filter = f,
            days = matching.groupBy { it.transaction.date }.map { (date, day) -> HistoryDay(date, day.headlineTotal(), day.hasSpending(), day) },
            count = matching.size,
            total = matching.headlineTotal(),
            totalIsSpending = matching.hasSpending(),
            shares = matching.categoryShares(),
            // Home still lists what's archived if it has spending, so the filter can name one.
            people = people.filter { !it.isArchived || it.id == f.personId },
            categories = categories.filter { it.parentId == null && (!it.isArchived || it.id == f.categoryId) },
            accounts = accounts.filterNot { it.isArchived },
            hideAmounts = p.hideAmountsInApp,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setMonth(month: YearMonth?) = filter.update { it.copy(month = month) }
    fun setQuery(query: String) = filter.update { it.copy(query = query) }
    fun setPerson(id: Long?) = filter.update { it.copy(personId = id) }
    fun setCategory(id: Long?) = filter.update { it.copy(categoryId = id) }
    fun setAccount(id: Long?) = filter.update { it.copy(accountId = id) }
    fun delete(id: Long) { viewModelScope.launch { transactions.delete(id) } }

    companion object {
        const val ARG_MONTH = "month"
        const val ARG_CATEGORY = "categoryId"
        const val ARG_PERSON = "personId"
    }
}
