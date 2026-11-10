package com.eminallahverdi.balance.feature.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.BankAlertRepository
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.DebtPayment
import com.eminallahverdi.balance.core.data.DebtRepository
import com.eminallahverdi.balance.core.data.PendingItem
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.data.Suggestion
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.Debt
import com.eminallahverdi.balance.core.model.DebtBalance
import com.eminallahverdi.balance.core.model.DebtDirection
import com.eminallahverdi.balance.core.model.DebtType
import com.eminallahverdi.balance.core.model.Merchant
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.OwedTo
import com.eminallahverdi.balance.core.model.PendingEntry
import com.eminallahverdi.balance.core.model.PendingStatus
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.SpokenEntry
import com.eminallahverdi.balance.core.model.SpokenLanguage
import com.eminallahverdi.balance.core.model.SpokenNames
import com.eminallahverdi.balance.core.model.Transaction
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.core.model.monthlyFor
import com.eminallahverdi.balance.core.model.namesIn
import com.eminallahverdi.balance.core.model.owedByPerson
import com.eminallahverdi.balance.core.model.parseSpokenEntry
import com.eminallahverdi.balance.core.model.payBack
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

/**
 * Category first: [CATEGORY] is the grid, [ENTRY] the one page with the amount and every detail. [DEBT] is the Debt tab's
 * own first page: the four new kinds, and who's to be paid back.
 */
enum class QuickAddStep { CATEGORY, ENTRY, DEBT }

/** What the phone's keyboard is open for, in place of the number pad. */
enum class QuickAddText { MERCHANT, NOTE, NAME }

/**
 * What the Debt tab is making. [who] is one name, or several for a bill ("Sam, Alex and Kamal"). [parts]: money lent or
 * borrowed comes back in this many monthly payments. [shareText]: the user's own part of a bill, empty until it's known.
 * [owed] is who's being paid back, or paying back.
 */
data class QuickDebt(val type: DebtType, val who: String = "", val parts: Int? = null, val shareText: String = "", val owed: OwedTo? = null) {
    val names: List<String> get() = namesIn(who)
    val share: Money? get() = if (shareText.isBlank()) Money.ZERO else Money.parse(shareText)?.takeIf { !it.isNegative }
}

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
    /** What a spoken sentence named, kept so picking a category afterwards doesn't overwrite it. */
    val spoken: SpokenEntry? = null,
    /** The sentence as it was heard, shown so the user can see what was understood. */
    val heard: String? = null,
    /** On the Debt tab: what it's making, once one of its kinds is picked. */
    val debt: QuickDebt? = null,
    /** Every debt and what's left of it, for the people to pay back and the names to pick from. */
    val debts: List<DebtBalance> = emptyList(),
    /** Ready-made entries from bank notifications, still waiting on To confirm, with what each would be saved as. */
    val waiting: List<PendingItem> = emptyList(),
    val saved: Boolean = false,
) {
    /**
     * What the bank sent of this tab's kind, offered on a new entry's grid: the payment just made is usually there already,
     * so it's checked and added rather than typed again.
     */
    val waitingHere: List<PendingItem>
        get() = if (editing != null || pending != null || payment != null || spoken != null) emptyList() else waiting.filter { it.entry.kind == kind }
    val amount: Money? get() = Money.parse(amountText)?.takeIf { it.minor > 0 }

    /** A new entry on a page with the kind tabs: the grid, the Debt tab's first page, or a transfer, which skips the grid. */
    val showsKinds: Boolean
        get() = editing == null && pending == null && payment == null &&
            (step == QuickAddStep.CATEGORY || step == QuickAddStep.DEBT || kind == TransactionKind.TRANSFER)

    val isDebt: Boolean get() = kind == TransactionKind.DEBT

    /** Everyone owed or owing, one line each, for Paid back. */
    val owedLines: List<OwedTo> get() = debts.owedByPerson()

    /** Names already used, one person each even from a shared bill, so someone is picked again without retyping. */
    val knownNames: List<String> get() = debts.flatMap { namesIn(it.debt.counterparty) }.distinctBy { it.lowercase() }

    /** What a debt can be saved with: who, how much, and what it needs besides, or not more than is owed when paying back. */
    private val canSaveDebt: Boolean
        get() {
            val d = debt ?: return false
            val total = amount ?: return false
            return when (d.type) {
                DebtType.LENT, DebtType.BORROWED -> d.who.isNotBlank() && accountId != null
                DebtType.PAID_FOR_ME -> d.who.isNotBlank() && categoryId != null
                DebtType.PAID_FOR_OTHERS -> d.who.isNotBlank() && categoryId != null && accountId != null && d.share?.let { it < total } == true
                DebtType.PAID_BACK -> d.owed != null && accountId != null && total <= d.owed.left
            }
        }

    val category: Category? get() = allCategories.firstOrNull { it.id == categoryId }
    val subCategories: List<Category> get() = allCategories.filter { it.parentId == categoryId && !it.isArchived }
    val subCategory: Category? get() = allCategories.firstOrNull { it.id == subCategoryId }
    val person: Person? get() = people.firstOrNull { it.id == personId }
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }
    val canSave: Boolean get() = if (isDebt) canSaveDebt else amount != null && accountId != null && when (kind) {
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
    private val debtRepository: DebtRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickAddUiState())
    val state: StateFlow<QuickAddUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { debtRepository.debts.collect { all -> _state.update { it.copy(debts = all) } } }
        viewModelScope.launch {
            bankAlerts.items(Instant.now()).collect { items -> _state.update { it.copy(waiting = items.filter { item -> item.entry.status == PendingStatus.WAITING }) } }
        }
        val requestedKind = savedState.get<String>(ARG_KIND)?.let { runCatching { TransactionKind.valueOf(it) }.getOrNull() } ?: TransactionKind.EXPENSE
        val editingId = savedState.get<Long>(ARG_TRANSACTION)
        val requestedCategory = savedState.get<Long>(ARG_CATEGORY)
        val paymentId = savedState.get<Long>(ARG_PAYMENT)
        val pendingId = savedState.get<Long>(ARG_PENDING)
        val spokenText = savedState.get<String>(ARG_SPOKEN)?.takeIf { it.isNotBlank() }
        val spokenLanguage = savedState.get<String>(ARG_SPOKEN_LANGUAGE).let { tag -> SpokenLanguage.entries.firstOrNull { it.tag == tag } } ?: SpokenLanguage.ENGLISH
        val receiptTotal = savedState.get<Long>(ARG_RECEIPT_TOTAL)
        viewModelScope.launch {
            val editing = editingId?.let { transactions.get(it) }
            if (editing != null) loadForEditing(editing)
            paymentId?.let { recurring.get(it) }?.let { loadFromPayment(it) }
            val pending = pendingId?.let { bankAlerts.entry(it) }
            pending?.let { loadFromPending(it) }
            val spoken = spokenText?.let { loadFromSpeech(it, spokenLanguage) }
            if (receiptTotal != null) loadFromReceipt(Money(receiptTotal), savedState.get<String>(ARG_RECEIPT_STORE).orEmpty(), savedState.get<Long>(ARG_RECEIPT_DATE), savedState.get<Long>(ARG_RECEIPT_CATEGORY))
            val startKind = editing?.kind ?: pending?.kind ?: spoken?.kind ?: requestedKind
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
                            step = if (s.loading) s.debt?.let { debtStep(it, s.categoryId) } ?: startStep(startKind, s.categoryId ?: requested?.id) else s.step,
                            kind = if (s.loading) startKind else s.kind,
                            accounts = pickableAccounts(accounts, s.editing),
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

    /** A sentence the user said. What it named is filled in, the rest as usual, and nothing is saved until they press Save. */
    private suspend fun loadFromSpeech(text: String, language: SpokenLanguage): SpokenEntry {
        val categories = catalog.categories.first()
        val debts = debtRepository.debts.first()
        val debtNames = debts.flatMap { namesIn(it.debt.counterparty) }.distinctBy { it.lowercase() }
        val names = SpokenNames(categories, catalog.people.first(), catalog.accounts.first(), catalog.merchants.first(), transactions.categoryUse(), debtNames)
        val spoken = parseSpokenEntry(text, names, language)
        val category = categories.firstOrNull { it.id == spoken.categoryId }
        spoken.debt?.let { type ->
            // Paying back needs someone with a debt open; anyone else leaves the Debt tab open to pick them.
            val owed = if (type == DebtType.PAID_BACK) debts.owedByPerson().firstOrNull { line -> namesIn(line.name).any { it.equals(spoken.counterparty, ignoreCase = true) } } else null
            val debt = when {
                type != DebtType.PAID_BACK -> QuickDebt(type, who = spoken.counterparty.orEmpty())
                owed != null -> QuickDebt(type, who = owed.name, owed = owed)
                else -> null
            }
            _state.update {
                it.copy(
                    spoken = spoken,
                    heard = text,
                    debt = debt,
                    amountText = (spoken.amount ?: owed?.left)?.toInputText().orEmpty(),
                    accountId = spoken.accountId ?: it.accountId,
                    categoryId = category?.parentId ?: category?.id,
                    subCategoryId = category?.id?.takeIf { category.parentId != null },
                    date = spoken.daysAgo?.let { days -> LocalDate.now().minusDays(days.toLong()) } ?: it.date,
                )
            }
            return spoken
        }
        _state.update {
            it.copy(
                spoken = spoken,
                heard = text,
                amountText = spoken.amount?.toInputText().orEmpty(),
                accountId = spoken.accountId ?: it.accountId,
                categoryId = category?.parentId ?: category?.id,
                subCategoryId = category?.id?.takeIf { category.parentId != null },
                merchant = spoken.merchant.orEmpty(),
                personId = spoken.personId,
                date = spoken.daysAgo?.let { days -> LocalDate.now().minusDays(days.toLong()) } ?: it.date,
            )
        }
        return spoken
    }

    /** A scanned receipt: its total, shop and day, in the groceries category. Nothing is saved until the user presses Save. */
    private suspend fun loadFromReceipt(total: Money, store: String, epochDay: Long?, categoryId: Long?) {
        val category = catalog.categories.first().firstOrNull { it.id == categoryId }
        _state.update {
            it.copy(
                amountText = total.toInputText(),
                categoryId = category?.id,
                personId = category?.defaultPersonId,
                merchant = store,
                date = epochDay?.let(LocalDate::ofEpochDay) ?: it.date,
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
            debt = null,
        )
    }

    /**
     * One of the Debt tab's four kinds. Money lent or borrowed goes straight to its page; a bill, or a share of one, picks
     * its category first, as an expense does, because some of it becomes spending.
     */
    fun pickDebt(type: DebtType) = _state.update {
        it.copy(debt = QuickDebt(type), categoryId = null, subCategoryId = null, step = if (type.hasCategory) QuickAddStep.CATEGORY else QuickAddStep.ENTRY, pickedFromGrid = false)
    }

    /** Paying someone back, or them paying: what's left starts in the amount, and the account is where it goes or comes from. */
    fun payBack(owed: OwedTo) = _state.update {
        it.copy(debt = QuickDebt(DebtType.PAID_BACK, who = owed.name, owed = owed), amountText = owed.left.toInputText(), categoryId = null, step = QuickAddStep.ENTRY)
    }

    /** A bank entry, opened to check: filled in as To confirm's Edit does, and saving it takes it off To confirm. */
    fun openPending(item: PendingItem) {
        viewModelScope.launch {
            loadFromPending(item.entry)
            _state.update {
                it.copy(
                    kind = item.entry.kind,
                    categories = topLevel(it.allCategories, item.entry.kind),
                    step = if (item.entry.kind == TransactionKind.TRANSFER || it.categoryId != null) QuickAddStep.ENTRY else QuickAddStep.CATEGORY,
                    pickedFromGrid = false,
                )
            }
            _state.value.category?.let { loadUsualMerchants(it) }
        }
    }

    /** A bank entry added as it was guessed, as To confirm's Confirm does; the sheet closes, as after Save. */
    fun addPending(item: PendingItem) {
        viewModelScope.launch {
            bankAlerts.confirm(item.entry.id)
            _state.update { it.copy(saved = true) }
        }
    }

    /** Back to the Debt tab's first page, as tapping the kind on an entry does. */
    fun showDebtKinds() = _state.update { it.copy(step = QuickAddStep.DEBT, debt = null, categoryId = null, subCategoryId = null, typing = null) }

    fun setWho(text: String) = _state.update { s -> s.copy(debt = s.debt?.copy(who = text)) }

    /** A known name: one person for a loan or a share, and for a bill one more of those it's split with, or one less. */
    fun pickName(name: String) = _state.update { s ->
        val d = s.debt ?: return@update s
        val who = if (d.type != DebtType.PAID_FOR_OTHERS) {
            if (d.who.trim().equals(name, ignoreCase = true)) "" else name
        } else {
            val names = d.names
            val rest = if (names.any { it.equals(name, ignoreCase = true) }) names.filterNot { it.equals(name, ignoreCase = true) } else names + name
            joinNames(rest)
        }
        s.copy(debt = d.copy(who = who), typing = null)
    }

    fun setParts(parts: Int?) = _state.update { s -> s.copy(debt = s.debt?.copy(parts = parts)) }

    fun setShare(share: Money?) = _state.update { s -> s.copy(debt = s.debt?.copy(shareText = share?.takeIf { it.minor > 0 }?.toInputText().orEmpty())) }

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
        if (!s.canSave) return
        if (s.isDebt) {
            viewModelScope.launch {
                saveDebt(s, amount)
                _state.update { it.copy(saved = true) }
            }
            return
        }
        val accountId = s.accountId ?: return
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

    /**
     * A debt goes through the same repository as Loans & debts, so the money works the same from either: a loan moves its
     * account, a share someone paid for you moves nothing until it's paid back, a bill leaves the account with the user's
     * share as spending, and a payback settles the oldest debts first.
     */
    private suspend fun saveDebt(s: QuickAddUiState, amount: Money) {
        val d = s.debt ?: return
        val note = s.note.trim()
        val categoryId = s.subCategoryId ?: s.categoryId
        when (d.type) {
            DebtType.LENT, DebtType.BORROWED -> debtRepository.add(
                Debt(0, d.who.trim(), d.type.direction, amount, note = note, date = s.date, monthly = d.parts?.takeIf { it > 1 }?.let { monthlyFor(amount, it) }),
                s.accountId,
            )
            DebtType.PAID_FOR_ME -> debtRepository.add(
                Debt(0, d.who.trim(), DebtDirection.I_OWE, amount, categoryId = categoryId, note = note.ifBlank { s.merchant.trim() }, date = s.date),
                accountId = null,
            )
            DebtType.PAID_FOR_OTHERS -> debtRepository.addPaidForOthers(
                Debt(0, d.who.trim(), DebtDirection.OWED_TO_ME, amount, categoryId = categoryId, note = note.ifBlank { s.merchant.trim() }, date = s.date),
                s.accountId ?: return,
                d.share ?: Money.ZERO,
                s.merchant.trim().takeIf { it.isNotEmpty() },
                s.personId,
            )
            DebtType.PAID_BACK -> {
                val owed = d.owed ?: return
                val payments = payBack(amount, owed.debts).map { (balance, part) -> DebtPayment(balance.debt, part, s.personFor(balance.debt)) }
                debtRepository.pay(payments, s.accountId ?: return, s.date)
            }
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

        /** Set to start filled in from what the user said, in the language [ARG_SPOKEN_LANGUAGE] names. */
        const val ARG_SPOKEN = "spoken"
        const val ARG_SPOKEN_LANGUAGE = "spokenLanguage"

        /** Set to start filled in from a scanned receipt: its total, shop, day (epochDay) and the groceries category. */
        const val ARG_RECEIPT_TOTAL = "receiptTotal"
        const val ARG_RECEIPT_STORE = "receiptStore"
        const val ARG_RECEIPT_DATE = "receiptDate"
        const val ARG_RECEIPT_CATEGORY = "receiptCategory"

        /** Set to start filled in from a bank notification on To confirm. */
        const val ARG_PENDING = "pendingId"
    }
}

private fun categoryType(kind: TransactionKind) = if (kind == TransactionKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE

/**
 * A transfer has no category, and an entry that arrives with one goes straight to its page. A debt starts on the Debt
 * tab's own page. Everything else starts on the grid.
 */
internal fun startStep(kind: TransactionKind, categoryId: Long?): QuickAddStep = when {
    kind == TransactionKind.DEBT -> QuickAddStep.DEBT
    kind == TransactionKind.TRANSFER || categoryId != null -> QuickAddStep.ENTRY
    else -> QuickAddStep.CATEGORY
}

/** Where a debt that arrives filled in, as a spoken one does, opens: its page, or the grid when it still needs a category. */
internal fun debtStep(debt: QuickDebt, categoryId: Long?): QuickAddStep =
    if (debt.type.hasCategory && categoryId == null) QuickAddStep.CATEGORY else QuickAddStep.ENTRY

/** One step back: the keyboard, then the grid, then, on the Debt tab, its first page. Null means there's nowhere left. */
internal fun QuickAddUiState.afterBack(): QuickAddUiState? = when {
    typing != null -> copy(typing = null)
    step == QuickAddStep.ENTRY && pickedFromGrid -> copy(step = QuickAddStep.CATEGORY)
    isDebt && step != QuickAddStep.DEBT && editing == null -> copy(step = QuickAddStep.DEBT, debt = null, categoryId = null, subCategoryId = null)
    else -> null
}

/** "Sam, Alex and Kamal": how names read in a bill's counterparty, and how [namesIn] takes them apart again. */
internal fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names.single()
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}

/** Who a paid-back share is spending for: whoever its category is usually for, or who the page has picked. */
internal fun QuickAddUiState.personFor(debt: Debt): Long? {
    val category = allCategories.firstOrNull { it.id == debt.categoryId } ?: return personId
    return category.defaultPersonId ?: allCategories.firstOrNull { it.id == category.parentId }?.defaultPersonId ?: personId
}

/** The category's last entry, or a frequent merchant's own last use. A bank notification keeps its merchant and account. */
internal fun QuickAddUiState.withSuggestion(suggestion: Suggestion, frequent: Merchant? = null): QuickAddUiState {
    // What the user said outranks what's usual for the category.
    val person = spoken?.personId ?: frequent?.lastPersonId ?: suggestion.personId
    val account = spoken?.accountId ?: frequent?.lastAccountId ?: suggestion.accountId
    return copy(
        subCategoryId = if (frequent == null) suggestion.subCategoryId else frequent.lastCategoryId?.takeIf { id -> subCategories.any { it.id == id } },
        merchant = frequent?.name ?: if (pending != null || spoken?.merchant != null) merchant else suggestion.merchantName.orEmpty(),
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

/** An entry on an archived account still shows it, so nothing reads blank and the entry can be moved off it. Nothing new can use one. */
internal fun pickableAccounts(accounts: List<Account>, editing: Transaction?): List<Account> =
    accounts.filter { !it.isArchived || it.id == editing?.accountId || it.id == editing?.toAccountId }

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
