package com.eminallahverdi.balance.core.model

import com.eminallahverdi.balance.core.model.SpokenLanguage.AZERBAIJANI
import com.eminallahverdi.balance.core.model.SpokenLanguage.ENGLISH
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpokenEntryTest {
    private val e = CategoryType.EXPENSE
    private val names = SpokenNames(
        categories = listOf(
            Category(1, "Groceries", e, icon = "shopping_cart", color = 0, defaultPersonId = 3),
            Category(2, "Cafe & Restaurants", e, icon = "restaurant", color = 0, defaultPersonId = 1),
            Category(3, "Transport", e, icon = "directions_car", color = 0, defaultPersonId = 1),
            Category(31, "Taxi", e, parentId = 3, icon = "local_taxi", color = 0),
            Category(5, "Utilities", e, icon = "bolt", color = 0),
            Category(51, "Internet", e, parentId = 5, icon = "wifi", color = 0),
            Category(9, "Salary", CategoryType.INCOME, icon = "payments", color = 0, defaultPersonId = 1),
        ),
        people = listOf(Person(1, "Alex", 0, sortOrder = 0), Person(2, "Sam", 0, sortOrder = 1), Person(3, "Family", 0, sortOrder = 2)),
        accounts = listOf(
            Account(1, "Card", AccountType.DEBIT_CARD, "credit_card", 0),
            Account(2, "Cash", AccountType.CASH, "payments", 0),
            Account(3, "Kapital Bank", AccountType.DEBIT_CARD, "account_balance", 0),
        ),
        merchants = listOf(
            Merchant(1, "Bravo", lastCategoryId = 1, lastAccountId = 1, lastPersonId = 3, useCount = 30),
            Merchant(2, "Bolt", lastCategoryId = 31, lastAccountId = 3, lastPersonId = 2, useCount = 12),
            Merchant(3, "Starbucks", lastCategoryId = 2, lastAccountId = 1, lastPersonId = 1, useCount = 5),
        ),
    )

    private fun en(text: String) = parseSpokenEntry(text, names, ENGLISH)
    private fun az(text: String) = parseSpokenEntry(text, names, AZERBAIJANI)

    @Test fun `everything said is matched to what the user has`() {
        assertThat(en("15.40 Bravo groceries for family from card")).isEqualTo(
            SpokenEntry(Money(15_40), TransactionKind.EXPENSE, categoryId = 1, personId = 3, accountId = 1, merchant = "Bravo"),
        )
    }

    @Test fun `a known merchant brings its usual category, person and account`() {
        assertThat(en("Bolt 12")).isEqualTo(SpokenEntry(Money(12_00), categoryId = 31, personId = 2, accountId = 3, merchant = "Bolt"))
    }

    @Test fun `manat and qepik, and a sub-category over its parent`() {
        val spoken = en("spent 7 manat 80 on taxi with cash")
        assertThat(spoken.amount).isEqualTo(Money(7_80))
        assertThat(spoken.categoryId).isEqualTo(31)
        assertThat(spoken.accountId).isEqualTo(2)
        assertThat(spoken.merchant).isNull()
    }

    @Test fun `the longest name wins, and income is income`() {
        val spoken = en("salary 2,500 into Kapital Bank")
        assertThat(spoken.kind).isEqualTo(TransactionKind.INCOME)
        assertThat(spoken.amount).isEqualTo(Money(2_500_00))
        assertThat(spoken.categoryId).isEqualTo(9)
        assertThat(spoken.accountId).isEqualTo(3)
    }

    @Test fun `numbers said as words`() {
        assertThat(en("fifteen forty Bravo").amount).isEqualTo(Money(15_40))
        assertThat(en("twenty five dollars at Starbucks").amount).isEqualTo(Money(25_00))
        assertThat(en("one hundred twenty for internet").amount).isEqualTo(Money(120_00))
        assertThat(az("on beş manat qırx qəpik Bravo").amount).isEqualTo(Money(15_40))
        assertThat(az("iki yüz əlli manat").amount).isEqualTo(Money(250_00))
    }

    @Test fun `Azerbaijani endings and words for cash and for whom`() {
        val spoken = az("Bravodan 25 manat 50 qəpik, Sam üçün, nağd")
        assertThat(spoken.amount).isEqualTo(Money(25_50))
        assertThat(spoken.merchant).isEqualTo("Bravo")
        assertThat(spoken.categoryId).isEqualTo(1)
        assertThat(spoken.personId).isEqualTo(2)
        assertThat(spoken.accountId).isEqualTo(2)
    }

    @Test fun `a new shop is named by where the money went, and the day can be said`() {
        val spoken = az("dünən Wolt-dan 18 manat Sam üçün")
        assertThat(spoken.daysAgo).isEqualTo(1)
        assertThat(spoken.merchant).isEqualTo("Wolt")
        assertThat(spoken.categoryId).isEqualTo(2)
        assertThat(spoken.amount).isEqualTo(Money(18_00))
        assertThat(en("9 at Corner Shop yesterday").merchant).isEqualTo("Corner")
        assertThat(en("coffee 4.50 at Starbucks").merchant).isEqualTo("Starbucks")
    }

    @Test fun `one word of a long category name is enough`() {
        assertThat(en("restaurant 32 for me").categoryId).isEqualTo(2)
        assertThat(en("restaurant 32 for me").personId).isEqualTo(1)
    }

    @Test fun `a wrong letter in a long word still matches, and plurals do`() {
        assertThat(en("10 grocerys").categoryId).isEqualTo(1)
        assertThat(en("10 on the cards").accountId).isEqualTo(1)
    }

    @Test fun `what wasn't said is left for quick add`() {
        val spoken = en("something 5")
        assertThat(spoken).isEqualTo(SpokenEntry(Money(5_00)))
        assertThat(en("nothing at all").amount).isNull()
    }

    @Test fun `short words aren't matched by chance`() {
        // Sam in Samsung, Nar in Nargiz: an ending has to be a real one.
        assertThat(en("Samsung charger 30").personId).isNull()
    }
}
