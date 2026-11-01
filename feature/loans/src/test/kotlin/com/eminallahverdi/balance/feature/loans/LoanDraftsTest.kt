package com.eminallahverdi.balance.feature.loans

import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.Debt
import com.eminallahverdi.balance.core.model.DebtBalance
import com.eminallahverdi.balance.core.model.DebtDirection
import com.eminallahverdi.balance.core.model.LoanTerms
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.RecurringFrequency
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.Transaction
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.TransactionKind
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class LoanDraftsTest {
    private val today = LocalDate.of(2026, 11, 1)
    private val loan = LoanDraft(name = " Car loan ", amountText = "250.50", paymentsLeftText = "30", paymentsMadeText = "3", categoryId = 1, accountId = 7, nextDate = today)
    private val owed = Debt(1, "Sam", DebtDirection.I_OWE, Money(10_00), categoryId = 4, date = today)
    private val gift = Debt(2, "sam", DebtDirection.I_OWE, Money(20_00), categoryId = 5, date = today)
    private val lent = Debt(3, "Sam", DebtDirection.OWED_TO_ME, Money(40_00), date = today)

    @Test fun `a loan is a monthly payment with terms`() {
        val payment = loan.toPayment()!!
        assertThat(payment.name).isEqualTo("Car loan")
        assertThat(payment.amount).isEqualTo(Money(250_50))
        assertThat(payment.frequency).isEqualTo(RecurringFrequency.MONTHLY)
        assertThat(payment.loan).isEqualTo(LoanTerms(paymentsMade = 3, paymentsLeft = 30))
    }

    @Test fun `the optional fields can stay empty`() {
        assertThat(loan.copy(paymentsMadeText = "", lastAmountText = "").toPayment()!!.loan).isEqualTo(LoanTerms(0, 30))
    }

    @Test fun `a loan needs its details and sensible numbers`() {
        assertThat(loan.canSave).isTrue()
        assertThat(loan.copy(name = " ").canSave).isFalse()
        assertThat(loan.copy(amountText = "0").canSave).isFalse()
        assertThat(loan.copy(paymentsLeftText = "").canSave).isFalse()
        assertThat(loan.copy(paymentsMadeText = "x").canSave).isFalse()
        assertThat(loan.copy(lastAmountText = "x").canSave).isFalse()
        assertThat(loan.copy(categoryId = null).canSave).isFalse()
    }

    @Test fun `editing a loan keeps everything`() {
        val saved = RecurringPayment(
            id = 9,
            name = "Sofa",
            amount = Money(100_00),
            accountId = 7,
            categoryId = 1,
            personId = 3,
            frequency = RecurringFrequency.MONTHLY,
            nextDate = today,
            anchorDay = 31,
            loan = LoanTerms(2, 5, Money(40_00)),
        )
        assertThat(saved.toLoanDraft(emptyList()).toPayment()).isEqualTo(saved)
    }

    @Test fun `someone paying for you needs a category and moves no money`() {
        val draft = DebtDraft(kind = DebtKind.PAID_FOR_ME, counterparty = "Sam", amountText = "10", categoryId = 1, subCategoryId = 2, accountId = 7, date = today)
        assertThat(draft.canSave).isTrue()
        assertThat(draft.copy(categoryId = null).canSave).isFalse()
        assertThat(draft.toDebt()!!.categoryId).isEqualTo(2)
        assertThat(draft.toDebt()!!.direction).isEqualTo(DebtDirection.I_OWE)
        assertThat(draft.openingAccount).isNull()
    }

    @Test fun `borrowing and lending need an account and no category`() {
        val borrowed = DebtDraft(kind = DebtKind.BORROWED, counterparty = "Sam", amountText = "50", categoryId = 1, accountId = 7, date = today)
        assertThat(borrowed.toDebt()!!.categoryId).isNull()
        assertThat(borrowed.openingAccount).isEqualTo(7)
        assertThat(borrowed.copy(accountId = null).canSave).isFalse()
        assertThat(borrowed.copy(kind = DebtKind.LENT).toDebt()!!.direction).isEqualTo(DebtDirection.OWED_TO_ME)
    }

    @Test fun `debts group by person and direction, ignoring case`() {
        val groups = listOf(DebtBalance(owed, Money.ZERO), DebtBalance(gift, Money(5_00)), DebtBalance(lent, Money.ZERO)).groupedByPerson()
        assertThat(groups).hasSize(2)
        assertThat(groups.first().total).isEqualTo(Money(25_00))
        assertThat(groups.last().direction).isEqualTo(DebtDirection.OWED_TO_ME)
    }

    @Test fun `settled debts leave the list`() {
        assertThat(listOf(DebtBalance(owed, Money(10_00))).groupedByPerson()).isEmpty()
    }

    @Test fun `paying one debt can be part of it, but not more than is left`() {
        val draft = PayDraft(listOf(DebtBalance(owed, Money(4_00))), "6", accountId = 7, date = today)
        assertThat(draft.total).isEqualTo(Money(6_00))
        assertThat(draft.copy(amountText = "2.50").total).isEqualTo(Money(2_50))
        assertThat(draft.copy(amountText = "7").canPay).isFalse()
    }

    @Test fun `paying several settles each in full`() {
        val first = DebtBalance(owed, Money.ZERO)
        val second = DebtBalance(gift, Money(5_00))
        val draft = PayDraft(listOf(first, second), "", accountId = 7, date = today)
        assertThat(draft.total).isEqualTo(Money(25_00))
        assertThat(draft.amountFor(second)).isEqualTo(Money(15_00))
    }

    @Test fun `only credit cards show, with this month's spending on them`() {
        val card = Account(1, "Credit card", AccountType.CREDIT_CARD, icon = "credit_card", color = 0)
        val debit = Account(2, "Salary", AccountType.DEBIT_CARD, icon = "payments", color = 0)
        val entries = listOf(entry(card, TransactionKind.EXPENSE, 30_00), entry(card, TransactionKind.TRANSFER, 500_00), entry(debit, TransactionKind.EXPENSE, 9_00))
        assertThat(cardsInUse(listOf(card, debit), entries)).containsExactly(CardUse(card, Money(30_00)))
    }

    @Test fun `this month's total counts what's due and what's overdue, not what's later`() {
        val soon = payment(today.plusDays(5))
        val overdue = payment(today.minusDays(40))
        val later = payment(today.plusMonths(1))
        val done = payment(today).copy(loan = LoanTerms(3, 0))
        val state = LoansUiState(loans = listOf(soon, overdue, later, done), today = today)
        assertThat(state.dueThisMonth).isEqualTo(Money(200_00))
        assertThat(state.stillToPay).isEqualTo(Money(900_00))
    }

    @Test fun `a sub-category shows under its parent`() {
        val work = Category(1, "Work", CategoryType.EXPENSE, icon = "work", color = 0)
        val lunch = Category(2, "Lunch", CategoryType.EXPENSE, parentId = 1, icon = "work", color = 0)
        val state = LoansUiState(categories = listOf(work, lunch))
        assertThat(state.categoryPath(2)).isEqualTo("Work · Lunch")
        assertThat(state.categoryPath(1)).isEqualTo("Work")
        assertThat(state.categoryPath(null)).isNull()
    }

    private fun payment(on: LocalDate) = RecurringPayment(
        id = 1,
        name = "Loan",
        amount = Money(100_00),
        accountId = 7,
        categoryId = 1,
        personId = null,
        frequency = RecurringFrequency.MONTHLY,
        nextDate = on,
        loan = LoanTerms(0, 3),
    )

    private fun entry(account: Account, kind: TransactionKind, minor: Long) = TransactionDetails(
        transaction = Transaction(id = 0, kind = kind, amount = Money(minor), date = today, accountId = account.id),
        account = account,
        toAccount = null,
        category = null,
        parentCategory = null,
        person = null,
        merchant = null,
    )
}
