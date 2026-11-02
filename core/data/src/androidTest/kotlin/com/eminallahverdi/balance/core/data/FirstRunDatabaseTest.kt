package com.eminallahverdi.balance.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eminallahverdi.balance.core.database.BalanceDatabase
import com.eminallahverdi.balance.core.database.entity.AccountEntity
import com.eminallahverdi.balance.core.database.entity.CategoryEntity
import com.eminallahverdi.balance.core.database.entity.PersonEntity
import com.eminallahverdi.balance.core.model.parseTransactionCsv
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What decides a fresh install, and what a new user's whole history goes through. Everything named here is invented. */
@RunWith(AndroidJUnit4::class)
class FirstRunDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BalanceDatabase
    private lateinit var catalog: CatalogRepository
    private lateinit var importer: CsvImporter

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, BalanceDatabase::class.java).build()
        catalog = CatalogRepository(db, db.personDao(), db.categoryDao(), db.accountDao(), db.merchantDao(), db.transactionDao(), db.recurringPaymentDao())
        importer = CsvImporter(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun import(csv: String) {
        catalog.applyTemplate(StarterTemplate.household("Alex"))
        importer.write(parseTransactionCsv(csv)!!)
    }

    private suspend fun balances(): Map<String, Long> {
        val names = db.accountDao().getAll().associate { it.id to it.name }
        return db.transactionDao().observeAccountSums().first().groupBy { names.getValue(it.accountId) }.mapValues { (_, sums) -> sums.sumOf { it.total } }
    }

    private suspend fun onlyEntry() = db.transactionDao().observeLatest(1).first().single()
    private suspend fun category(id: Long?) = db.categoryDao().getAll().first { it.id == id }
    private suspend fun person(id: Long?) = db.personDao().getAll().first { it.id == id }.name

    @Test fun an_empty_database_is_a_fresh_install() = runBlocking<Unit> {
        assertThat(db.maintenanceDao().hasData()).isFalse()
    }

    @Test fun any_row_at_all_means_it_has_been_used_even_an_archived_one() = runBlocking<Unit> {
        db.accountDao().upsert(AccountEntity(name = "Closed", type = "CASH", icon = "payments", color = 0, isArchived = true))
        assertThat(db.maintenanceDao().hasData()).isTrue()
    }

    @Test fun a_person_or_a_category_alone_counts_too() = runBlocking<Unit> {
        db.personDao().upsert(PersonEntity(name = "Alex", color = 0, isArchived = true))
        assertThat(db.maintenanceDao().hasData()).isTrue()
        db.close()
        db = Room.inMemoryDatabaseBuilder(context, BalanceDatabase::class.java).build()
        db.categoryDao().upsert(CategoryEntity(name = "Food", type = "EXPENSE", icon = "restaurant", color = 0, isArchived = true))
        assertThat(db.maintenanceDao().hasData()).isTrue()
    }

    @Test fun balances_come_out_right_and_new_accounts_are_added() = runBlocking<Unit> {
        import(
            "Date,Type,Amount,Account,To account,Category,Sub-category,For whom,Merchant,Note\n" +
                "2026-01-01,Income,500.00,CARD,,Paycheck,Salary,Alex,,\n" +
                "2026-01-02,Expense,-20.00,card,,Groceries,,,Bakery,\n" +
                "2026-01-03,Transfer,-100.00,Card,Savings,,,,,\n" +
                "2026-01-04,Adjustment,-5.00,Cash,,,,,,\n",
        )
        assertThat(balances()).containsExactly("Card", 380_00L, "Savings", 100_00L, "Cash", -5_00L)
        assertThat(db.accountDao().getAll().map { it.name }).containsExactly("Card", "Cash", "Savings").inOrder()
    }

    @Test fun a_name_that_is_known_is_reused_whatever_its_case() = runBlocking<Unit> {
        import("Date,Amount,Category,Sub-category\n2026-01-01,-5.00,CAR,fuel\n")
        val fuel = category(onlyEntry().categoryId)
        assertThat(fuel.name).isEqualTo("Fuel")
        assertThat(category(fuel.parentId).name).isEqualTo("Car")
    }

    @Test fun a_sub_category_on_its_own_finds_where_it_lives() = runBlocking<Unit> {
        import("Date,Amount,Category\n2026-01-01,-5.00,Taxi\n")
        assertThat(category(category(onlyEntry().categoryId).parentId).name).isEqualTo("Transport")
    }

    @Test fun a_top_level_name_wins_over_a_sub_category_with_the_same_name() = runBlocking<Unit> {
        import("Date,Amount,Category\n2026-01-01,-5.00,Shopping\n")
        assertThat(category(onlyEntry().categoryId).parentId).isNull()
    }

    @Test fun new_categories_and_sub_categories_are_added_under_the_right_parent() = runBlocking<Unit> {
        import("Date,Amount,Category,Sub-category\n2026-01-01,-5.00,Pets,Vet\n2026-01-02,-6.00,Pets,Food\n")
        val pets = db.categoryDao().getAll().filter { it.name == "Pets" }
        assertThat(pets).hasSize(1)
        assertThat(db.categoryDao().getAll().filter { it.parentId == pets.single().id }.map { it.name }).containsExactly("Vet", "Food")
    }

    @Test fun no_category_means_other_for_spending_and_other_income_for_income() = runBlocking<Unit> {
        import("Date,Amount\n2026-01-01,-5.00\n")
        assertThat(category(onlyEntry().categoryId).name).isEqualTo("Other")
        db.clearAllTables()
        import("Date,Amount\n2026-01-01,5.00\n")
        assertThat(category(onlyEntry().categoryId).name).isEqualTo("Other income")
    }

    @Test fun other_is_added_back_if_it_was_left_out() = runBlocking<Unit> {
        catalog.applyTemplate(StarterTemplate.household("Alex").without(setOf("Other")))
        importer.write(parseTransactionCsv("Date,Amount\n2026-01-01,-5.00\n")!!)
        assertThat(category(onlyEntry().categoryId).name).isEqualTo("Other")
    }

    @Test fun no_person_means_the_category_default_then_the_owner() = runBlocking<Unit> {
        import("Date,Amount,Category\n2026-01-01,-5.00,Groceries\n")
        assertThat(person(onlyEntry().personId)).isEqualTo("Family")
        db.clearAllTables()
        import("Date,Amount,Category\n2026-01-01,-5.00,Kids & Education\n")
        assertThat(person(onlyEntry().personId)).isEqualTo("Alex")
    }

    @Test fun a_new_person_is_added_and_a_known_one_reused() = runBlocking<Unit> {
        import("Date,Amount,For whom\n2026-01-01,-5.00,sam\n2026-01-02,-5.00,ALEX\n")
        assertThat(db.personDao().getAll().map { it.name }).containsExactly("Alex", "Family", "sam")
    }

    @Test fun no_account_means_the_first_one() = runBlocking<Unit> {
        import("Date,Amount\n2026-01-01,-5.00\n")
        assertThat(balances()).containsExactly("Card", -5_00L)
    }

    @Test fun merchants_remember_their_latest_use_whatever_order_the_file_is_in() = runBlocking<Unit> {
        import("Date,Amount,Account,Category,Merchant\n2026-03-01,-5.00,Cash,Health,Corner Shop\n2026-01-01,-5.00,Card,Groceries,corner shop\n")
        val merchant = db.merchantDao().findByName("Corner Shop")!!
        assertThat(merchant.useCount).isEqualTo(2)
        assertThat(category(merchant.lastCategoryId).name).isEqualTo("Health")
        assertThat(db.merchantDao().observeAll().first()).hasSize(1)
    }

    @Test fun the_files_order_survives_within_a_day() = runBlocking<Unit> {
        import("Date,Amount,Note\n2026-01-01,-1.00,first\n2026-01-01,-2.00,second\n")
        assertThat(db.transactionDao().observeLatest(2).first().map { it.note }).containsExactly("second", "first").inOrder()
    }
}
