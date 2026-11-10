package com.eminallahverdi.balance.feature.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.DebtPayment
import com.eminallahverdi.balance.core.data.DebtRepository
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.Debt
import com.eminallahverdi.balance.core.model.DebtBalance
import com.eminallahverdi.balance.core.model.DebtDirection
import com.eminallahverdi.balance.core.model.LoanTerms
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.RecurringFrequency
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.core.model.annuityPayment
import com.eminallahverdi.balance.core.model.evenShare
import com.eminallahverdi.balance.core.model.formatRate
import com.eminallahverdi.balance.core.model.monthlyFor
import com.eminallahverdi.balance.core.model.namesIn
import com.eminallahverdi.balance.core.model.parseRate
import com.eminallahverdi.balance.core.model.sum
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

internal fun positiveAmount(text: String): Money? = Money.parse(text)?.takeIf { it.minor > 0 }

/** What a typed field starts with: 16.31, and 1184.00 without the separator. */
internal fun Money.toFieldText(): String = MoneyFormat.format(this).removePrefix(MoneyFormat.SYMBOL).replace(",", "")

/**
 * A bank loan is a recurring payment with a fixed number of payments, so it reminds the same way. What the contract says
 * (what was borrowed, the rate, the commission) is optional, and splits what's left into what was borrowed and interest.
 */
data class LoanDraft(
    val id: Long = 0,
    val name: String = "",
    val amountText: String = "",
    val termText: String = "",
    val paymentsMadeText: String = "",
    val lastAmountText: String = "",
    val principalText: String = "",
    val rateText: String = "",
    val commissionText: String = "",
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val personId: Long? = null,
    val accountId: Long? = null,
    val nextDate: LocalDate,
    val anchorDay: Int = nextDate.dayOfMonth,
) {
    val isNew: Boolean get() = id == 0L
    val amount: Money? get() = positiveAmount(amountText)
    private val term: Int? get() = termText.toIntOrNull()?.takeIf { it > 0 }

    /** An empty optional field is fine. One that's filled in has to make sense. */
    private val paymentsMade: Int? get() = if (paymentsMadeText.isBlank()) 0 else paymentsMadeText.toIntOrNull()?.takeIf { it >= 0 }
    private val principal: Money? get() = positiveAmount(principalText)
    private val rateBp: Int? get() = parseRate(rateText)
    private val commission: Money? get() = positiveAmount(commissionText)
    private val optionalsAreValid: Boolean
        get() = (lastAmountText.isBlank() || positiveAmount(lastAmountText) != null) &&
            (principalText.isBlank() || principal != null) &&
            (rateText.isBlank() || rateBp != null) &&
            (commissionText.isBlank() || commission != null)

    /** The term, less the payments already made. */
    val paymentsLeft: Int?
        get() {
            val made = paymentsMade ?: return null
            return term?.minus(made)?.takeIf { it >= 0 }
        }

    /** The monthly payment the amount, rate and term work out to, offered when it isn't what's typed. */
    val suggestedAmount: Money?
        get() {
            val principal = principal ?: return null
            val rate = rateBp ?: return null
            return annuityPayment(principal, rate, term ?: return null)?.takeIf { it != amount }
        }

    val canSave: Boolean
        get() = name.isNotBlank() && amount != null && paymentsLeft != null && optionalsAreValid && categoryId != null && accountId != null

    /** The loan as typed so far, for the sums under the form, before a category or account is picked. */
    val preview: RecurringPayment?
        get() {
            if (!optionalsAreValid) return null
            return RecurringPayment(
                id = id,
                name = name.trim(),
                amount = amount ?: return null,
                accountId = accountId ?: 0,
                categoryId = subCategoryId ?: categoryId ?: 0,
                personId = personId,
                frequency = RecurringFrequency.MONTHLY,
                nextDate = nextDate,
                anchorDay = anchorDay,
                loan = LoanTerms(paymentsMade ?: return null, paymentsLeft ?: return null, positiveAmount(lastAmountText), principal, rateBp, commission),
            )
        }

    fun toPayment(): RecurringPayment? = preview?.takeIf { canSave }
}

internal fun RecurringPayment.toLoanDraft(categories: List<Category>): LoanDraft {
    val category = categories.firstOrNull { it.id == categoryId }
    return LoanDraft(
        id = id,
        name = name,
        amountText = amount.toFieldText(),
        termText = loan?.term?.toString().orEmpty(),
        paymentsMadeText = loan?.paymentsMade?.toString().orEmpty(),
        lastAmountText = loan?.lastPayment?.toFieldText().orEmpty(),
        principalText = loan?.principal?.toFieldText().orEmpty(),
        rateText = loan?.annualRateBp?.let(::formatRate).orEmpty(),
        commissionText = loan?.commission?.toFieldText().orEmpty(),
        categoryId = category?.parentId ?: categoryId,
        subCategoryId = categoryId.takeIf { category?.parentId != null },
        personId = personId,
        accountId = accountId,
        nextDate = nextDate,
        anchorDay = anchorDay,
    )
}

enum class DebtKind(val label: String) {
    PAID_FOR_ME("Paid for me"),
    BORROWED("I borrowed"),
    LENT("I lent"),
}

data class DebtDraft(
    val kind: DebtKind = DebtKind.PAID_FOR_ME,
    val counterparty: String = "",
    val amountText: String = "",
    val note: String = "",
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val accountId: Long? = null,
    val date: LocalDate,
    /** A loan that was already running: its money moved before, so nothing moves now. */
    val running: Boolean = false,
    val paidBeforeText: String = "",
    val paymentsLeftText: String = "",
) {
    val amount: Money? get() = positiveAmount(amountText)

    /** Only a plain loan can be running or paid back in parts. A share of lunch is paid back at once. */
    val isLoan: Boolean get() = kind != DebtKind.PAID_FOR_ME
    val isRunning: Boolean get() = isLoan && running

    private val paidBefore: Money?
        get() = if (!isRunning || paidBeforeText.isBlank()) Money.ZERO else Money.parse(paidBeforeText)?.takeIf { !it.isNegative }

    /** What's still owed, once what was paid before is taken off. */
    val left: Money?
        get() {
            val total = amount ?: return null
            return paidBefore?.let { total - it }?.takeIf { it.minor > 0 }
        }

    private val paymentsLeft: Int? get() = paymentsLeftText.toIntOrNull()?.takeIf { it > 0 }

    /** The monthly payment that settles what's left over the payments typed. */
    val monthly: Money? get() = if (isLoan) left?.let { l -> paymentsLeft?.let { monthlyFor(l, it) } } else null

    val canSave: Boolean
        get() = counterparty.isNotBlank() && left != null && (!isLoan || paymentsLeftText.isBlank() || paymentsLeft != null) &&
            when {
                !isLoan -> categoryId != null
                isRunning -> true
                else -> accountId != null
            }

    fun toDebt(): Debt? {
        if (!canSave) return null
        return Debt(
            id = 0,
            counterparty = counterparty.trim(),
            direction = if (kind == DebtKind.LENT) DebtDirection.OWED_TO_ME else DebtDirection.I_OWE,
            amount = amount ?: return null,
            categoryId = if (kind == DebtKind.PAID_FOR_ME) subCategoryId ?: categoryId else null,
            note = note.trim(),
            date = date,
            paidBefore = paidBefore ?: return null,
            monthly = monthly,
        )
    }

    /** Only plain loans move money when they're made, and a running one already did. */
    val openingAccount: Long? get() = accountId.takeUnless { !isLoan || isRunning }
}

/**
 * A bill paid for others too, like lunch for the table. The whole of it leaves the account: the user's share is spending,
 * and the rest is owed back by [who]. The share can stay empty and be marked later, from whatever isn't paid back.
 */
data class BillDraft(
    val totalText: String = "",
    val who: String = "",
    val mineText: String = "",
    val merchant: String = "",
    val note: String = "",
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val personId: Long? = null,
    val accountId: Long? = null,
    val date: LocalDate,
) {
    val total: Money? get() = positiveAmount(totalText)
    val mine: Money? get() = if (mineText.isBlank()) Money.ZERO else Money.parse(mineText)?.takeIf { !it.isNegative }
    val others: List<String> get() = namesIn(who)

    /** What the others owe, once the share is taken off. Nothing to owe means there's no one to wait for. */
    val owed: Money? get() = total?.let { t -> mine?.let { m -> (t - m).takeIf { it.minor > 0 } } }

    /** The user's share if it's split evenly with everyone named. */
    val evenSplit: Money? get() = total?.takeIf { others.isNotEmpty() }?.let { evenShare(it, others.size) }

    val canSave: Boolean get() = who.isNotBlank() && owed != null && categoryId != null && accountId != null

    fun toDebt(): Debt? {
        if (!canSave) return null
        return Debt(
            id = 0,
            counterparty = who.trim(),
            direction = DebtDirection.OWED_TO_ME,
            amount = total ?: return null,
            categoryId = subCategoryId ?: categoryId,
            note = note.trim().ifBlank { merchant.trim() },
            date = date,
        )
    }
}

/** Paying one debt can be for part of it. Paying several settles each in full, as its own entry. */
data class PayDraft(
    val debts: List<DebtBalance>,
    val amountText: String,
    val accountId: Long?,
    val date: LocalDate,
) {
    val isSingle: Boolean get() = debts.size == 1
    val total: Money? get() = if (isSingle) positiveAmount(amountText)?.takeIf { it <= debts.single().left } else debts.map { it.left }.sum()
    val canPay: Boolean get() = total != null && accountId != null

    /** Of a bill paid for others, what isn't coming back can be marked as the user's own share instead. */
    val canMarkShare: Boolean get() = isSingle && debts.single().debt.isPaidForOthers && total != null

    fun amountFor(balance: DebtBalance): Money = (if (isSingle) total else null) ?: balance.left
}

sealed interface LoansSheet {
    data object New : LoansSheet
    data class Loan(val draft: LoanDraft) : LoansSheet
    data class NewDebt(val draft: DebtDraft) : LoansSheet
    data class NewBill(val draft: BillDraft) : LoansSheet
    data class Pay(val draft: PayDraft) : LoansSheet

    /** A new name for everyone filed under [from]. */
    data class Rename(val from: String, val text: String) : LoansSheet {
        val canSave: Boolean get() = text.isNotBlank() && text.trim() != from.trim()
    }
}

/** Everything open with one person, in one direction. */
data class PersonDebts(val name: String, val direction: DebtDirection, val debts: List<DebtBalance>) {
    val total: Money get() = debts.map { it.left }.sum()
    val isSensitive: Boolean get() = debts.any { it.debt.movesMoney }
}

internal fun List<DebtBalance>.groupedByPerson(): List<PersonDebts> =
    filterNot { it.isSettled }
        .groupBy { it.debt.counterparty.lowercase() to it.debt.direction }
        .values
        .map { PersonDebts(it.first().debt.counterparty, it.first().debt.direction, it) }

/** A card bought on and paid back later, with what's gone on it this month. */
data class CardUse(val account: Account, val used: Money)

internal fun cardsInUse(accounts: List<Account>, month: List<TransactionDetails>): List<CardUse> =
    accounts.filter { it.type == AccountType.CREDIT_CARD && !it.isArchived }.map { account ->
        CardUse(account, month.filter { it.transaction.kind == TransactionKind.EXPENSE && it.account.id == account.id }.map { it.transaction.amount }.sum())
    }

data class LoansUiState(
    val loading: Boolean = true,
    val loans: List<RecurringPayment> = emptyList(),
    val cards: List<CardUse> = emptyList(),
    val debts: List<DebtBalance> = emptyList(),
    val categories: List<Category> = emptyList(),
    val people: List<Person> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val preferredAccountId: Long? = null,
    val hideAmounts: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val sheet: LoansSheet? = null,
) {
    /** Payments that fall this month, plus any still waiting from before it. */
    val dueThisMonth: Money
        get() = loans.filter { !it.isFinished && !it.nextDate.isAfter(YearMonth.from(today).atEndOfMonth()) }.map { it.amountDue }.sum()

    val stillToPay: Money get() = loans.map { it.stillToPay }.sum()
    val isEmpty: Boolean get() = loans.isEmpty() && cards.isEmpty() && openDebts.isEmpty()

    val openDebts: List<PersonDebts> get() = debts.groupedByPerson()

    /** Names already used, one person each even from a shared bill, for picking someone again without retyping them. */
    val knownNames: List<String> get() = debts.flatMap { namesIn(it.debt.counterparty) }.distinctBy { it.lowercase() }

    val topCategories: List<Category> get() = categories.filter { it.parentId == null && it.type == CategoryType.EXPENSE && !it.isArchived }
    val activeAccounts: List<Account> get() = accounts.filterNot { it.isArchived }
    val activePeople: List<Person> get() = people.filterNot { it.isArchived }
    val defaultAccountId: Long? get() = activeAccounts.firstOrNull { it.id == preferredAccountId }?.id ?: activeAccounts.firstOrNull()?.id

    fun subCategoriesOf(id: Long?): List<Category> = categories.filter { it.parentId == id && id != null && !it.isArchived }
    fun category(id: Long?): Category? = categories.firstOrNull { it.id == id }

    /** "Work · Lunch", or just the category at the top level. */
    fun categoryPath(id: Long?): String? {
        val category = category(id) ?: return null
        return listOfNotNull(category(category.parentId)?.name, category.name).joinToString(" · ")
    }

    /** Who a paid-back expense is for: whoever the category is usually for, then the first person. */
    fun personFor(debt: Debt): Long? = category(debt.categoryId)?.let { it.defaultPersonId ?: category(it.parentId)?.defaultPersonId } ?: activePeople.firstOrNull()?.id
}

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val recurring: RecurringRepository,
    private val debtRepository: DebtRepository,
    catalog: CatalogRepository,
    transactions: TransactionRepository,
    prefs: UserPreferencesRepository,
) : ViewModel() {

    private val sheet = MutableStateFlow<LoansSheet?>(null)
    private val month = transactions.observeDetails(DateRange.month(YearMonth.now()))

    val state: StateFlow<LoansUiState> = combine(
        combine(recurring.payments, debtRepository.debts, month) { payments, debts, entries -> Triple(payments, debts, entries) },
        combine(catalog.accounts, catalog.categories, catalog.people) { accounts, categories, people -> Triple(accounts, categories, people) },
        prefs.preferences,
        sheet,
    ) { (payments, debts, entries), (accounts, categories, people), preferences, open ->
        LoansUiState(
            loading = false,
            loans = payments.filter { it.loan != null },
            cards = cardsInUse(accounts, entries),
            debts = debts,
            categories = categories,
            people = people,
            accounts = accounts,
            preferredAccountId = preferences.defaultExpenseAccountId,
            hideAmounts = preferences.hideAmountsInApp,
            today = LocalDate.now(),
            sheet = open,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoansUiState())

    fun openNew() = sheet.update { LoansSheet.New }
    fun closeSheet() = sheet.update { null }

    fun newLoan() = sheet.update { LoansSheet.Loan(LoanDraft(nextDate = LocalDate.now().plusMonths(1), accountId = state.value.defaultAccountId)) }
    fun editLoan(loan: RecurringPayment) = sheet.update { LoansSheet.Loan(loan.toLoanDraft(state.value.categories)) }

    fun updateLoan(change: LoanDraft.() -> LoanDraft) = sheet.update { (it as? LoansSheet.Loan)?.let { open -> open.copy(draft = open.draft.change()) } ?: it }

    fun selectLoanCategory(category: Category) = updateLoan { copy(categoryId = category.id, subCategoryId = null, personId = category.defaultPersonId ?: personId) }

    fun selectLoanSubCategory(category: Category) = updateLoan {
        val next = category.id.takeIf { it != subCategoryId }
        copy(subCategoryId = next, personId = category.defaultPersonId.takeIf { next != null } ?: personId)
    }

    fun saveLoan() {
        val payment = (sheet.value as? LoansSheet.Loan)?.draft?.toPayment() ?: return
        sheet.update { null }
        viewModelScope.launch {
            recurring.save(payment)
            recurring.settleLogged()
        }
    }

    /** Stops the reminders. Payments already logged stay in the history. */
    fun removeLoan() {
        val id = (sheet.value as? LoansSheet.Loan)?.draft?.id ?: return
        sheet.update { null }
        viewModelScope.launch { recurring.remove(id) }
    }

    fun newDebt(kind: DebtKind) = sheet.update { LoansSheet.NewDebt(DebtDraft(kind = kind, accountId = state.value.defaultAccountId, date = LocalDate.now())) }

    fun updateDebt(change: DebtDraft.() -> DebtDraft) = sheet.update { (it as? LoansSheet.NewDebt)?.let { open -> open.copy(draft = open.draft.change()) } ?: it }

    fun selectDebtCategory(category: Category) = updateDebt { copy(categoryId = category.id, subCategoryId = null) }
    fun selectDebtSubCategory(category: Category) = updateDebt { copy(subCategoryId = category.id.takeIf { it != subCategoryId }) }

    fun saveDebt() {
        val draft = (sheet.value as? LoansSheet.NewDebt)?.draft ?: return
        val debt = draft.toDebt() ?: return
        sheet.update { null }
        viewModelScope.launch { debtRepository.add(debt, draft.openingAccount) }
    }

    fun newBill() {
        val s = state.value
        sheet.update { LoansSheet.NewBill(BillDraft(accountId = s.defaultAccountId, personId = s.activePeople.firstOrNull()?.id, date = LocalDate.now())) }
    }

    fun updateBill(change: BillDraft.() -> BillDraft) = sheet.update { (it as? LoansSheet.NewBill)?.let { open -> open.copy(draft = open.draft.change()) } ?: it }

    fun selectBillCategory(category: Category) = updateBill { copy(categoryId = category.id, subCategoryId = null, personId = category.defaultPersonId ?: personId) }

    fun selectBillSubCategory(category: Category) = updateBill {
        val next = category.id.takeIf { it != subCategoryId }
        copy(subCategoryId = next, personId = category.defaultPersonId.takeIf { next != null } ?: personId)
    }

    /** Adds a name someone was owed by before to who's in on this one. */
    fun addToBill(name: String) = updateBill { copy(who = if (who.isBlank()) name else "${who.trim().trimEnd(',')}, $name") }

    fun saveBill() {
        val draft = (sheet.value as? LoansSheet.NewBill)?.draft ?: return
        val bill = draft.toDebt() ?: return
        val accountId = draft.accountId ?: return
        sheet.update { null }
        viewModelScope.launch { debtRepository.addPaidForOthers(bill, accountId, draft.mine ?: Money.ZERO, draft.merchant.takeIf { it.isNotBlank() }, draft.personId) }
    }

    /** What isn't coming back of a bill paid for others becomes the user's own share: spending, on the bill's day. */
    fun markShare() {
        val draft = (sheet.value as? LoansSheet.Pay)?.draft ?: return
        if (!draft.canMarkShare) return
        val bill = draft.debts.single().debt
        val mine = draft.total ?: return
        sheet.update { null }
        viewModelScope.launch { debtRepository.addShare(bill, mine, state.value.personFor(bill)) }
    }

    fun openPay(debts: List<DebtBalance>) {
        val amount = debts.singleOrNull()?.nextPayment?.toFieldText().orEmpty()
        sheet.update { LoansSheet.Pay(PayDraft(debts, amount, state.value.defaultAccountId, LocalDate.now())) }
    }

    fun updatePay(change: PayDraft.() -> PayDraft) = sheet.update { (it as? LoansSheet.Pay)?.let { open -> open.copy(draft = open.draft.change()) } ?: it }

    /** Saves the entries the user asked for, each in its own category. Nothing here runs without them pressing the button. */
    fun confirmPay() {
        val s = state.value
        val draft = (sheet.value as? LoansSheet.Pay)?.draft ?: return
        val accountId = draft.accountId ?: return
        if (!draft.canPay) return
        sheet.update { null }
        viewModelScope.launch {
            debtRepository.pay(draft.debts.map { DebtPayment(it.debt, draft.amountFor(it), s.personFor(it.debt)) }, accountId, draft.date)
        }
    }

    /** For a debt added by mistake, before anything's been paid on it. */
    fun removeDebt(debt: Debt) {
        sheet.update { null }
        viewModelScope.launch { debtRepository.remove(debt) }
    }

    fun openRename(name: String) = sheet.update { LoansSheet.Rename(name, name) }

    fun updateRename(text: String) = sheet.update { (it as? LoansSheet.Rename)?.copy(text = text) ?: it }

    fun saveRename() {
        val rename = (sheet.value as? LoansSheet.Rename)?.takeIf { it.canSave } ?: return
        sheet.update { null }
        viewModelScope.launch { debtRepository.rename(rename.from, rename.text) }
    }
}
