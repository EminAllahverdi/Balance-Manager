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

/** On a real database with its foreign keys on, because these two can't be undone and they're about money. */
@RunWith(AndroidJUnit4::class)
class AccountRemovalDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BalanceDatabase
    private lateinit var catalog: CatalogRepository
    private var old = 0L
    private var card = 0L
    private var cash = 0L
    private var food = 0L

    @Before fun setUp() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder(context, BalanceDatabase::class.java).build()
        catalog = CatalogRepository(db, db.personDao(), db.categoryDao(), db.accountDao(), db.merchantDao(), db.transactionDao(), db.recurringPaymentDao())
        old = account("Old card", archived = true)
        card = account("Card")
        cash = account("Cash")
        food = db.categoryDao().upsert(CategoryEntity(name = "Food", type = "EXPENSE", icon = "restaurant", color = 0))
    }

    @After fun tearDown() = db.close()

    private suspend fun account(name: String, archived: Boolean = false) =
        db.accountDao().upsert(AccountEntity(name = name, type = "DEBIT_CARD", icon = "credit_card", color = 0, isArchived = archived))

    private suspend fun entry(kind: String, minor: Long, account: Long, to: Long? = null) =
        db.transactionDao().upsert(TransactionEntity(kind = kind, amountMinor = minor, date = 20_000, accountId = account, toAccountId = to, createdAt = 0, updatedAt = 0))

    private suspend fun payment(account: Long) = db.recurringPaymentDao().upsert(
        RecurringPaymentEntity(name = "Rent", amountMinor = 100_00, accountId = account, categoryId = food, frequency = "MONTHLY", nextDate = 20_100, anchorDay = 1),
    )

    private suspend fun balances(): Map<Long, Long> =
        db.transactionDao().observeAccountSums().first().groupBy { it.accountId }.mapValues { (_, sums) -> sums.sumOf { it.total } }

    private suspend fun entriesOn(account: Long) = db.transactionDao().observeForAccount(account).first()

    @Test fun moving_brings_every_kind_of_entry_and_the_balance_with_it() = runBlocking<Unit> {
        entry("INCOME", 100_00, old)
        entry("EXPENSE", 30_00, old)
        entry("ADJUSTMENT", -5_00, old)
        entry("TRANSFER", 20_00, old, to = cash)
        entry("INCOME", 10_00, card)
        entry("EXPENSE", 1_00, cash)

        catalog.moveAccountEntries(old, card)

        val after = balances()
        assertThat(after[card]).isEqualTo(10_00 + 100_00 - 30_00 - 5_00 - 20_00)
        assertThat(after[cash]).isEqualTo(20_00 - 1_00)
        assertThat(after[old] ?: 0).isEqualTo(0)
        assertThat(entriesOn(old)).isEmpty()
        assertThat(db.accountDao().usageCount(old)).isEqualTo(0)
    }

    @Test fun moving_keeps_the_total_across_all_accounts() = runBlocking<Unit> {
        entry("INCOME", 500_00, old)
        entry("TRANSFER", 100_00, old, to = card)
        entry("TRANSFER", 40_00, card, to = old)
        entry("TRANSFER", 7_00, cash, to = old)
        entry("EXPENSE", 3_00, card)
        val total = balances().values.sum()

        catalog.moveAccountEntries(old, card)

        assertThat(balances().values.sum()).isEqualTo(total)
    }

    @Test fun moving_removes_transfers_between_the_two_accounts_and_no_others() = runBlocking<Unit> {
        entry("TRANSFER", 100_00, old, to = card)
        entry("TRANSFER", 40_00, card, to = old)
        val other = entry("TRANSFER", 7_00, old, to = cash)

        catalog.moveAccountEntries(old, card)

        assertThat(db.transactionDao().count()).isEqualTo(1)
        val kept = db.transactionDao().get(other)!!
        assertThat(kept.accountId).isEqualTo(card)
        assertThat(kept.toAccountId).isEqualTo(cash)
        assertThat(kept.updatedAt).isGreaterThan(0L)
    }

    @Test fun moving_changes_the_side_a_transfer_arrives_on_too() = runBlocking<Unit> {
        val incoming = entry("TRANSFER", 30_00, cash, to = old)

        catalog.moveAccountEntries(old, card)

        val kept = db.transactionDao().get(incoming)!!
        assertThat(kept.accountId).isEqualTo(cash)
        assertThat(kept.toAccountId).isEqualTo(card)
        assertThat(balances()[card]).isEqualTo(30_00)
    }

    @Test fun recurring_payments_and_what_a_merchant_remembers_follow_a_move() = runBlocking<Unit> {
        val rent = payment(old)
        val shop = db.merchantDao().insert(MerchantEntity(name = "Shop", lastAccountId = old))

        catalog.moveAccountEntries(old, card)

        assertThat(db.recurringPaymentDao().get(rent)!!.accountId).isEqualTo(card)
        assertThat(db.merchantDao().get(shop)!!.lastAccountId).isEqualTo(card)
    }

    @Test fun an_account_can_be_deleted_after_a_move_without_touching_anything_else() = runBlocking<Unit> {
        entry("INCOME", 100_00, old)
        payment(old)
        catalog.moveAccountEntries(old, card)
        val before = balances()

        catalog.deleteAccount(old)

        assertThat(db.accountDao().getAll().map { it.id }).containsExactly(card, cash)
        assertThat(balances()).isEqualTo(before)
        assertThat(db.transactionDao().count()).isEqualTo(1)
        assertThat(db.recurringPaymentDao().observeAll().first()).hasSize(1)
    }

    @Test fun deleting_takes_its_entries_and_puts_other_balances_back() = runBlocking<Unit> {
        entry("EXPENSE", 30_00, old)
        entry("TRANSFER", 100_00, old, to = card)
        entry("TRANSFER", 25_00, cash, to = old)
        entry("INCOME", 5_00, card)
        entry("EXPENSE", 2_00, cash)

        catalog.deleteAccount(old)

        val after = balances()
        assertThat(after[card]).isEqualTo(5_00)
        assertThat(after[cash]).isEqualTo(-2_00)
        assertThat(db.transactionDao().count()).isEqualTo(2)
        assertThat(db.accountDao().getAll().map { it.id }).containsExactly(card, cash)
    }

    @Test fun deleting_takes_recurring_payments_on_it_and_leaves_the_rest() = runBlocking<Unit> {
        payment(old)
        val kept = payment(card)

        catalog.deleteAccount(old)

        assertThat(db.recurringPaymentDao().observeAll().first().map { it.id }).containsExactly(kept)
    }

    @Test fun a_merchant_forgets_a_deleted_account_and_keeps_another() = runBlocking<Unit> {
        val onOld = db.merchantDao().insert(MerchantEntity(name = "Shop", lastAccountId = old))
        val onCard = db.merchantDao().insert(MerchantEntity(name = "Cafe", lastAccountId = card))

        catalog.deleteAccount(old)

        assertThat(db.merchantDao().get(onOld)!!.lastAccountId).isNull()
        assertThat(db.merchantDao().get(onCard)!!.lastAccountId).isEqualTo(card)
    }
}
