package com.eminallahverdi.balance.core.model

import com.eminallahverdi.balance.core.model.SpokenLanguage.AZERBAIJANI
import com.eminallahverdi.balance.core.model.SpokenLanguage.ENGLISH
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Debts said aloud. Every name here is invented. */
class SpokenDebtTest {
    private val e = CategoryType.EXPENSE
    private val names = SpokenNames(
        categories = listOf(Category(2, "Cafe & Restaurants", e, icon = "restaurant", color = 0), Category(21, "Lunch", e, parentId = 2, icon = "lunch_dining", color = 0)),
        people = listOf(Person(1, "Me", 0)),
        accounts = listOf(Account(1, "Card", AccountType.DEBIT_CARD, "credit_card", 0), Account(2, "Cash", AccountType.CASH, "payments", 0)),
        merchants = emptyList(),
        debtNames = listOf("Kamal", "Nina"),
    )

    private fun en(text: String) = parseSpokenEntry(text, names, ENGLISH)
    private fun az(text: String) = parseSpokenEntry(text, names, AZERBAIJANI)

    @Test fun `lending names the person and the account it left`() {
        val said = en("lent Kamal 20 manat from cash")
        assertThat(said.kind).isEqualTo(TransactionKind.DEBT)
        assertThat(said.debt).isEqualTo(DebtType.LENT)
        assertThat(said.counterparty).isEqualTo("Kamal")
        assertThat(said.amount).isEqualTo(Money(20_00))
        assertThat(said.accountId).isEqualTo(2)
    }

    @Test fun `someone new is the word where English puts them`() {
        assertThat(en("I lent 15 to Tom").counterparty).isEqualTo("Tom")
        assertThat(en("borrowed 50 from Tom").let { it.debt to it.counterparty }).isEqualTo(DebtType.BORROWED to "Tom")
        assertThat(en("Tom paid me back 10").let { it.debt to it.counterparty }).isEqualTo(DebtType.PAID_BACK to "Tom")
        assertThat(en("I paid Tom back 10").counterparty).isEqualTo("Tom")
        assertThat(en("Tom paid for me 12 manat").let { it.debt to it.counterparty }).isEqualTo(DebtType.PAID_FOR_ME to "Tom")
    }

    @Test fun `a bill paid for several keeps every name, and its category`() {
        val said = en("paid 30 for Tom and Lisa at lunch")
        assertThat(said.debt).isEqualTo(DebtType.PAID_FOR_OTHERS)
        assertThat(said.counterparty).isEqualTo("Tom and Lisa")
        assertThat(said.categoryId).isEqualTo(21)
    }

    @Test fun `Azerbaijani endings come off the name`() {
        assertThat(az("Kamala 20 manat borc verdim").let { it.debt to it.counterparty }).isEqualTo(DebtType.LENT to "Kamal")
        assertThat(az("Tomə 15 manat borc verdim").counterparty).isEqualTo("Tom")
        assertThat(az("Ninaya 15 manat borc verdim").counterparty).isEqualTo("Nina")
        assertThat(az("Tomdan 50 manat borc aldım").let { it.debt to it.counterparty }).isEqualTo(DebtType.BORROWED to "Tom")
        assertThat(az("Tom 10 manat qaytardı").let { it.debt to it.counterparty }).isEqualTo(DebtType.PAID_BACK to "Tom")
        assertThat(az("Tom mənim üçün 12 manat ödədi").let { it.debt to it.counterparty }).isEqualTo(DebtType.PAID_FOR_ME to "Tom")
        assertThat(az("Tom və Lisa üçün 30 manat ödədim").let { it.debt to it.counterparty }).isEqualTo(DebtType.PAID_FOR_OTHERS to "Tom and Lisa")
    }

    @Test fun `an ordinary expense isn't a debt`() {
        val said = en("12 manat lunch from card")
        assertThat(said.debt).isNull()
        assertThat(said.kind).isEqualTo(TransactionKind.EXPENSE)
    }
}
