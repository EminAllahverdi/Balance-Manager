package com.eminallahverdi.balance.feature.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.BankAlertRepository
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.PendingItem
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.PendingStatus
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.TransactionKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One card: a pending entry and the names of what it would be saved as. */
data class ConfirmRow(
    val item: PendingItem,
    val category: Category?,
    val parent: Category?,
    val person: Person?,
    val account: Account?,
) {
    val isWaiting: Boolean get() = item.entry.status == PendingStatus.WAITING
    val canConfirm: Boolean get() = category != null && account != null

    /** "Groceries · Bravo", "Subscriptions · Apps · Spotify", or just the merchant until it has a category. */
    val title: String
        get() {
            val merchant = item.guess.merchantName.ifBlank { null }
            val fallback = if (item.entry.kind == TransactionKind.INCOME) "Money received" else "Bank payment"
            return listOfNotNull(parent?.name, category?.name, merchant).distinct().joinToString(" · ").ifBlank { fallback }
        }

    val caption: String
        get() = if (category == null) "Pick a category" else listOfNotNull("Suggested".takeIf { item.guess.isSuggested }, person?.name, account?.name).joinToString(" · ")
}

data class ConfirmUiState(
    val loading: Boolean = true,
    val rows: List<ConfirmRow> = emptyList(),
    val listening: Boolean = false,
    val autoConfirm: Boolean = false,
    val askingAutoConfirm: Boolean = false,
) {
    val waiting: Int get() = rows.count { it.isWaiting }
}

@HiltViewModel
class ConfirmViewModel @Inject constructor(
    private val alerts: BankAlertRepository,
    catalog: CatalogRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    private val asking = MutableStateFlow(false)
    private val startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()

    val state: StateFlow<ConfirmUiState> = combine(
        alerts.items(startOfToday),
        combine(catalog.categories, catalog.people, catalog.accounts) { c, p, a -> Triple(c, p, a) },
        prefs.preferences,
        asking,
    ) { items, (categories, people, accounts), settings, askingNow ->
        ConfirmUiState(
            loading = false,
            rows = items.map { item ->
                val category = categories.firstOrNull { it.id == item.guess.categoryId }
                ConfirmRow(
                    item = item,
                    category = category,
                    parent = categories.firstOrNull { it.id == category?.parentId },
                    person = people.firstOrNull { it.id == item.guess.personId },
                    account = accounts.firstOrNull { it.id == item.guess.accountId },
                )
            },
            listening = settings.bankNotificationsEnabled,
            autoConfirm = settings.autoConfirmKnownMerchants,
            askingAutoConfirm = askingNow,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfirmUiState())

    fun confirm(id: Long) { viewModelScope.launch { alerts.confirm(id) } }
    fun skip(id: Long) { viewModelScope.launch { alerts.skip(id) } }

    /** Turning it on always asks first. Turning it off never needs to. */
    fun setAutoConfirm(enabled: Boolean) {
        if (enabled) asking.update { true } else viewModelScope.launch { prefs.setAutoConfirmKnownMerchants(false) }
    }

    fun acceptAutoConfirm() {
        asking.update { false }
        viewModelScope.launch { prefs.setAutoConfirmKnownMerchants(true) }
    }

    fun declineAutoConfirm() = asking.update { false }
}
