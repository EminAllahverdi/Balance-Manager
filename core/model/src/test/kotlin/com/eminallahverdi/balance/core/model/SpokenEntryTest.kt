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
        assertThat(az("Samsung adapter 30 manat").personId).isNull()
    }

    /** Two vehicles with the same sub-categories, as the owner has them. */
    private val vehicles = SpokenNames(
        categories = listOf(
            Category(10, "Car", e, icon = "directions_car", color = 0, defaultPersonId = 1),
            Category(11, "Fuel", e, parentId = 10, icon = "local_gas_station", color = 0),
            Category(12, "Service", e, parentId = 10, icon = "build", color = 0),
            Category(20, "Motorbike", e, icon = "two_wheeler", color = 0, defaultPersonId = 2),
            Category(21, "Fuel", e, parentId = 20, icon = "local_gas_station", color = 0),
            Category(22, "Service", e, parentId = 20, icon = "build", color = 0),
        ),
        people = names.people,
        accounts = names.accounts,
        merchants = emptyList(),
    )

    @Test fun `a sub-category said with its parent wins over one of the same name elsewhere`() {
        fun en(text: String) = parseSpokenEntry(text, vehicles, ENGLISH)
        assertThat(en("motorbike fuel 20").categoryId).isEqualTo(21)
        assertThat(en("motorbike fuel 20").personId).isEqualTo(2)
        assertThat(en("motorbike service 45").categoryId).isEqualTo(22)
        assertThat(en("20 for fuel for the motorbike").categoryId).isEqualTo(21)
        assertThat(en("car service 60").categoryId).isEqualTo(12)
        assertThat(en("motorbike 20").categoryId).isEqualTo(20)
    }

    @Test fun `a main category said beats a sub-category of another`() {
        val noBikeFuel = vehicles.copy(categories = vehicles.categories.filterNot { it.id == 21L })
        assertThat(parseSpokenEntry("motorbike fuel 20", noBikeFuel, ENGLISH).categoryId).isEqualTo(20)
    }

    @Test fun `of two sub-categories with one name, the one in use wins`() {
        assertThat(parseSpokenEntry("fuel 30", vehicles.copy(categoryUse = mapOf(11L to 2, 21L to 9)), ENGLISH).categoryId).isEqualTo(21)
        assertThat(parseSpokenEntry("fuel 30", vehicles, ENGLISH).categoryId).isEqualTo(11)
    }

    /** Names a user might make up, none of them in the word list, so only the ranking can tell them apart. */
    private val sheds = SpokenNames(
        categories = listOf(
            Category(10, "Garden", e, icon = "yard", color = 0, defaultPersonId = 3),
            Category(11, "Tools", e, parentId = 10, icon = "build", color = 0),
            Category(12, "Seeds", e, parentId = 10, icon = "grass", color = 0),
            Category(20, "Workshop", e, icon = "handyman", color = 0, defaultPersonId = 1),
            Category(21, "Tools", e, parentId = 20, icon = "build", color = 0),
            Category(22, "Paint", e, parentId = 20, icon = "format_paint", color = 0),
            Category(30, "Bağ", e, icon = "yard", color = 0),
            Category(31, "Alətlər", e, parentId = 30, icon = "build", color = 0),
            Category(40, "Emalatxana", e, icon = "handyman", color = 0),
            Category(41, "Alətlər", e, parentId = 40, icon = "build", color = 0),
        ),
        people = names.people,
        accounts = names.accounts,
        merchants = emptyList(),
    )

    @Test fun `any names a user makes up are told apart the same way`() {
        fun en(text: String) = parseSpokenEntry(text, sheds, ENGLISH)
        fun az(text: String) = parseSpokenEntry(text, sheds, AZERBAIJANI)
        assertThat(en("workshop tools 20").categoryId).isEqualTo(21)
        assertThat(en("garden tools 20").categoryId).isEqualTo(11)
        assertThat(en("20 on tools for the workshop").categoryId).isEqualTo(21)
        assertThat(en("workshop 20").categoryId).isEqualTo(20)
        assertThat(parseSpokenEntry("tools 30", sheds.copy(categoryUse = mapOf(21L to 9)), ENGLISH).categoryId).isEqualTo(21)
        assertThat(az("emalatxananın alətlərinə 15 manat").categoryId).isEqualTo(41)
        assertThat(az("bağ alətləri 12 manat").categoryId).isEqualTo(31)
        assertThat(az("bağ alətləri 12 manat").personId).isNull()
    }

    @Test fun `English isn't read with Azerbaijani endings`() {
        assertThat(parseSpokenEntry("self care 20", vehicles, ENGLISH).categoryId).isNull()
    }

    @Test fun `Azerbaijani finds categories named in English`() {
        fun az(text: String) = parseSpokenEntry(text, vehicles, AZERBAIJANI)
        assertThat(az("motosiklet yanacağı 20 manat").categoryId).isEqualTo(21)
        assertThat(az("motosikletə servis 40 manat").categoryId).isEqualTo(22)
        assertThat(az("maşının servisinə 60 manat").categoryId).isEqualTo(12)
        assertThat(parseSpokenEntry("taksiyə 5 manat", names, AZERBAIJANI).categoryId).isEqualTo(31)
    }

    /** Categories named in Azerbaijani, as a new user might add them. */
    private val azNames = SpokenNames(
        categories = listOf(
            Category(1, "Ərzaq", e, icon = "shopping_cart", color = 0),
            Category(2, "Çörək", e, parentId = 1, icon = "bakery_dining", color = 0),
            Category(3, "Motosiklet", e, icon = "two_wheeler", color = 0),
            Category(4, "Yanacaq", e, parentId = 3, icon = "local_gas_station", color = 0),
            Category(5, "Servis", e, parentId = 3, icon = "build", color = 0),
            Category(6, "Kafe və restoran", e, icon = "restaurant", color = 0),
        ),
        people = names.people,
        accounts = names.accounts,
        merchants = emptyList(),
    )

    @Test fun `Azerbaijani names are found with their endings`() {
        fun az(text: String) = parseSpokenEntry(text, azNames, AZERBAIJANI)
        assertThat(az("ərzağa 15 manat").categoryId).isEqualTo(1)
        assertThat(az("çörəyə 2 manat").categoryId).isEqualTo(2)
        assertThat(az("motosikletin yanacağına 20 manat").categoryId).isEqualTo(4)
        assertThat(az("yanacaq 10").categoryId).isEqualTo(4)
        assertThat(az("motosiklet servisi 35 manat").categoryId).isEqualTo(5)
        assertThat(az("kafe-restorana 30 manat").categoryId).isEqualTo(6)
    }

    @Test fun `English finds categories named in Azerbaijani`() {
        assertThat(parseSpokenEntry("groceries 15", azNames, ENGLISH).categoryId).isEqualTo(1)
        assertThat(parseSpokenEntry("motorbike fuel 20", azNames, ENGLISH).categoryId).isEqualTo(4)
    }

    @Test fun `the number said with manat is the amount`() {
        assertThat(az("bir kofe 3 manat").amount).isEqualTo(Money(3_00))
        assertThat(en("2 coffees 7 dollars").amount).isEqualTo(Money(7_00))
        assertThat(az("20 manata aldım").amount).isEqualTo(Money(20_00))
    }

    @Test fun `Azerbaijani endings on words for money, people and cards`() {
        val salary = az("maaşım 1500 manat")
        assertThat(salary.kind).isEqualTo(TransactionKind.INCOME)
        assertThat(salary.categoryId).isEqualTo(9)
        val spoken = az("Samə 10 manat kartımdan")
        assertThat(spoken.personId).isEqualTo(2)
        assertThat(spoken.accountId).isEqualTo(1)
    }
}
