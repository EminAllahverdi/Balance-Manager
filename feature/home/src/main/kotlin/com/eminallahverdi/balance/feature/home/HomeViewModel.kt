package com.eminallahverdi.balance.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.AccountBalance
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.MonthSummary
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.SpendingCalculator
import com.eminallahverdi.balance.core.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class HomeView { CATEGORY, PERSON }

data class HomeUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val view: HomeView = HomeView.CATEGORY,
    val hideAmounts: Boolean = false,
    val total: Money = Money.ZERO,
    val balances: List<AccountBalance> = emptyList(),
    val summary: MonthSummary? = null,
    val expandedPersonId: Long? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    catalog: CatalogRepository,
    transactions: TransactionRepository,
    prefs: UserPreferencesRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val view = MutableStateFlow(HomeView.CATEGORY)
    private val expanded = MutableStateFlow<Long?>(null)
    private val reveal = MutableStateFlow(false)

    private val summary = month.flatMapLatest { m ->
        val range = DateRange.month(m)
        combine(transactions.observeDetails(range), catalog.people) { details, people ->
            SpendingCalculator.summarize(range, details, people.filterNot { it.isArchived })
        }
    }

    val state: StateFlow<HomeUiState> = combine(
        summary,
        transactions.balances,
        prefs.preferences,
        combine(month, view, expanded, reveal) { m, v, e, r -> Quad(m, v, e, r) },
    ) { s, balances, p, ui ->
        val included = balances.filter { it.account.includeInTotal }
        HomeUiState(
            loading = false,
            month = ui.month,
            view = ui.view,
            hideAmounts = p.hideAmountsInApp && !ui.reveal,
            total = included.map { it.balance }.sum(),
            balances = included,
            summary = s,
            expandedPersonId = ui.expanded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { if (it < YearMonth.now()) it.plusMonths(1) else it }
    fun setView(v: HomeView) = view.update { v }
    fun togglePerson(id: Long) = expanded.update { if (it == id) null else id }
    fun toggleReveal() = reveal.update { !it }

    private data class Quad(val month: YearMonth, val view: HomeView, val expanded: Long?, val reveal: Boolean)
}
