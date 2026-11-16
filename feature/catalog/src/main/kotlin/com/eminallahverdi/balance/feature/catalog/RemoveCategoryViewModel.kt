package com.eminallahverdi.balance.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.common.UiText
import com.eminallahverdi.balance.core.common.pluralText
import com.eminallahverdi.balance.core.common.uiText
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.DebtRepository
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where some of a category's entries are filed: the category itself, or one of its sub-categories. */
data class CategoryPart(val category: Category, val count: Int, val total: Money, val isParent: Boolean)

data class RemoveCategoryUiState(
    val loading: Boolean = true,
    /** Null once it's gone. */
    val category: Category? = null,
    val parts: List<CategoryPart> = emptyList(),
    val subCategories: List<Category> = emptyList(),
    val payments: List<RecurringPayment> = emptyList(),
    val debts: Int = 0,
    /** Where its entries can go: another category of its kind, or for a sub-category its parent and the parent's others. */
    val targets: List<Category> = emptyList(),
    val hideAmounts: Boolean = false,
) {
    val isGone: Boolean get() = !loading && category == null
    val count: Int get() = parts.sumOf { it.count }

    /** The eye hides income, so an income category's totals go with it. Spending stays visible, as everywhere. */
    val hidesTotals: Boolean get() = hideAmounts && category?.type == CategoryType.INCOME

    val intro: UiText
        get() {
            val pieces = listOfNotNull(
                pluralText(R.plurals.catalog_n_transactions, count, count).takeIf { count > 0 },
                pluralText(R.plurals.catalog_n_recurring, payments.size, payments.size).takeIf { payments.isNotEmpty() },
                pluralText(R.plurals.catalog_n_debts, debts, debts).takeIf { debts > 0 },
            )
            val uses = when (pieces.size) {
                0 -> pluralText(R.plurals.catalog_n_transactions, 0, 0)
                1 -> pieces[0]
                2 -> uiText(R.string.catalog_list_two, pieces[0], pieces[1])
                else -> uiText(R.string.catalog_list_three, pieces[0], pieces[1], pieces[2])
            }
            return uiText(R.string.catalog_remove_intro, category?.name.orEmpty(), uses)
        }

    fun moveLabel(target: Category): UiText =
        if (count == 0) uiText(R.string.catalog_move_to_named, target.name) else pluralText(R.plurals.catalog_move_n_to, count, count, target.name)
}

@HiltViewModel
class RemoveCategoryViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    transactions: TransactionRepository,
    recurring: RecurringRepository,
    debts: DebtRepository,
    prefs: UserPreferencesRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val categoryId: Long = checkNotNull(savedState[ARG_CATEGORY])

    /** Set once it's been moved or archived, so the page closes rather than showing what's left. */
    private val done = MutableStateFlow(false)

    val state: StateFlow<RemoveCategoryUiState> = combine(
        transactions.observeDetails(DateRange.ALL_TIME),
        catalog.categories,
        combine(recurring.payments, debts.debts) { payments, owed -> payments to owed },
        prefs.preferences,
        done,
    ) { rows, categories, (payments, owed), p, finished ->
        val category = categories.firstOrNull { it.id == categoryId }?.takeUnless { finished }
            ?: return@combine RemoveCategoryUiState(loading = false)
        val children = categories.filter { it.parentId == category.id }
        val ids = (children.map { it.id } + category.id).toSet()
        RemoveCategoryUiState(
            loading = false,
            category = category,
            parts = parts(category, children, rows.filter { it.transaction.categoryId in ids }),
            subCategories = children,
            payments = payments.filter { it.categoryId in ids },
            debts = owed.count { it.debt.categoryId in ids },
            targets = targets(category, categories),
            hideAmounts = p.hideAmountsInApp,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemoveCategoryUiState())

    fun moveAll(target: Category) {
        viewModelScope.launch {
            catalog.moveCategory(categoryId, target.id)
            done.update { true }
        }
    }

    /** Leaves it where it is, out of the lists: what's filed under it keeps its name. */
    fun keepArchived() {
        val category = state.value.category ?: return
        viewModelScope.launch {
            catalog.removeCategory(category)
            done.update { true }
        }
    }

    companion object {
        const val ARG_CATEGORY = "categoryId"
    }
}

/** The category's own entries first, then each sub-category's, biggest first. Ones with nothing don't take a row. */
internal fun parts(category: Category, children: List<Category>, rows: List<TransactionDetails>): List<CategoryPart> {
    val byCategory = rows.groupBy { it.transaction.categoryId }
    fun part(c: Category, isParent: Boolean) = byCategory[c.id]?.let { list -> CategoryPart(c, list.size, list.map { it.transaction.amount }.sum(), isParent) }
    return listOfNotNull(part(category, isParent = children.isNotEmpty())) + children.mapNotNull { part(it, isParent = false) }.sortedByDescending { it.total }
}

/** A category goes to another of its kind; a sub-category to its parent or the parent's other sub-categories. */
internal fun targets(category: Category, all: List<Category>): List<Category> {
    val live = all.filter { !it.isArchived && it.id != category.id && it.type == category.type }
    val parentId = category.parentId ?: return live.filter { it.parentId == null }
    return live.filter { it.id == parentId } + live.filter { it.parentId == parentId }
}
