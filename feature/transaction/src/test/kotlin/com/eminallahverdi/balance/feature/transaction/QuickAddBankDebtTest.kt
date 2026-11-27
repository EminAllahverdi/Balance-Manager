package com.eminallahverdi.balance.feature.transaction

import com.eminallahverdi.balance.core.data.PendingItem
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.Debt
import com.eminallahverdi.balance.core.model.DebtBalance
import com.eminallahverdi.balance.core.model.DebtDirection
import com.eminallahverdi.balance.core.model.DebtType
import com.eminallahverdi.balance.core.model.EntryGuess
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.PendingEntry
import com.eminallahverdi.balance.core.model.TransactionKind
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

/** A bank payment made into a debt: what the Debt tab offers it, and what it keeps from the bank. */
class QuickAddBankDebtTest {
    private val eatingOut = Category(1, "Eating out", CategoryType.EXPENSE, icon = "restaurant", color = 0)
    private val salary = Category(2, "Salary", CategoryType.INCOME, icon = "payments", color = 0)
    private val card = Account(1, "Card", AccountType.DEBIT_CARD, "payments", 0)

    private fun entry(kind: TransactionKind, merchant: String, amount: Long = 9_000) =
        PendingEntry(id = 7, externalId = "x", source = "Bank", amount = Money(amount), kind = kind, merchant = merchant, postedAt = Instant.parse("2026-11-27T09:20:00Z"))

    private val bill = entry(TransactionKind.EXPENSE, "MANGAL HOUSE")
    private val fromSam = entry(TransactionKind.INCOME, "C2C SAM", amount = 20_000)

    private fun balance(id: Long, who: String, direction: DebtDirection, left: Long) =
        DebtBalance(Debt(id, who, direction, Money(left), date = LocalDate.of(2026, 11, 1)), paid = Money.ZERO)

    private val debts = listOf(
        balance(1, "Kamal", DebtDirection.I_OWE, 45_000),
        balance(2, "Alex", DebtDirection.OWED_TO_ME, 800),
        balance(3, "Sam", DebtDirection.OWED_TO_ME, 20_000),
    )

    private val opened = QuickAddUiState(
        loading = false,
        step = QuickAddStep.ENTRY,
        kind = TransactionKind.EXPENSE,
        amountText = "90.00",
        allCategories = listOf(eatingOut, salary),
        accounts = listOf(card),
        accountId = 1,
        categoryId = 1,
        merchant = "Mangal House",
        pending = bill,
        pendingCategoryId = 1,
        debts = debts,
    )

    @Test fun `money that left can be a bill or a loan made, never money borrowed or paid for me`() {
        assertThat(newDebtsFor(BankFlow.OUT)).containsExactly(DebtType.PAID_FOR_OTHERS, DebtType.LENT)
    }

    @Test fun `money that came in can only be a loan taken`() {
        assertThat(newDebtsFor(BankFlow.IN)).containsExactly(DebtType.BORROWED)
    }

    @Test fun `anything typed in offers all four`() {
        assertThat(newDebtsFor(null)).hasSize(4)
    }

    @Test fun `a cash withdrawal left an account`() {
        assertThat(entry(TransactionKind.TRANSFER, "ATM").flow).isEqualTo(BankFlow.OUT)
    }

    @Test fun `money that left settles only people the user owes`() {
        assertThat(opened.owedLines.map { it.name }).containsExactly("Kamal")
    }

    @Test fun `money that came in settles only people who owe, the one the bank names first`() {
        val lines = opened.copy(pending = fromSam).owedLines
        assertThat(lines.map { it.name }).containsExactly("Sam", "Alex").inOrder()
    }

    @Test fun `a name only counts as a word of its own`() {
        assertThat(named("C2C SAM", "Sam")).isTrue()
        assertThat(named("SAMSUNG STORE", "Sam")).isFalse()
        assertThat(named("Köçürmə: İlkin M.", "ilkin")).isTrue()
    }

    @Test fun `without a bank payment everyone shows`() {
        assertThat(opened.copy(pending = null).owedLines).hasSize(3)
    }

    @Test fun `paying back from the bank takes its amount, up to what's left`() {
        assertThat(bankPayBack(Money(15_000), Money(45_000))).isEqualTo(Money(15_000))
        assertThat(bankPayBack(Money(50_000), Money(45_000))).isEqualTo(Money(45_000))
        assertThat(bankPayBack(null, Money(45_000))).isEqualTo(Money(45_000))
    }

    @Test fun `switching tabs keeps the bank's amount, account, shop and day`() {
        val debt = opened.withKind(TransactionKind.DEBT)
        assertThat(debt.step).isEqualTo(QuickAddStep.DEBT)
        assertThat(debt.amountText).isEqualTo("90.00")
        assertThat(debt.accountId).isEqualTo(1)
        assertThat(debt.merchant).isEqualTo("Mangal House")
        assertThat(debt.date).isEqualTo(opened.date)
        assertThat(debt.categoryId).isNull()
    }

    @Test fun `going back to the bank's own tab brings its guess back`() {
        val back = opened.withKind(TransactionKind.DEBT).withKind(TransactionKind.EXPENSE)
        assertThat(back.categoryId).isEqualTo(1)
        assertThat(back.step).isEqualTo(QuickAddStep.ENTRY)
    }

    @Test fun `another tab than the bank's starts on its grid`() {
        val income = opened.withKind(TransactionKind.INCOME)
        assertThat(income.categoryId).isNull()
        assertThat(income.step).isEqualTo(QuickAddStep.CATEGORY)
        assertThat(income.amountText).isEqualTo("90.00")
    }

    @Test fun `a bill paid by card starts in the category it was guessed as`() {
        val bill = opened.withKind(TransactionKind.DEBT).withDebt(DebtType.PAID_FOR_OTHERS)
        assertThat(bill.categoryId).isEqualTo(1)
        assertThat(bill.step).isEqualTo(QuickAddStep.ENTRY)
    }

    @Test fun `a bill typed in still picks its category first`() {
        val typed = opened.copy(pending = null, pendingCategoryId = null).withKind(TransactionKind.DEBT).withDebt(DebtType.PAID_FOR_OTHERS)
        assertThat(typed.categoryId).isNull()
        assertThat(typed.step).isEqualTo(QuickAddStep.CATEGORY)
    }

    @Test fun `a loan goes straight to its page`() {
        val lent = opened.withKind(TransactionKind.DEBT).withDebt(DebtType.LENT)
        assertThat(lent.step).isEqualTo(QuickAddStep.ENTRY)
        assertThat(lent.categoryId).isNull()
    }

    @Test fun `a bank payment has the tabs on every page`() {
        assertThat(opened.showsKinds).isTrue()
        assertThat(opened.copy(pending = null).showsKinds).isFalse()
    }

    @Test fun `after one from the list, + is a new entry again naming it`() {
        val item = PendingItem(bill, EntryGuess(merchantName = "Mangal House", categoryId = 1, personId = null, accountId = 1, isKnown = true))
        val fresh = opened.copy(openedFromList = true).freshAfter(item)
        assertThat(fresh.pending).isNull()
        assertThat(fresh.step).isEqualTo(QuickAddStep.CATEGORY)
        assertThat(fresh.amountText).isEmpty()
        assertThat(fresh.added).isEqualTo(item)
        assertThat(fresh.saved).isFalse()
    }
}
