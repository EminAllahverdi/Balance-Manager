package com.eminallahverdi.balance.feature.loans

import com.eminallahverdi.balance.core.model.Debt
import com.eminallahverdi.balance.core.model.DebtBalance
import com.eminallahverdi.balance.core.model.DebtDirection
import com.eminallahverdi.balance.core.model.Money
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/** Lunch for four at an invented shop, paid on one card. */
class BillDraftTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val lunch = BillDraft(totalText = "20", who = "Sam, Alex and Kamal", merchant = " Some Doner ", categoryId = 1, subCategoryId = 11, accountId = 7, date = today)

    @Test fun `a bill is owed back by whoever's named, for the whole of it until a share is set`() {
        val bill = lunch.toDebt()!!
        assertThat(bill.counterparty).isEqualTo("Sam, Alex and Kamal")
        assertThat(bill.direction).isEqualTo(DebtDirection.OWED_TO_ME)
        assertThat(bill.amount).isEqualTo(Money(20_00))
        assertThat(bill.categoryId).isEqualTo(11)
        assertThat(bill.isPaidForOthers).isTrue()
        assertThat(lunch.owed).isEqualTo(Money(20_00))
        assertThat(lunch.mine).isEqualTo(Money.ZERO)
    }

    @Test fun `with no note, the shop says what it was`() {
        assertThat(lunch.toDebt()!!.note).isEqualTo("Some Doner")
        assertThat(lunch.copy(note = "Friday lunch").toDebt()!!.note).isEqualTo("Friday lunch")
    }

    @Test fun `an even split counts the user too`() {
        assertThat(lunch.evenSplit).isEqualTo(Money(5_00))
        assertThat(lunch.copy(mineText = "5").owed).isEqualTo(Money(15_00))
        assertThat(lunch.copy(who = "").evenSplit).isNull()
    }

    @Test fun `a bill needs someone to owe it, a category and an account, and a share smaller than the bill`() {
        assertThat(lunch.canSave).isTrue()
        assertThat(lunch.copy(who = " ").canSave).isFalse()
        assertThat(lunch.copy(categoryId = null, subCategoryId = null).canSave).isFalse()
        assertThat(lunch.copy(accountId = null).canSave).isFalse()
        assertThat(lunch.copy(mineText = "20").canSave).isFalse()
        assertThat(lunch.copy(mineText = "abc").canSave).isFalse()
        assertThat(lunch.copy(totalText = "").canSave).isFalse()
    }

    @Test fun `what isn't paid back of a bill can be marked as the user's share, and only of a bill`() {
        val bill = lunch.toDebt()!!.copy(id = 4)
        val open = DebtBalance(bill, paid = Money(12_00))
        val pay = PayDraft(listOf(open), amountText = open.left.toFieldText(), accountId = 7, date = today)
        assertThat(open.left).isEqualTo(Money(8_00))
        assertThat(pay.canMarkShare).isTrue()
        assertThat(pay.total).isEqualTo(Money(8_00))
        val lent = DebtBalance(Debt(5, "Sam", DebtDirection.OWED_TO_ME, Money(40_00), date = today), Money.ZERO)
        assertThat(PayDraft(listOf(lent), "10", 7, today).canMarkShare).isFalse()
        assertThat(PayDraft(listOf(open, lent), "", 7, today).canMarkShare).isFalse()
    }
}
