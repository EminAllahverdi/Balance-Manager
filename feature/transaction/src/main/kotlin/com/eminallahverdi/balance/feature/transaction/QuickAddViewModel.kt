package com.eminallahverdi.balance.feature.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.BankAlertRepository
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.data.Suggestion
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.Merchant
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.PendingEntry
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.Transaction
import com.eminallahverdi.balance.core.model.TransactionKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val FREQUENT_MERCHANTS = 3
private const val MERCHANT_CHIPS = 4
private const val MERCHANT_MATCHES = 3

/** Category first: [CATEGORY] is the grid, [ENTRY] the one page with the amount and every detail. */
enum class QuickAddStep { CATEGORY, ENTRY }

/** What the phone's keyboard is open for, in place of the number pad. */
enum class QuickAddText { MERCHANT, NOTE }

/** A shop used often, and the category it was last filed under. */
data class FrequentMerchant(val merchant: Merchant, val category: Category)

data class QuickAddUiState(
    val loading: Boolean = true,
    val step: QuickAddStep = QuickAddStep.CATEGORY,
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
    val merchants: List<Merchant> = emptyList(),
    val typing: QuickAddText? = null,
    /** Set when the category was picked on the grid, so Back returns to it. */
    val pickedFromGrid: Boolean = false,
    val people: List<Person> = emptyList(),
    val personId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    /** The saved transaction being changed, or null when adding a new one. */
    val editing: Transaction? = null,
    /** The reminder this entry was filled in from. Saving it moves that reminder to its next date. */
    val payment: RecurringPayment? = null,
    /** Set when this is a bank notification being edited. It keeps the bank's merchant and account when the category changes. */
    val pending: PendingEntry? = null,
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
    val frequent: List<FrequentMerchant>
        get() = if (kind == TransactionKind.TRANSFER) emptyList() else frequentMerchants(merchants, allCategories, categoryType(kind))

    /** The category's usual merchants, with a typed one in front until it becomes one of them. */
    val merchantChips: List<String>
        get() {
            val typed = merchant.trim()
            val isUsual = typed.isEmpty() || recentMerchants.any { it.equals(typed, ignoreCase = true) }
            return (if (isUsual) recentMerchants else listOf(typed) + recentMerchants).take(MERCHANT_CHIPS)
        }
    val merchantMatches: List<String> get() = merchantMatches(merchants, merchant)
    val isNewMerchant: Boolean get() = merchant.isNotBlank() && merchants.none { it.name.equals(merchant.trim(), ignoreCase = true) }
}

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val transactions: TransactionRepository,
    private val recurring: RecurringRepository,
    private val bankAlerts: BankAlertRepository,
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
        val pendingId = savedState.get<Long>(ARG_PENDING)
        viewModelScope.launch {
            val editing = editingId?.let { transactions.get(it) }
            if (editing != null) loadForEditing(editing)
            paymentId?.let { recurring.get(it) }?.let { loadFromPayment(it) }
            val pending = pendingId?.let { bankAlerts.entry(it) }
            pending?.let { loadFromPending(it) }
            val startKind = editing?.kind ?: pending?.kind ?: requestedKind
            val defaultAccount = prefs.current().defaultExpenseAccountId
            combine(catalog.accounts, catalog.categories, catalog.people, catalog.merchants) { a, c, p, m -> Catalog(a, c, p, m) }
                .collect { (accounts, categories, people, merchants) ->
                    val active = accounts.filterNot { it.isArchived }
                    val firstLoad = _state.value.loading
                    val requested = topLevel(categories, startKind).firstOrNull { it.id == requestedCategory }.takeIf { firstLoad }
                    _state.update { s ->
                        val accountId = s.accountId ?: active.firstOrNull { it.id == defaultAccount }?.id ?: active.firstOrNull()?.id
                        s.copy(
                            loading = false,
                            step = if (s.loading) startStep(startKind, s.categoryId ?: requested?.id) else s.step,
                            kind = if (s.loading) startKind else s.kind,
                            accounts = active,
                            accountId = accountId,
                            toAccountId = s.toAccountId ?: active.firstOrNull { it.id != accountId }?.id,
                            allCategories = categories,
                            categories = topLevel(categories, if (s.loading) startKind else s.kind),
                            merchants = merchants,
                            people = people.filterNot { it.isArchived },
                            personId = s.personId ?: people.firstOrNull()?.id,
                        )
                    }
                    if (firstLoad) {
                        if (requested != null) pick(requested, fromGrid = false) else _state.value.category?.let { loadUsualMerchants(it) }
                    }
                }
        }
    }

    private data class Catalog(val accounts: List<Account>, val categories: List<Category>, val people: List<Person>, val merchants: List<Merchant>)

    /** An entry that arrives filled in keeps its details, and only needs its category's merchants for the chips. */
    private suspend fun loadUsualMerchants(category: Category) {
        val usual = transactions.suggestFor(category, subCategoriesOf(category)).recentMerchants
        _state.update { it.copy(recentMerchants = usual) }
    }

    private fun subCategoriesOf(category: Category) = _state.value.allCategories.filter { it.parentId == category.id && !it.isArchived }

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

    /** A ready-made entry from a bank notification. Nothing is saved until the user presses Save. */
    private suspend fun loadFromPending(entry: PendingEntry) {
        val guess = bankAlerts.guess(entry)
        val category = catalog.categories.first().firstOrNull { it.id == guess.categoryId }
        _state.update {
            it.copy(
                pending = entry,
                amountText = entry.amount.toInputText(),
                accountId = guess.accountId,
                toAccountId = guess.toAccountId,
                categoryId = category?.parentId ?: category?.id,
                subCategoryId = category?.id?.takeIf { category.parentId != null },
                merchant = guess.merchantName,
                personId = guess.personId,
                date = entry.postedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
            )
        }
    }

    private fun topLevel(all: List<Category>, kind: TransactionKind): List<Category> =
        all.filter { it.parentId == null && it.type == categoryType(kind) && !it.isArchived }

    fun setKind(kind: TransactionKind) = _state.update {
        if (kind == it.kind) return@update it
        it.withDistinctAccounts().copy(
            kind = kind,
            categories = topLevel(it.allCategories, kind),
            categoryId = null,
            subCategoryId = null,
            step = startStep(kind, categoryId = null),
            pickedFromGrid = false,
        )
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

    /** Tapping the category that's already picked goes back to the entry as it was. */
    fun selectCategory(category: Category) {
        if (category.id == _state.value.categoryId) _state.update { it.copy(step = QuickAddStep.ENTRY) } else pick(category, fromGrid = true)
    }

    /** A frequent merchant fills in what it was filed under last time, so only the amount is left. */
    fun selectFrequent(item: FrequentMerchant) {
        val top = _state.value.allCategories.firstOrNull { it.id == item.category.parentId } ?: item.category
        pick(top, fromGrid = true, frequent = item.merchant)
    }

    private fun pick(category: Category, fromGrid: Boolean, frequent: Merchant? = null) {
        _state.update {
            it.copy(categoryId = category.id, subCategoryId = null, merchant = frequent?.name ?: it.merchant, step = QuickAddStep.ENTRY, pickedFromGrid = fromGrid)
        }
        viewModelScope.launch {
            val suggestion = transactions.suggestFor(category, subCategoriesOf(category))
            _state.update { it.withSuggestion(suggestion, frequent) }
        }
    }

    fun showCategories() = _state.update { it.copy(step = QuickAddStep.CATEGORY, pickedFromGrid = true, typing = null) }

    /** Back closes the keyboard first, then returns to the grid. False means there's nowhere left, so the sheet closes. */
    fun back(): Boolean {
        _state.value = _state.value.afterBack() ?: return false
        return true
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

    /** A merchant stays optional, so tapping the selected one clears it. */
    fun toggleMerchant(name: String) = _state.update { it.copy(merchant = if (it.merchant.trim().equals(name, ignoreCase = true)) "" else name) }
    fun pickMerchant(name: String) = _state.update { it.copy(merchant = name, typing = null) }
    fun setNote(text: String) = _state.update { it.copy(note = text) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun startTyping(text: QuickAddText) = _state.update { it.copy(typing = text) }
    fun stopTyping() = _state.update { it.copy(typing = null) }

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
                externalId = s.pending?.externalId,
            )
            s.payment?.let { recurring.logged(it) }
            s.pending?.let { bankAlerts.markAdded(it, s.merchant) }
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

        /** Set to start filled in from a bank notification on To confirm. */
        const val ARG_PENDING = "pendingId"
    }
}

private fun categoryType(kind: TransactionKind) = if (kind == TransactionKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE

/** A transfer has no category, and an entry that arrives with one goes straight to its page. Everything else starts on the grid. */
internal fun startStep(kind: TransactionKind, categoryId: Long?): QuickAddStep =
    if (kind == TransactionKind.TRANSFER || categoryId != null) QuickAddStep.ENTRY else QuickAddStep.CATEGORY

internal fun QuickAddUiState.afterBack(): QuickAddUiState? = when {
    typing != null -> copy(typing = null)
    step == QuickAddStep.ENTRY && pickedFromGrid -> copy(step = QuickAddStep.CATEGORY)
    else -> null
}

/** The category's last entry, or a frequent merchant's own last use. A bank notification keeps its merchant and account. */
internal fun QuickAddUiState.withSuggestion(suggestion: Suggestion, frequent: Merchant? = null): QuickAddUiState {
    val person = frequent?.lastPersonId ?: suggestion.personId
    val account = frequent?.lastAccountId ?: suggestion.accountId
    return copy(
        subCategoryId = if (frequent == null) suggestion.subCategoryId else frequent.lastCategoryId?.takeIf { id -> subCategories.any { it.id == id } },
        merchant = frequent?.name ?: if (pending != null) merchant else suggestion.merchantName.orEmpty(),
        recentMerchants = suggestion.recentMerchants,
        personId = person?.takeIf { id -> people.any { it.id == id } } ?: people.firstOrNull()?.id,
        accountId = account?.takeIf { id -> pending == null && accounts.any { it.id == id } } ?: accountId,
    )
}

/** The shops used most, skipping any whose category was archived. */
internal fun frequentMerchants(merchants: List<Merchant>, categories: List<Category>, type: CategoryType): List<FrequentMerchant> {
    val active = categories.filterNot { it.isArchived }.associateBy(Category::id)
    return merchants.sortedByDescending { it.useCount }
        .mapNotNull { merchant ->
            val category = active[merchant.lastCategoryId]?.takeIf { it.type == type && (it.parentId == null || it.parentId in active) }
            category?.let { FrequentMerchant(merchant, it) }
        }
        .take(FREQUENT_MERCHANTS)
}

/** Merchants from every category that match what's typed, so a new spelling doesn't start a second one. */
internal fun merchantMatches(merchants: List<Merchant>, typed: String): List<String> {
    val text = typed.trim()
    if (text.isEmpty()) return emptyList()
    return merchants.filter { it.name.contains(text, ignoreCase = true) && !it.name.equals(text, ignoreCase = true) }
        .sortedWith(compareByDescending<Merchant> { it.name.startsWith(text, ignoreCase = true) }.thenByDescending { it.useCount })
        .map { it.name }
        .take(MERCHANT_MATCHES)
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
