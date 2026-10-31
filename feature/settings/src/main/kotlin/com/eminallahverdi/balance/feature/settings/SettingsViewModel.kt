package com.eminallahverdi.balance.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.datastore.ThemePreference
import com.eminallahverdi.balance.core.datastore.UserPreferences
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.Account
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
) {
    /** Quick add falls back to the first account until one is chosen, so show that here too. */
    val defaultAccount: Account? get() = accounts.firstOrNull { it.id == preferences.defaultExpenseAccountId } ?: accounts.firstOrNull()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    catalog: CatalogRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(prefs.preferences, catalog.accounts, catalog.categories, catalog.people) { p, accounts, categories, people ->
        SettingsUiState(
            preferences = p,
            accounts = accounts.filterNot { it.isArchived },
            categoryCount = categories.count { it.parentId == null && !it.isArchived },
            peopleCount = people.count { !it.isArchived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDefaultAccount(id: Long) { viewModelScope.launch { prefs.setDefaultExpenseAccount(id) } }
    fun setTheme(theme: ThemePreference) { viewModelScope.launch { prefs.setTheme(theme) } }
    fun setHideAmounts(hide: Boolean) { viewModelScope.launch { prefs.setHideAmounts(hide) } }
}
