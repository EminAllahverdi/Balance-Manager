package com.eminallahverdi.balance.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.InsightOverview
import com.eminallahverdi.balance.core.model.InsightPeriod
import com.eminallahverdi.balance.core.model.InsightsCalculator
import com.eminallahverdi.balance.core.model.TransactionDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class InsightsUiState(
    val period: InsightPeriod = InsightPeriod.MONTH,
    val overview: InsightOverview? = null,
    val hideAmounts: Boolean = false,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    catalog: CatalogRepository,
    transactions: TransactionRepository,
    prefs: UserPreferencesRepository,
) : ViewModel() {

    private val period = MutableStateFlow(InsightPeriod.MONTH)
    private val selected = MutableStateFlow<Int?>(null)

    private val loaded = period.flatMapLatest { p ->
        val ranges = InsightsCalculator.periods(p, LocalDate.now())
        transactions.observeDetails(DateRange(ranges.first().start, ranges.last().endInclusive)).map { rows -> Loaded(p, ranges, rows) }
    }

    val state: StateFlow<InsightsUiState> = combine(loaded, catalog.people, selected, prefs.preferences) { data, people, index, p ->
        InsightsUiState(
            period = data.period,
            overview = InsightsCalculator.overview(data.ranges, data.rows, people, index, LocalDate.now()),
            hideAmounts = p.hideAmountsInApp,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun setPeriod(p: InsightPeriod) {
        selected.update { null }
        period.update { p }
    }

    fun select(index: Int) = selected.update { index }

    /** Kept together, so a slow load can't pair one period's rows with another's bars. */
    private data class Loaded(val period: InsightPeriod, val ranges: List<DateRange>, val rows: List<TransactionDetails>)
}
