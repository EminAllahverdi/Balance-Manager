package com.eminallahverdi.balance.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.PlannedRepository
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.PlannedPayment
import com.eminallahverdi.balance.core.model.PlannedStatus
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.sum
import com.eminallahverdi.balance.core.model.toPayBy
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the plan sheet holds while typing. [categoryId] is top level, with [subCategoryId] under it, as in quick add. */
data class PlannedDraft(
    val id: Long = 0,
    val name: String = "",
    val amountText: String = "",
    val date: LocalDate,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val recurringId: Long? = null,
    val plannedOn: LocalDate,
    val paidOn: LocalDate? = null,
) {
    val isNew: Boolean get() = id == 0L
    val amount: Money? get() = Money.parse(amountText)?.takeIf { it.minor > 0 }
    val canSave: Boolean get() = name.isNotBlank() && amount != null

    fun toPayment(): PlannedPayment? {
        val amount = amount ?: return null
        return PlannedPayment(
            id = id,
            name = name.trim(),
            amount = amount,
            date = date,
            accountId = accountId,
            categoryId = subCategoryId ?: categoryId,
            recurringId = recurringId,
            plannedOn = plannedOn,
            paidOn = paidOn,
        )
    }
}

internal fun PlannedPayment.toDraft(categories: List<Category>): PlannedDraft {
    val category = categories.firstOrNull { it.id == categoryId }
    return PlannedDraft(
        id = id,
        name = name,
        amountText = amount.toFieldText(),
        date = date,
        accountId = accountId,
        categoryId = category?.parentId ?: categoryId,
        subCategoryId = categoryId.takeIf { category?.parentId != null },
        recurringId = recurringId,
        plannedOn = plannedOn,
        paidOn = paidOn,
    )
}

data class PlannedUiState(
    val loading: Boolean = true,
    val statuses: List<PlannedStatus> = emptyList(),
    val recurring: List<RecurringPayment> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    /** What the accounts in the total hold, which planned payments are taken from. */
    val total: Money = Money.ZERO,
    val hideAmounts: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val editor: PlannedDraft? = null,
) {
    private val month: YearMonth get() = YearMonth.from(today)

    /** Still to pay this month, overdue ones included: the ones Home takes off the total. */
    val toPay: List<PlannedStatus> get() = statuses.toPayBy(month)
    val toPayTotal: Money get() = toPay.map { it.payment.amount }.sum()
    val left: Money get() = total - toPayTotal

    /** Planned for after this month, not taken off yet. */
    val later: List<PlannedStatus> get() = statuses.filter { !it.isPaid && it.payment.date.isAfter(month.atEndOfMonth()) }

    /** Paid this month, by an entry or a tick. Older ones have done their job. */
    val paid: List<PlannedStatus> get() = statuses.filter { status -> status.isPaid && status.paidOn?.let { YearMonth.from(it) == month } == true }

    /** Recurring payments due by the end of the month that aren't planned for that date yet, to add in one tap. */
    val offered: List<RecurringPayment>
        get() = recurring.filter { payment ->
            !payment.isFinished && !payment.nextDate.isAfter(month.atEndOfMonth()) &&
                statuses.none { it.payment.recurringId == payment.id && it.payment.date == payment.nextDate }
        }

    val topCategories: List<Category> get() = categories.filter { it.parentId == null && it.type == CategoryType.EXPENSE && !it.isArchived }
    val activeAccounts: List<Account> get() = accounts.filterNot { it.isArchived }

    fun subCategoriesOf(id: Long?): List<Category> = categories.filter { it.parentId == id && id != null && !it.isArchived }
    fun category(id: Long?): Category? = categories.firstOrNull { it.id == id }
    fun account(id: Long?): Account? = accounts.firstOrNull { it.id == id }
}

@HiltViewModel
class PlannedViewModel @Inject constructor(
    private val planned: PlannedRepository,
    recurring: RecurringRepository,
    catalog: CatalogRepository,
    transactions: TransactionRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    private val editor = MutableStateFlow<PlannedDraft?>(null)

    val state: StateFlow<PlannedUiState> = combine(
        planned.statuses,
        recurring.payments,
        combine(catalog.categories, catalog.accounts) { c, a -> c to a },
        combine(transactions.balances, prefs.preferences) { b, p -> b to p },
        editor,
    ) { statuses, payments, (categories, accounts), (balances, p), draft ->
        PlannedUiState(
            loading = false,
            statuses = statuses,
            recurring = payments,
            categories = categories,
            accounts = accounts,
            total = balances.filter { it.account.includeInTotal }.map { it.balance }.sum(),
            hideAmounts = p.hideAmountsInApp,
            today = LocalDate.now(),
            editor = draft,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannedUiState())

    /** A new plan starts at the end of the month, from the account entries usually come from. */
    fun newPayment() {
        viewModelScope.launch {
            val s = state.value
            val preferred = prefs.current().defaultExpenseAccountId
            val account = s.activeAccounts.firstOrNull { it.id == preferred } ?: s.activeAccounts.firstOrNull()
            val today = LocalDate.now()
            editor.update { PlannedDraft(date = YearMonth.from(today).atEndOfMonth(), accountId = account?.id, plannedOn = today) }
        }
    }

    fun edit(payment: PlannedPayment) = editor.update { payment.toDraft(state.value.categories) }
    fun closeEditor() = editor.update { null }

    private fun updateDraft(change: PlannedDraft.() -> PlannedDraft) = editor.update { it?.change() }

    fun setName(text: String) = updateDraft { copy(name = text) }
    fun setAmount(text: String) = updateDraft { copy(amountText = text) }
    fun setDate(date: LocalDate) = updateDraft { copy(date = date) }
    fun setAccount(id: Long) = updateDraft { copy(accountId = id.takeIf { it != accountId }) }

    /** Tapping the picked category again leaves it out: a plan needs no category. */
    fun selectCategory(category: Category) = updateDraft {
        if (categoryId == category.id) copy(categoryId = null, subCategoryId = null) else copy(categoryId = category.id, subCategoryId = null)
    }

    fun selectSubCategory(category: Category) = updateDraft { copy(subCategoryId = category.id.takeIf { it != subCategoryId }) }

    fun saveEditor() {
        val payment = editor.value?.toPayment() ?: return
        editor.update { null }
        viewModelScope.launch { planned.save(payment) }
    }

    fun removeEditing() {
        val id = editor.value?.id ?: return
        editor.update { null }
        viewModelScope.launch { planned.remove(id) }
    }

    /** A tick says it's paid when no entry was added for it, like cash handed over. Ticking again takes it back. */
    fun toggleTick(status: PlannedStatus) {
        viewModelScope.launch { planned.setPaid(status.payment.id, if (status.isTicked) null else LocalDate.now()) }
    }

    /** Plans a recurring payment for its next date, as it stands. */
    fun plan(payment: RecurringPayment) {
        viewModelScope.launch {
            planned.save(
                PlannedPayment(
                    id = 0,
                    name = payment.name,
                    amount = payment.amountDue,
                    date = payment.nextDate,
                    accountId = payment.accountId,
                    categoryId = payment.categoryId,
                    recurringId = payment.id,
                    plannedOn = minOf(LocalDate.now(), payment.firstLoggedDay),
                ),
            )
        }
    }
}
