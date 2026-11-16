package com.eminallahverdi.balance.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eminallahverdi.balance.core.database.BalanceDatabase
import com.eminallahverdi.balance.core.database.entity.AccountEntity
import com.eminallahverdi.balance.core.database.entity.CategoryEntity
import com.eminallahverdi.balance.core.database.entity.MerchantEntity
import com.eminallahverdi.balance.core.database.entity.RecurringPaymentEntity
import com.eminallahverdi.balance.core.database.entity.TransactionEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** On a real database with its foreign keys on: a parent's delete cascades to its sub-categories, and payments restrict it. */
@RunWith(AndroidJUnit4::class)
class CategoryMoveDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BalanceDatabase
    private lateinit var catalog: CatalogRepository
    private var card = 0L
    private var car = 0L
    private var carFuel = 0L
    private var parking = 0L
    private var transport = 0L
    private var transportFuel = 0L

    @Before fun setUp() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder(context, BalanceDatabase::class.java).build()
        catalog = CatalogRepository(db, db.personDao(), db.categoryDao(), db.accountDao(), db.merchantDao(), db.transactionDao(), db.recurringPaymentDao(), db.debtDao(), db.plannedPaymentDao())
        card = db.accountDao().upsert(AccountEntity(name = "Card", type = "DEBIT_CARD", icon = "credit_card", color = 0))
        car = category("Car", icon = "directions_car", color = 1)
        carFuel = category("Fuel", parent = car, icon = "directions_car", color = 1)
        parking = category("Parking", parent = car, icon = "local_parking", color = 1)
        transport = category("Transport", icon = "commute", color = 2)
        transportFuel = category("fuel", parent = transport, icon = "commute", color = 2)
    }

    @After fun tearDown() = db.close()

    private suspend fun category(name: String, parent: Long? = null, icon: String, color: Long) =
        db.categoryDao().upsert(CategoryEntity(name = name, type = "EXPENSE", parentId = parent, icon = icon, color = color))

    private suspend fun entry(category: Long, minor: Long) =
        db.transactionDao().upsert(TransactionEntity(kind = "EXPENSE", amountMinor = minor, date = 20_000, accountId = card, categoryId = category, createdAt = 0, updatedAt = 0))

    private suspend fun categoryOf(id: Long) = db.transactionDao().observeForAccount(card).first().first { it.id == id }.categoryId

    @Test fun moving_a_category_takes_its_sub_categories_and_merges_a_namesake() = runBlocking<Unit> {
        val own = entry(car, 10_00)
        val fuel = entry(carFuel, 20_00)
        val park = entry(parking, 3_00)
        db.recurringPaymentDao().upsert(RecurringPaymentEntity(name = "Insurance", amountMinor = 100_00, accountId = card, categoryId = car, frequency = "YEARLY", nextDate = 20_100, anchorDay = 1))
        val shop = db.merchantDao().insert(MerchantEntity(name = "Azpetrol", lastCategoryId = carFuel))

        catalog.moveCategory(car, transport)

        val categories = db.categoryDao().getAll()
        assertThat(categories.map { it.id }).containsNoneOf(car, carFuel)
        val movedParking = categories.first { it.id == parking }
        assertThat(movedParking.parentId).isEqualTo(transport)
        // It had its own icon, so it keeps it; its colour was Car's, so it takes Transport's.
        assertThat(movedParking.icon).isEqualTo("local_parking")
        assertThat(movedParking.color).isEqualTo(2)
        assertThat(categoryOf(own)).isEqualTo(transport)
        assertThat(categoryOf(fuel)).isEqualTo(transportFuel)
        assertThat(categoryOf(park)).isEqualTo(parking)
        assertThat(db.recurringPaymentDao().observeAll().first().single().categoryId).isEqualTo(transport)
        assertThat(db.merchantDao().get(shop)?.lastCategoryId).isEqualTo(transportFuel)
        assertThat(db.transactionDao().observeAccountSums().first().sumOf { it.total }).isEqualTo(-33_00)
    }

    @Test fun a_sub_category_moves_into_its_parent() = runBlocking<Unit> {
        val park = entry(parking, 3_00)

        catalog.moveCategory(parking, car)

        assertThat(db.categoryDao().getAll().map { it.id }).doesNotContain(parking)
        assertThat(categoryOf(park)).isEqualTo(car)
        assertThat(catalog.isCategoryUsed(car)).isTrue()
    }
}
