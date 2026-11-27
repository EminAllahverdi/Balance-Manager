package com.eminallahverdi.balance.feature.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.common.UiText
import com.eminallahverdi.balance.core.common.rawText
import com.eminallahverdi.balance.core.common.uiText
import com.eminallahverdi.balance.core.data.BankAlertRepository
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.PendingItem
import com.eminallahverdi.balance.core.data.UnreadNotification
import com.eminallahverdi.balance.core.datastore.InstallPreferences
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
    val toAccount: Account? = null,
) {
    val isTransfer: Boolean get() = item.entry.kind == TransactionKind.TRANSFER
    val isWaiting: Boolean get() = item.entry.status == PendingStatus.WAITING
    val canConfirm: Boolean get() = account != null && (if (isTransfer) toAccount != null else category != null)

    /** "Groceries · Bravo", "Subscriptions · Apps · Spotify", or just the merchant until it has a category. */
    val title: UiText
        get() {
            if (isTransfer) return uiText(R.string.confirm_cash_withdrawal)
            val merchant = item.guess.merchantName.ifBlank { null }
            val names = listOfNotNull(parent?.name, category?.name, merchant).distinct()
            if (names.isNotEmpty()) return rawText(names.joinToString(" · "))
            return uiText(if (item.entry.kind == TransactionKind.INCOME) R.string.confirm_money_received else R.string.confirm_bank_payment)
        }

    val caption: List<UiText>
        get() = when {
            isTransfer -> listOf(if (toAccount == null) uiText(R.string.confirm_pick_account_to_move) else rawText("${account?.name} → ${toAccount.name}"))
            // Done with, as a debt for one: nothing left to pick.
            category == null -> if (isWaiting) listOf(uiText(R.string.confirm_pick_a_category)) else emptyList()
            else -> listOfNotNull(uiText(R.string.confirm_suggested).takeIf { item.guess.isSuggested }, person?.name?.let(::rawText), account?.name?.let(::rawText))
        }
}

data class ConfirmUiState(
    val loading: Boolean = true,
    val rows: List<ConfirmRow> = emptyList(),
    val listening: Boolean = false,
    val autoConfirm: Boolean = false,
    val askingAutoConfirm: Boolean = false,
    val hideAmounts: Boolean = false,
    /** Notifications from picked bank apps that weren't read, so the user can see why. */
    val unread: List<UnreadNotification> = emptyList(),
    /** A payment just swiped away, named with Undo for a few seconds. */
    val skipped: PendingItem? = null,
    /** Whether swiping hasn't been shown yet: the top card slides a little, and a line says so until Got it. */
    val swipeHint: Boolean = false,
) {
    val waiting: Int get() = rows.count { it.isWaiting }
}

@HiltViewModel
class ConfirmViewModel @Inject constructor(
    private val alerts: BankAlertRepository,
    catalog: CatalogRepository,
    private val prefs: UserPreferencesRepository,
    private val install: InstallPreferences,
) : ViewModel() {

    private val asking = MutableStateFlow(false)
    private val skipped = MutableStateFlow<PendingItem?>(null)
    private val startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()

    val state: StateFlow<ConfirmUiState> = combine(
        alerts.items(startOfToday),
        combine(catalog.categories, catalog.people, catalog.accounts) { c, p, a -> Triple(c, p, a) },
        combine(prefs.preferences, alerts.unread) { settings, unread -> settings to unread },
        combine(asking, skipped, install.confirmSwipeShown) { a, s, shown -> Triple(a, s, shown) },
    ) { items, (categories, people, accounts), (settings, unread), (askingNow, skippedNow, swipeShown) ->
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
                    toAccount = accounts.firstOrNull { it.id == item.guess.toAccountId },
                )
            },
            listening = settings.bankNotificationsEnabled,
            autoConfirm = settings.autoConfirmKnownMerchants,
            askingAutoConfirm = askingNow,
            hideAmounts = settings.hideAmountsInApp,
            unread = unread,
            skipped = skippedNow,
            swipeHint = !swipeShown && items.any { it.entry.status == PendingStatus.WAITING },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfirmUiState())

    fun confirm(id: Long) { viewModelScope.launch { alerts.confirm(id) } }

    /** Swiped left. Nothing is lost, so it asks nothing, and [undoSkip] brings it back for a few seconds. */
    fun skip(item: PendingItem) {
        skipped.value = item
        viewModelScope.launch {
            alerts.skip(item.entry.id)
            install.setConfirmSwipeShown()
        }
    }

    fun undoSkip() {
        val item = skipped.value ?: return
        skipped.value = null
        viewModelScope.launch { alerts.unskip(item.entry.id) }
    }

    /** The Undo has had its time. */
    fun forgetSkipped() { skipped.value = null }

    /** Got it: swiping has been seen, so the hint doesn't come back. */
    fun dismissSwipeHint() { viewModelScope.launch { install.setConfirmSwipeShown() } }

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
