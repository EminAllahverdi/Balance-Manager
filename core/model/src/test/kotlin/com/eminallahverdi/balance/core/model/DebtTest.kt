package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class DebtTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val monthEnd = LocalDate.of(2026, 11, 30)
    private val lunch = Debt(1, "Sam", DebtDirection.I_OWE, Money(10_00), categoryId = 7, note = "Friday lunch", date = monday)
    private val borrowed = Debt(2, "Sam", DebtDirection.I_OWE, Money(50_00), date = monday)
    private val lent = Debt(3, "Aysel", DebtDirection.OWED_TO_ME, Money(40_00), date = monday)

    @Test fun `someone paying for you moves none of your money`() {
        assertThat(lunch.opening(accountId = 5)).isNull()
    }

    @Test fun `borrowing adds to the balance without being income`() {
        val entry = borrowed.opening(accountId = 5)!!
        assertThat(entry.kind).isEqualTo(TransactionKind.DEBT)
        assertThat(entry.amount).isEqualTo(Money(50_00))
        assertThat(entry.countsInReports).isFalse()
        assertThat(entry.effectOn(5)).isEqualTo(Money(50_00))
    }

    @Test fun `lending takes from the balance without being spending`() {
        val entry = lent.opening(accountId = 5)!!
        assertThat(entry.kind).isEqualTo(TransactionKind.DEBT)
        assertThat(entry.effectOn(5)).isEqualTo(Money(-40_00))
        assertThat(entry.countsInReports).isFalse()
    }

    @Test fun `paying back a lunch lands as an expense in its category`() {
        val entry = lunch.repayment(Money(10_00), accountId = 5, on = monthEnd, personId = 9)
        assertThat(entry.kind).isEqualTo(TransactionKind.EXPENSE)
        assertThat(entry.categoryId).isEqualTo(7)
        assertThat(entry.personId).isEqualTo(9)
        assertThat(entry.date).isEqualTo(monthEnd)
        assertThat(entry.amount).isEqualTo(Money(10_00))
        assertThat(entry.countsInReports).isTrue()
        assertThat(entry.note).isEqualTo("Friday lunch · Paid back to Sam")
    }

    @Test fun `paying back borrowed money takes from the balance without being spending`() {
        val entry = borrowed.repayment(Money(15_00), accountId = 5, on = monthEnd)
        assertThat(entry.kind).isEqualTo(TransactionKind.DEBT)
        assertThat(entry.effectOn(5)).isEqualTo(Money(-15_00))
        assertThat(entry.countsInReports).isFalse()
    }

    @Test fun `being paid back adds to the balance without being income`() {
        val entry = lent.repayment(Money(40_00), accountId = 5, on = monthEnd)
        assertThat(entry.kind).isEqualTo(TransactionKind.DEBT)
        assertThat(entry.effectOn(5)).isEqualTo(Money(40_00))
        assertThat(entry.countsInReports).isFalse()
    }

    @Test fun `what's left follows the repayments, not the opening`() {
        val entries = listOf(borrowed.opening(5)!!, borrowed.repayment(Money(15_00), 5, monthEnd))
        val balance = DebtBalance(borrowed, borrowed.paidBack(entries))
        assertThat(balance.left).isEqualTo(Money(35_00))
        assertThat(balance.isSettled).isFalse()
    }

    @Test fun `money lent is settled once it's all back`() {
        val entries = listOf(lent.opening(5)!!, lent.repayment(Money(40_00), 5, monthEnd))
        assertThat(DebtBalance(lent, lent.paidBack(entries)).isSettled).isTrue()
    }

    @Test fun `an expense paying back a lunch counts as paid`() {
        val entries = listOf(lunch.repayment(Money(4_00), 5, monthEnd, personId = 9))
        assertThat(DebtBalance(lunch, lunch.paidBack(entries)).left).isEqualTo(Money(6_00))
    }

    @Test fun `another debt's entries don't count`() {
        assertThat(borrowed.paidBack(listOf(lent.repayment(Money(40_00), 5, monthEnd)))).isEqualTo(Money.ZERO)
    }

    @Test fun `each direction of money reads clearly`() {
        assertThat(borrowed.label(borrowed.opening(5)!!)).isEqualTo("Borrowed from Sam")
        assertThat(borrowed.label(borrowed.repayment(Money(15_00), 5, monthEnd))).isEqualTo("Paid back to Sam")
        assertThat(lent.label(lent.opening(5)!!)).isEqualTo("Lent to Aysel")
        assertThat(lent.label(lent.repayment(Money(40_00), 5, monthEnd))).isEqualTo("Paid back by Aysel")
    }
}
