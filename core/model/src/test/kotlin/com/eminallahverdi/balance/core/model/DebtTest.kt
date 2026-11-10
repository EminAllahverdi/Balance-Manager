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

    /** Lunch for four, ₼20 on the card. */
    private val table = Debt(4, "Sam, Alex and Kamal", DebtDirection.OWED_TO_ME, Money(20_00), categoryId = 7, note = "Bravo", date = monday)

    @Test fun `a bill paid for others takes what they owe from the account, and your share is spending`() {
        val opening = table.opening(accountId = 5, mine = Money(5_00))!!
        assertThat(opening.kind).isEqualTo(TransactionKind.DEBT)
        assertThat(opening.effectOn(5)).isEqualTo(Money(-15_00))
        assertThat(opening.countsInReports).isFalse()
        val share = table.share(Money(5_00), accountId = 5, personId = 9)
        assertThat(share.kind).isEqualTo(TransactionKind.EXPENSE)
        assertThat(share.categoryId).isEqualTo(7)
        assertThat(share.personId).isEqualTo(9)
        assertThat(share.date).isEqualTo(monday)
        assertThat(share.countsInReports).isTrue()
        // The card paid ₼20 in all.
        assertThat(opening.effectOn(5) + share.effectOn(5)).isEqualTo(Money(-20_00))
    }

    @Test fun `with no share known yet, the whole bill is owed`() {
        assertThat(table.opening(accountId = 5)!!.amount).isEqualTo(Money(-20_00))
        assertThat(table.opening(accountId = 5, mine = Money(20_00))).isNull()
    }

    @Test fun `money coming back from the others isn't income, and what's left is the bill less your share and theirs`() {
        val back = table.repayment(Money(4_00), accountId = 6, on = monthEnd)
        assertThat(back.kind).isEqualTo(TransactionKind.DEBT)
        assertThat(back.effectOn(6)).isEqualTo(Money(4_00))
        val entries = listOf(table.opening(5)!!, back, table.repayment(Money(4_00), 5, monthEnd), table.repayment(Money(4_00), 6, monthEnd))
        assertThat(table.paidBack(entries)).isEqualTo(Money(12_00))
        assertThat(table.mine(entries)).isEqualTo(Money.ZERO)
        assertThat(DebtBalance(table, table.paidBack(entries), table.mine(entries)).left).isEqualTo(Money(8_00))
        // The ₼8 left is marked as the user's own.
        val settled = entries + table.share(Money(8_00), 5, personId = 9)
        assertThat(table.mine(settled)).isEqualTo(Money(8_00))
        assertThat(table.paidBack(settled)).isEqualTo(Money(12_00))
        assertThat(DebtBalance(table, table.paidBack(settled), table.mine(settled)).isSettled).isTrue()
    }

    @Test fun `a bill paid for others moves money, a share paid for you doesn't yet`() {
        assertThat(table.movesMoney).isTrue()
        assertThat(lent.movesMoney).isTrue()
        assertThat(lunch.movesMoney).isFalse()
        assertThat(table.isPaidForOthers).isTrue()
        assertThat(lunch.isPaidForOthers).isFalse()
    }

    @Test fun `a bill paid for others reads clearly`() {
        assertThat(table.role(table.opening(5)!!)).isEqualTo(DebtEntryRole.PAID_FOR)
        assertThat(table.role(table.share(Money(5_00), 5, 9))).isEqualTo(DebtEntryRole.YOUR_SHARE)
        assertThat(table.role(table.repayment(Money(4_00), 6, monthEnd))).isEqualTo(DebtEntryRole.PAID_BACK_BY)
    }

    @Test fun `an even split keeps the odd qepik with you`() {
        assertThat(namesIn("Sam, Alex and Kamal")).containsExactly("Sam", "Alex", "Kamal").inOrder()
        assertThat(namesIn("Sam və Aysel")).containsExactly("Sam", "Aysel").inOrder()
        assertThat(namesIn("  ")).isEmpty()
        assertThat(evenShare(Money(20_00), others = 3)).isEqualTo(Money(5_00))
        // ₼10 three ways: the others owe ₼3.33 each, and the user's share is ₼3.34.
        assertThat(evenShare(Money(10_00), others = 2)).isEqualTo(Money(3_34))
        assertThat(evenShare(Money(10_00), others = 0)).isEqualTo(Money(10_00))
    }

    @Test fun `each direction of money reads clearly`() {
        assertThat(borrowed.role(borrowed.opening(5)!!)).isEqualTo(DebtEntryRole.BORROWED_FROM)
        assertThat(borrowed.role(borrowed.repayment(Money(15_00), 5, monthEnd))).isEqualTo(DebtEntryRole.PAID_BACK_TO)
        assertThat(lent.role(lent.opening(5)!!)).isEqualTo(DebtEntryRole.LENT_TO)
        assertThat(lent.role(lent.repayment(Money(40_00), 5, monthEnd))).isEqualTo(DebtEntryRole.PAID_BACK_BY)
    }
}
