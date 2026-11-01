package com.eminallahverdi.balance.feature.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.Transaction
import com.eminallahverdi.balance.core.model.TransactionKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuickAddStep { AMOUNT, DETAILS }

data class QuickAddUiState(
    val loading: Boolean = true,
    val step: QuickAddStep = QuickAddStep.AMOUNT,
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val amountText: String = "",
    val accounts: List<Account> = emptyList(),
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val categories: List<Category> = emptyList(),
    val allCategories: List<Category> = emptyList(),
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val merchant: String = "",
    val recentMerchants: List<String> = emptyList(),
    val people: List<Person> = emptyList(),
    val personId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    /** The saved transaction being changed, or null when adding a new one. */
    val editing: Transaction? = null,
    /** The reminder this entry was filled in from. Saving it moves that reminder to its next date. */
    val payment: RecurringPayment? = null,
    val saved: Boolean = false,
) {
    val amount: Money? get() = Money.parse(amountText)?.takeIf { it.minor > 0 }
    val category: Category? get() = allCategories.firstOrNull { it.id == categoryId }
    val subCategories: List<Category> get() = allCategories.filter { it.parentId == categoryId && !it.isArchived }
    val subCategory: Category? get() = allCategories.firstOrNull { it.id == subCategoryId }
    val person: Person? get() = people.firstOrNull { it.id == personId }
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }
    val canSave: Boolean get() = amount != null && accountId != null && when (kind) {
        TransactionKind.TRANSFER -> toAccountId != null && toAccountId != accountId
        else -> categoryId != null
    }
}

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val transactions: TransactionRepository,
    private val recurring: RecurringRepository,
    private val prefs: UserPreferencesRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickAddUiState())
    val state: StateFlow<QuickAddUiState> = _state.asStateFlow()

    init {
        val requestedKind = savedState.get<String>(ARG_KIND)?.let { runCatching { TransactionKind.valueOf(it) }.getOrNull() } ?: TransactionKind.EXPENSE
        val editingId = savedState.get<Long>(ARG_TRANSACTION)
        val requestedCategory = savedState.get<Long>(ARG_CATEGORY)
        val paymentId = savedState.get<Long>(ARG_PAYMENT)
        viewModelScope.launch {
            val editing = editingId?.let { transactions.get(it) }
            if (editing != null) loadForEditing(editing)
            paymentId?.let { recurring.get(it) }?.let { loadFromPayment(it) }
            val startKind = editing?.kind ?: requestedKind
            val defaultAccount = prefs.current().defaultExpenseAccountId
            combine(catalog.accounts, catalog.categories, catalog.people) { a, c, p -> Triple(a, c, p) }
                .collect { (accounts, categories, people) ->
                    val active = accounts.filterNot { it.isArchived }
                    val firstLoad = _state.value.loading
                    _state.update { s ->
                        val accountId = s.accountId ?: active.firstOrNull { it.id == defaultAccount }?.id ?: active.firstOrNull()?.id
                        s.copy(
                            loading = false,
                            kind = if (s.loading) startKind else s.kind,
                            accounts = active,
                            accountId = accountId,
                            toAccountId = s.toAccountId ?: active.firstOrNull { it.id != accountId }?.id,
                            allCategories = categories,
                            categories = topLevel(categories, if (s.loading) startKind else s.kind),
                            people = people.filterNot { it.isArchived },
                            personId = s.personId ?: people.firstOrNull()?.id,
                        )
                    }
                    if (firstLoad) _state.value.categories.firstOrNull { it.id == requestedCategory }?.let(::selectCategory)
                }
        }
    }

    private suspend fun loadForEditing(t: Transaction) {
        val category = catalog.categories.first().firstOrNull { it.id == t.categoryId }
        val merchant = catalog.merchants.first().firstOrNull { it.id == t.merchantId }
        _state.update {
            it.copy(
                editing = t,
                amountText = t.amount.toInputText(),
                accountId = t.accountId,
                toAccountId = t.toAccountId,
                categoryId = category?.parentId ?: category?.id,
                subCategoryId = category?.id?.takeIf { category.parentId != null },
                merchant = merchant?.name.orEmpty(),
                personId = t.personId,
                date = t.date,
                note = t.note,
            )
        }
    }

    /** A ready-made entry from a reminder. Nothing is saved until the user presses Save. */
    private suspend fun loadFromPayment(payment: RecurringPayment) {
        val category = catalog.categories.first().firstOrNull { it.id == payment.categoryId }
        _state.update {
            it.copy(
                payment = payment,
                amountText = payment.amountDue.toInputText(),
                accountId = payment.accountId,
                categoryId = category?.parentId ?: category?.id,
                subCategoryId = category?.id?.takeIf { category.parentId != null },
                merchant = payment.name,
                personId = payment.personId,
                date = payment.nextDate,
            )
        }
    }

    private fun topLevel(all: List<Category>, kind: TransactionKind): List<Category> {
        val type = if (kind == TransactionKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        return all.filter { it.parentId == null && it.type == type && !it.isArchived }
    }

    fun setKind(kind: TransactionKind) = _state.update {
        it.withDistinctAccounts().copy(kind = kind, categories = topLevel(it.allCategories, kind), categoryId = null, subCategoryId = null, step = QuickAddStep.AMOUNT)
    }

    fun key(k: Char) = _state.update { s ->
        val t = s.amountText
        val next = when {
            k == '.' && (t.contains('.')) -> t
            k == '.' && t.isEmpty() -> "0."
            k.isDigit() && t.substringAfter('.', "").length >= 2 && t.contains('.') -> t
            k.isDigit() && t == "0" -> k.toString()
            t.length >= 10 -> t
            else -> t + k
        }
        s.copy(amountText = next)
    }

    fun backspace() = _state.update { it.copy(amountText = it.amountText.dropLast(1)) }
    fun clear() = _state.update { it.copy(amountText = "") }

    fun selectCategory(category: Category) {
        _state.update { it.copy(categoryId = category.id, subCategoryId = null) }
        viewModelScope.launch {
            val subs = _state.value.allCategories.filter { it.parentId == category.id && !it.isArchived }
            val s = transactions.suggestFor(category, subs)
            _state.update { st ->
                st.copy(
                    subCategoryId = s.subCategoryId,
                    merchant = s.merchantName.orEmpty(),
                    recentMerchants = s.recentMerchants,
                    personId = s.personId?.takeIf { id -> st.people.any { it.id == id } } ?: st.people.firstOrNull()?.id,
                    accountId = s.accountId?.takeIf { id -> st.accounts.any { it.id == id } } ?: st.accountId,
                )
            }
        }
    }

    fun selectSubCategory(id: Long?) = _state.update { s ->
        val next = if (s.subCategoryId == id) null else id
        val override = s.allCategories.firstOrNull { it.id == next }?.defaultPersonId
        s.copy(subCategoryId = next, personId = override?.takeIf { p -> s.people.any { it.id == p } } ?: s.personId)
    }
    fun selectPerson(id: Long) = _state.update { it.copy(personId = id) }
    fun selectAccount(id: Long) = _state.update { it.withAccount(id) }
    fun selectToAccount(id: Long) = _state.update { it.copy(toAccountId = id) }
    fun setMerchant(text: String) = _state.update { it.copy(merchant = text) }
    fun setNote(text: String) = _state.update { it.copy(note = text) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun showDetails() = _state.update { it.copy(step = QuickAddStep.DETAILS) }
    fun showAmount() = _state.update { it.copy(step = QuickAddStep.AMOUNT) }

    fun save() {
        val s = _state.value
        val amount = s.amount ?: return
        val accountId = s.accountId ?: return
        if (!s.canSave) return
        viewModelScope.launch {
            val transfer = s.kind == TransactionKind.TRANSFER
            transactions.save(
                Transaction(
                    id = s.editing?.id ?: 0,
                    kind = s.kind,
                    amount = amount,
                    date = s.date,
                    accountId = accountId,
                    toAccountId = if (transfer) s.toAccountId else null,
                    categoryId = if (transfer) null else (s.subCategoryId ?: s.categoryId),
                    personId = if (transfer) null else s.personId,
                    note = s.note.trim(),
                    tripName = s.editing?.tripName,
                    debtId = s.editing?.debtId,
                    createdAt = s.editing?.createdAt ?: Instant.EPOCH,
                ),
                merchantName = if (transfer) null else s.merchant,
            )
            s.payment?.let { recurring.logged(it) }
            _state.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val id = _state.value.editing?.id ?: return
        viewModelScope.launch {
            transactions.delete(id)
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        const val ARG_KIND = "kind"

        /** Set to edit a saved transaction instead of adding one. */
        const val ARG_TRANSACTION = "transactionId"

        /** Set to start with this category picked, as a one-tap category on the widget does. */
        const val ARG_CATEGORY = "categoryId"

        /** Set to start filled in from a recurring payment's reminder. */
        const val ARG_PAYMENT = "paymentId"
    }
}

/** Picking the account on the other side of a transfer swaps them, so Save never dims for no visible reason. */
internal fun QuickAddUiState.withAccount(id: Long): QuickAddUiState =
    copy(accountId = id, toAccountId = if (id == toAccountId) accountId else toAccountId)

/** A category's suggestion can land From on To's account while the transfer fields are out of sight. */
internal fun QuickAddUiState.withDistinctAccounts(): QuickAddUiState =
    if (toAccountId != accountId) this else copy(toAccountId = accounts.firstOrNull { it.id != accountId }?.id)

/** What the keypad would have typed: 4.40 is "4.40", 12.00 is "12". */
internal fun Money.toInputText(): String {
    val fraction = minor % 100
    return if (fraction == 0L) "${minor / 100}" else "${minor / 100}.${fraction.toString().padStart(2, '0')}"
}
