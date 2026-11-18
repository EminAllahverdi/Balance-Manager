package com.eminallahverdi.balance.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.designsystem.theme.CategoryPalette
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
    /** How the list's spending splits, by category or by shop as [ringBy] says. Null when none of it is spending. */
    val ring: List<RingSlice>? = null,
    val ringBy: RingBy = RingBy.CATEGORY,
    /** The ring by category splits the picked category into its sub-categories, so its switch says so. */
    val ringOfSubCategories: Boolean = false,
    /** The picked category's name, a sub-category's too, which the filter list of top-level ones can't give. */
    val categoryName: String? = null,
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

    private val filter = MutableStateFlow(
        initialFilter(savedState[ARG_MONTH], savedState[ARG_CATEGORY], savedState[ARG_PERSON], savedState[ARG_KIND], savedState[ARG_FROM], savedState[ARG_TO], savedState[ARG_ACCOUNT]),
    )

    private val rows = filter.map { it.range }.distinctUntilChanged().flatMapLatest { transactions.observeDetails(it) }

    /** What the ring splits spending by. It stays as picked while the filters change, until the screen goes. */
    private val ringBy = MutableStateFlow(RingBy.CATEGORY)

    val state: StateFlow<HistoryUiState> = combine(
        rows,
        combine(filter, ringBy) { f, by -> f to by },
        combine(catalog.people, catalog.categories, catalog.accounts) { p, c, a -> Triple(p, c, a) },
        prefs.preferences,
    ) { all, (f, by), (people, categories, accounts), p ->
        val matching = all.filter(f::matches)
        val picked = categories.firstOrNull { it.id == f.categoryId }
        HistoryUiState(
            filter = f,
            days = matching.byDay(),
            count = matching.size,
            total = matching.headlineTotal(),
            totalIsSpending = matching.hasSpending(),
            categoryName = picked?.name,
            ring = when (by) {
                RingBy.CATEGORY -> matching.categoryRing(picked, categories, CategoryPalette, REST_COLOR)
                RingBy.SHOP -> matching.shopRing(CategoryPalette, REST_COLOR)
            },
            ringBy = by,
            ringOfSubCategories = splitsIntoSubCategories(picked, categories),
            // Home still lists what's archived if it has spending, so the filter can name one.
            people = people.filter { !it.isArchived || it.id == f.personId },
            categories = categories.filter { it.parentId == null && (!it.isArchived || it.id == f.categoryId) },
            accounts = accounts.filterNot { it.isArchived },
            hideAmounts = p.hideAmountsInApp,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setMonth(month: YearMonth?) = filter.update { it.copy(month = month, span = null) }
    fun setQuery(query: String) = filter.update { it.copy(query = query) }
    fun setPerson(id: Long?) = filter.update { it.copy(personId = id) }
    fun setCategory(id: Long?) = filter.update { it.copy(categoryId = id) }
    fun setAccount(id: Long?) = filter.update { it.copy(accountId = id) }
    fun setRingBy(by: RingBy) = ringBy.update { by }

    /** A part of the ring tapped: the list narrows to that category, or to that shop through the search. */
    fun narrowTo(slice: RingSlice) {
        slice.categoryId?.let { id -> filter.update { it.copy(categoryId = id) } }
        slice.shop?.let { name -> filter.update { it.copy(query = name) } }
    }

    fun clearFilters() = filter.update { it.cleared() }
    fun delete(id: Long) { viewModelScope.launch { transactions.delete(id) } }

    companion object {
        /** The ring's part for everything too small to show on its own. */
        private const val REST_COLOR = 0xFF8A94A3
        const val ARG_MONTH = "month"
        const val ARG_CATEGORY = "categoryId"
        const val ARG_PERSON = "personId"
        const val ARG_KIND = "kind"
        const val ARG_FROM = "from"
        const val ARG_TO = "to"
        const val ARG_ACCOUNT = "accountId"
    }
}
