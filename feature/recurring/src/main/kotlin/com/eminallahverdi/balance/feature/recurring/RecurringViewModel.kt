package com.eminallahverdi.balance.feature.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.LoanTerms
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.RecurringFrequency
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.RepeatingPayment
import com.eminallahverdi.balance.core.model.repeatingPayments
import com.eminallahverdi.balance.core.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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

/** What the editor holds while typing. [categoryId] is always top level, with [subCategoryId] under it, as in quick add. */
data class RecurringDraft(
    val id: Long = 0,
    val name: String = "",
    val amountText: String = "",
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val personId: Long? = null,
    val accountId: Long? = null,
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val nextDate: LocalDate,
    val anchorDay: Int = nextDate.dayOfMonth,
    val loan: LoanTerms? = null,
) {
    val isNew: Boolean get() = id == 0L
    val amount: Money? get() = Money.parse(amountText)?.takeIf { it.minor > 0 }
    val canSave: Boolean get() = name.isNotBlank() && amount != null && categoryId != null && accountId != null

    fun toPayment(): RecurringPayment? {
        val amount = amount ?: return null
        return RecurringPayment(
            id = id,
            name = name.trim(),
            amount = amount,
            accountId = accountId ?: return null,
            categoryId = subCategoryId ?: categoryId ?: return null,
            personId = personId,
            frequency = frequency,
            nextDate = nextDate,
            anchorDay = anchorDay,
            loan = loan,
        )
    }
}

/** What a typed field starts with: 16.31, and 1184.00 without the separator. */
internal fun Money.toFieldText(): String = MoneyFormat.format(this).removePrefix(MoneyFormat.SYMBOL).replace(",", "")

/** How far back repeating payments are looked for. */
private const val DAYS_OF_HISTORY = 200L

/** A new payment starts a period away, so it doesn't remind about today's the moment it's saved. */
internal fun newDraft(today: LocalDate, accountId: Long?) = RecurringDraft(nextDate = today.plusMonths(1), accountId = accountId)

internal fun RecurringPayment.toDraft(categories: List<Category>): RecurringDraft {
    val category = categories.firstOrNull { it.id == categoryId }
    return RecurringDraft(
        id = id,
        name = name,
        amountText = amount.toFieldText(),
        categoryId = category?.parentId ?: categoryId,
        subCategoryId = categoryId.takeIf { category?.parentId != null },
        personId = personId,
        accountId = accountId,
        frequency = frequency,
        nextDate = nextDate,
        anchorDay = anchorDay,
        loan = loan,
    )
}

data class RecurringUiState(
    val loading: Boolean = true,
    val payments: List<RecurringPayment> = emptyList(),
    val categories: List<Category> = emptyList(),
    val people: List<Person> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val editor: RecurringDraft? = null,
    /** Shops paid about the same every month or week that aren't recurring payments yet, offered to add. */
    val repeats: List<RepeatingPayment> = emptyList(),
) {
    val due: List<RecurringPayment> get() = payments.filter { it.isDue(today) }
    val upcoming: List<RecurringPayment> get() = payments.filterNot { it.isDue(today) }
    val monthlyCost: Money get() = payments.map { it.monthlyCost }.sum()

    val topCategories: List<Category> get() = categories.filter { it.parentId == null && it.type == CategoryType.EXPENSE && !it.isArchived }
    val activePeople: List<Person> get() = people.filterNot { it.isArchived }
    val activeAccounts: List<Account> get() = accounts.filterNot { it.isArchived }

    fun subCategoriesOf(id: Long?): List<Category> = categories.filter { it.parentId == id && id != null && !it.isArchived }
    fun category(id: Long?): Category? = categories.firstOrNull { it.id == id }
    fun account(id: Long?): Account? = accounts.firstOrNull { it.id == id }
}

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val recurring: RecurringRepository,
    catalog: CatalogRepository,
    transactions: TransactionRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    private val editor = MutableStateFlow<RecurringDraft?>(null)

    /** What the owner's last months say repeats, less what's already a recurring payment or was turned down. */
    private val repeats = combine(
        transactions.observeDetails(DateRange(LocalDate.now().minusDays(DAYS_OF_HISTORY), LocalDate.now().plusDays(1))),
        recurring.payments,
        prefs.preferences,
    ) { history, payments, p -> repeatingPayments(history, payments, LocalDate.now(), p.dismissedRepeats) }

    val state: StateFlow<RecurringUiState> = combine(
        combine(recurring.payments, repeats) { payments, offered -> payments to offered },
        catalog.categories,
        catalog.people,
        catalog.accounts,
        editor,
    ) { (payments, offered), categories, people, accounts, draft ->
        RecurringUiState(
            loading = false,
            payments = payments.filterNot { it.isFinished },
            categories = categories,
            people = people,
            accounts = accounts,
            today = LocalDate.now(),
            editor = draft,
            repeats = offered,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState())

    /** A repeating payment as a new recurring one, filled in from its last entry, for the owner to check and save. */
    fun adopt(repeat: RepeatingPayment) {
        val payment = RecurringPayment(
            id = 0,
            name = repeat.shop,
            amount = repeat.amount,
            accountId = repeat.accountId,
            categoryId = repeat.categoryId,
            personId = repeat.personId,
            frequency = repeat.frequency,
            nextDate = repeat.nextDate,
        )
        editor.update { payment.toDraft(state.value.categories) }
    }

    /** Not a recurring payment, so it isn't offered again. */
    fun dismiss(repeat: RepeatingPayment) { viewModelScope.launch { prefs.dismissRepeat(repeat.key) } }

    init {
        viewModelScope.launch { recurring.settleLogged() }
    }

    fun newPayment() {
        viewModelScope.launch {
            val s = state.value
            val preferred = prefs.current().defaultExpenseAccountId
            val account = s.activeAccounts.firstOrNull { it.id == preferred } ?: s.activeAccounts.firstOrNull()
            editor.update { newDraft(LocalDate.now(), account?.id) }
        }
    }

    fun edit(payment: RecurringPayment) = editor.update { payment.toDraft(state.value.categories) }
    fun closeEditor() = editor.update { null }

    private fun updateDraft(change: RecurringDraft.() -> RecurringDraft) = editor.update { it?.change() }

    fun setName(text: String) = updateDraft { copy(name = text) }
    fun setAmount(text: String) = updateDraft { copy(amountText = text) }
    fun setAccount(id: Long) = updateDraft { copy(accountId = id) }
    fun setPerson(id: Long) = updateDraft { copy(personId = id) }
    fun setFrequency(frequency: RecurringFrequency) = updateDraft { copy(frequency = frequency) }
    fun setDate(date: LocalDate) = updateDraft { copy(nextDate = date, anchorDay = date.dayOfMonth) }

    /** Each category knows who it's usually for, as it does in quick add. */
    fun selectCategory(category: Category) = updateDraft { copy(categoryId = category.id, subCategoryId = null, personId = category.defaultPersonId ?: personId) }

    fun selectSubCategory(category: Category) = updateDraft {
        val next = category.id.takeIf { it != subCategoryId }
        copy(subCategoryId = next, personId = category.defaultPersonId.takeIf { next != null } ?: personId)
    }

    fun saveEditor() {
        val payment = editor.value?.toPayment() ?: return
        editor.update { null }
        viewModelScope.launch {
            recurring.save(payment)
            recurring.settleLogged()
        }
    }

    /** Removes the reminder. Entries logged for it stay in the history. */
    fun removeEditing() {
        val id = editor.value?.id ?: return
        editor.update { null }
        viewModelScope.launch { recurring.remove(id) }
    }

    /** Moves past a date it didn't happen. */
    fun skip(payment: RecurringPayment) { viewModelScope.launch { recurring.skip(payment) } }
}
