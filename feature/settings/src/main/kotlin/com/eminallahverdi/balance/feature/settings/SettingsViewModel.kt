package com.eminallahverdi.balance.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.datastore.ThemePreference
import com.eminallahverdi.balance.core.datastore.UserPreferences
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.datastore.WidgetBalanceMode
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.RecurringPayment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val accounts: List<Account> = emptyList(),
    val categoryCount: Int = 0,
    val peopleCount: Int = 0,
    val recurring: List<RecurringPayment> = emptyList(),
) {
    /** Quick add falls back to the first account until one is chosen, so show that here too. */
    val defaultAccount: Account? get() = accounts.firstOrNull { it.id == preferences.defaultExpenseAccountId } ?: accounts.firstOrNull()

    val recurringSummary: String
        get() = if (recurring.isEmpty()) "None yet" else recurring.take(SUMMARY_NAMES).joinToString(", ") { it.name } + " · ${recurring.size}"

    private companion object {
        const val SUMMARY_NAMES = 3
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    catalog: CatalogRepository,
    recurring: RecurringRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(prefs.preferences, catalog.accounts, catalog.categories, catalog.people, recurring.payments) { p, accounts, categories, people, payments ->
        SettingsUiState(
            preferences = p,
            accounts = accounts.filterNot { it.isArchived },
            categoryCount = categories.count { it.parentId == null && !it.isArchived },
            peopleCount = people.count { !it.isArchived },
            recurring = payments,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDefaultAccount(id: Long) { viewModelScope.launch { prefs.setDefaultExpenseAccount(id) } }
    fun setTheme(theme: ThemePreference) { viewModelScope.launch { prefs.setTheme(theme) } }
    fun setHideAmounts(hide: Boolean) { viewModelScope.launch { prefs.setHideAmounts(hide) } }
    fun setWidgetBalanceMode(mode: WidgetBalanceMode) { viewModelScope.launch { prefs.setWidgetBalanceMode(mode) } }
}
