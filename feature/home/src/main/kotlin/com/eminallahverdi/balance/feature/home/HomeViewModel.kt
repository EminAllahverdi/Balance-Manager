package com.eminallahverdi.balance.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.UiText
import com.eminallahverdi.balance.core.common.uiText
import com.eminallahverdi.balance.core.data.BankAlertRepository
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.BackupProblem
import com.eminallahverdi.balance.core.datastore.UserPreferences
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.AccountBalance
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.MonthSummary
import com.eminallahverdi.balance.core.model.SpendingCalculator
import com.eminallahverdi.balance.core.model.isBackupOverdue
import com.eminallahverdi.balance.core.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeView { CATEGORY, PERSON }

data class HomeUiState(
    val loading: Boolean = true,
    val view: HomeView = HomeView.CATEGORY,
    val hideAmounts: Boolean = false,
    val total: Money = Money.ZERO,
    val balances: List<AccountBalance> = emptyList(),
    val expandedPersonId: Long? = null,
    /** Recurring payments waiting to be confirmed. */
    val duePayments: Int = 0,
    /** Bank payments waiting on To confirm. */
    val toConfirm: Int = 0,
    val showChart: Boolean = false,
    val chartIncome: Boolean = false,
    /** Set while the data isn't safely in Drive. */
    val backupWarning: UiText? = null,
)

/** Says nothing with backups off: that's a choice, and Settings shows it. */
private fun backupWarning(p: UserPreferences, now: Instant): UiText? {
    val last = p.lastBackupAt?.let(Instant::ofEpochMilli)
    return when {
        !p.driveBackupEnabled -> null
        p.backupProblem == BackupProblem.SIGN_IN -> uiText(R.string.home_backup_sign_in)
        !isBackupOverdue(now, last) -> null
        last == null -> uiText(R.string.home_not_backed_up)
        else -> uiText(R.string.home_not_backed_up_since, DateLabels.dayMonth(last.atZone(ZoneId.systemDefault()).toLocalDate()))
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val transactions: TransactionRepository,
    recurring: RecurringRepository,
    bankAlerts: BankAlertRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    private val view = MutableStateFlow(HomeView.CATEGORY)
    private val expanded = MutableStateFlow<Long?>(null)

    /** One month's figures. Each page of the month pager collects its own. */
    fun summary(month: YearMonth): Flow<MonthSummary> {
        val range = DateRange.month(month)
        return combine(transactions.observeDetails(range), catalog.people) { details, people ->
            SpendingCalculator.summarize(range, details, people.filterNot { it.isArchived })
        }
    }

    val state: StateFlow<HomeUiState> = combine(
        transactions.balances,
        prefs.preferences,
        combine(view, expanded) { v, e -> v to e },
        combine(recurring.payments, bankAlerts.waitingCount) { payments, waiting -> payments to waiting },
    ) { balances, p, (v, e), (payments, waiting) ->
        val included = balances.filter { it.account.includeInTotal }
        val today = LocalDate.now()
        HomeUiState(
            loading = false,
            view = v,
            hideAmounts = p.hideAmountsInApp,
            total = included.map { it.balance }.sum(),
            balances = included,
            expandedPersonId = e,
            duePayments = payments.count { it.isDue(today) },
            toConfirm = waiting,
            showChart = p.homeChart,
            chartIncome = p.homeChartIncome,
            backupWarning = backupWarning(p, Instant.now()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setView(v: HomeView) = view.update { v }
    fun togglePerson(id: Long) = expanded.update { if (it == id) null else id }
    fun setShowChart(show: Boolean) { viewModelScope.launch { prefs.setHomeChart(show) } }
    fun setChartIncome(income: Boolean) { viewModelScope.launch { prefs.setHomeChartIncome(income) } }

    /** The same setting as Settings' "Hide amounts", so it holds across the app and after a restart. */
    fun toggleHideAmounts() {
        viewModelScope.launch { prefs.setHideAmounts(!prefs.current().hideAmountsInApp) }
    }
}
