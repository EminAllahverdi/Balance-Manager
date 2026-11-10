package com.eminallahverdi.balance.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eminallahverdi.balance.core.database.BalanceDatabase
import com.eminallahverdi.balance.core.database.entity.AccountEntity
import com.eminallahverdi.balance.core.database.entity.DebtEntity
import com.eminallahverdi.balance.core.database.entity.TransactionEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** On a real database, because renaming rewrites notes with SQL, and those notes are the user's history. Invented names only. */
@RunWith(AndroidJUnit4::class)
class DebtRenameDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BalanceDatabase
    private lateinit var debts: DebtRepository
    private var card = 0L

    @Before fun setUp() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder(context, BalanceDatabase::class.java).build()
        val catalog = CatalogRepository(db, db.personDao(), db.categoryDao(), db.accountDao(), db.merchantDao(), db.transactionDao(), db.recurringPaymentDao())
        val transactions = TransactionRepository(db.transactionDao(), db.merchantDao(), db.debtDao(), catalog)
        debts = DebtRepository(db, db.debtDao(), db.transactionDao(), transactions)
        card = db.accountDao().upsert(AccountEntity(name = "Card", type = "DEBIT_CARD", icon = "credit_card", color = 0))
    }

    @After fun tearDown() = db.close()

    private suspend fun debt(name: String, direction: String = "I_OWE") =
        db.debtDao().upsert(DebtEntity(counterparty = name, direction = direction, amountMinor = 10_00, date = 20_000))

    private suspend fun payback(debtId: Long, note: String) = db.transactionDao().upsert(
        TransactionEntity(kind = "EXPENSE", amountMinor = 10_00, date = 20_001, accountId = card, debtId = debtId, note = note, createdAt = 0, updatedAt = 0),
    )

    @Test fun every_debt_under_the_name_is_renamed_whatever_its_case_and_no_one_else() = runBlocking<Unit> {
        val lunch = debt("Sam")
        val loan = debt("sam", direction = "OWED_TO_ME")
        val other = debt("Alex")

        debts.rename("Sam", "Samuel")

        val names = db.debtDao().all().associate { it.id to it.counterparty }
        assertThat(names[lunch]).isEqualTo("Samuel")
        assertThat(names[loan]).isEqualTo("Samuel")
        assertThat(names[other]).isEqualTo("Alex")
    }

    @Test fun the_notes_their_paybacks_wrote_take_the_new_name_and_keep_the_rest() = runBlocking<Unit> {
        val lunch = debt("Sam")
        val paid = payback(lunch, "Friday lunch · Paid back to Sam")
        val untouched = payback(debt("Alex"), "Paid back to Alex")

        debts.rename("Sam", "Samuel")

        val notes = db.transactionDao().forDebt(lunch).associate { it.id to it.note }
        assertThat(notes[paid]).isEqualTo("Friday lunch · Paid back to Samuel")
        assertThat(db.transactionDao().forDebt(db.debtDao().all().first { it.counterparty == "Alex" }.id).single { it.id == untouched }.note)
            .isEqualTo("Paid back to Alex")
    }

    @Test fun an_empty_name_changes_nothing() = runBlocking<Unit> {
        val lunch = debt("Sam")
        debts.rename("Sam", "   ")
        assertThat(db.debtDao().all().single { it.id == lunch }.counterparty).isEqualTo("Sam")
    }
}
