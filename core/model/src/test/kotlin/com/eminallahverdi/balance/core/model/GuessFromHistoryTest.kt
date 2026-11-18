package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

class GuessFromHistoryTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val card = Account(1, "Card", AccountType.DEBIT_CARD, icon = "credit_card", color = 0)
    private val cash = Account(2, "Cash", AccountType.CASH, icon = "payments", color = 0)
    private val groceries = Category(10, "Groceries", CategoryType.EXPENSE, icon = "shopping_cart", color = 0)
    private val breakfast = Category(11, "Breakfast", CategoryType.EXPENSE, icon = "local_cafe", color = 0)
    private val household = Category(12, "Household", CategoryType.EXPENSE, icon = "home", color = 0)
    private val categories = listOf(groceries, breakfast, household)
    private var nextId = 1L

    /** 2026-11-18 is a Wednesday. */
    private fun at(day: Int, hour: Int = 12, minute: Int = 0): Instant = LocalDate.of(2026, 11, day).atTime(hour, minute).toInstant(ZoneOffset.UTC)

    private fun spent(
        minor: Long,
        category: Category,
        shop: String?,
        date: LocalDate = LocalDate.of(2026, 11, 1),
        paidAt: Instant? = null,
        person: Long? = 7,
        account: Account = card,
    ) = TransactionDetails(
        transaction = Transaction(nextId++, TransactionKind.EXPENSE, Money(minor), date, accountId = account.id, categoryId = category.id, personId = person, paidAt = paidAt),
        account = account,
        toAccount = null,
        category = category,
        parentCategory = null,
        person = null,
        merchant = shop?.let { Merchant(nextId, it) },
    )

    private fun pending(merchant: String, minor: Long, postedAt: Instant = at(18)) =
        PendingEntry(1, "x", "Bank", Money(minor), TransactionKind.EXPENSE, merchant, postedAt, status = PendingStatus.WAITING)

    @Test fun `a shop's names compare without numbers, city and case, and a longer name of it counts`() {
        assertThat(sameShop("BRAVO 24 SUMQAYIT", "Bravo")).isTrue()
        assertThat(sameShop("ARAZ MMC", "Araz")).isTrue()
        assertThat(sameShop("Bolt Food", "Bolt")).isTrue()
        assertThat(sameShop("Oba", "Oba Market")).isFalse()
        assertThat(sameShop("Wolt", "Bolt")).isFalse()
    }

    @Test fun `the usual filing is what most recent entries at the shop share, not the last one`() {
        val history = listOf(
            spent(30_00, groceries, "Bravo", LocalDate.of(2026, 11, 1)),
            spent(25_00, groceries, "Bravo", LocalDate.of(2026, 11, 5)),
            spent(12_00, household, "Bravo", LocalDate.of(2026, 11, 9), account = cash),
        )
        val usual = usualFiling("BRAVO 24", history)!!
        assertThat(usual.categoryId).isEqualTo(groceries.id)
        assertThat(usual.times).isEqualTo(2)
        assertThat(usual.accountId).isEqualTo(card.id)
        assertThat(usual.shop).isEqualTo("Bravo")
    }

    @Test fun `a shop too split to call has no usual`() {
        val history = listOf(spent(1_00, groceries, "Kiosk"), spent(1_00, household, "Kiosk"), spent(1_00, breakfast, "Kiosk"))
        assertThat(usualFiling("Kiosk", history)).isNull()
    }

    @Test fun `entries under the exact name count before a longer name of it`() {
        val history = listOf(spent(5_00, breakfast, "Bolt Food"), spent(5_00, breakfast, "Bolt Food"), spent(4_00, household, "Bolt"))
        assertThat(usualFiling("Bolt", history)!!.categoryId).isEqualTo(household.id)
        assertThat(usualFiling("BOLT FOOD", history)!!.categoryId).isEqualTo(breakfast.id)
    }

    @Test fun `payments of about the same amount at about the same time on weekdays decide an unknown shop`() {
        val history = listOf(
            spent(4_50, breakfast, "Cafe One", paidAt = at(16, 9, 10)),
            spent(4_70, breakfast, "Cafe Two", paidAt = at(17, 8, 40)),
            spent(4_60, groceries, "Shop", paidAt = at(14, 19, 0)),
        )
        val alike = alikeFiling(Money(4_55), at(18, 9, 30), history, zone)!!
        assertThat(alike.categoryId).isEqualTo(breakfast.id)
        assertThat(alike.reason).isEqualTo(GuessReason.LikeAmount(Money(4_55), 9 * 60 + 30))
        // The same payment on a Saturday has no weekend breakfasts like it, and the amount alone is too mixed.
        assertThat(alikeFiling(Money(4_55), at(21, 9, 30), history, zone)).isNull()
    }

    @Test fun `without a time the amount alone has to be clearer`() {
        val history = List(3) { spent(2_00, breakfast, "Bakery $it") } + spent(2_00, groceries, "Shop")
        assertThat(alikeFiling(Money(2_00), null, history, zone)!!.reason.minuteOfDay).isNull()
        assertThat(alikeFiling(Money(2_00), null, history.take(3).take(2), zone)).isNull()
    }

    @Test fun `guessing takes the usual over the last, and says why`() {
        val history = listOf(spent(30_00, groceries, "Bravo"), spent(25_00, groceries, "Bravo"), spent(12_00, household, "Bravo"))
        val bravo = Merchant(5, "Bravo", lastCategoryId = household.id, lastAccountId = cash.id)
        val guess = guessFor(pending("BRAVO SUPERMARKET", 20_00), emptyMap(), listOf(bravo), categories, defaultAccountId = card.id, accounts = listOf(card, cash), history = history, zone = zone)
        assertThat(guess.categoryId).isEqualTo(groceries.id)
        assertThat(guess.reason).isEqualTo(GuessReason.UsualAt("Bravo", 2))
        assertThat(guess.isSuggested).isTrue()
    }

    @Test fun `an unknown shop with no telling words is guessed from payments like it`() {
        val history = listOf(spent(4_50, breakfast, "Cafe One", paidAt = at(16, 9, 0)), spent(4_50, breakfast, "Cafe Two", paidAt = at(17, 9, 15)))
        val guess = guessFor(pending("QWZX", 4_50, at(18, 9, 5)), emptyMap(), emptyList(), categories, defaultAccountId = card.id, history = history, zone = zone)
        assertThat(guess.categoryId).isEqualTo(breakfast.id)
        assertThat(guess.personId).isEqualTo(7)
        assertThat(guess.reason).isInstanceOf(GuessReason.LikeAmount::class.java)
    }

    @Test fun `a telling word still beats payments like it`() {
        val history = List(3) { spent(4_50, breakfast, "Cafe $it", paidAt = at(16, 9, 0)) }
        val guess = guessFor(pending("NEW SUPERMARKET", 4_50, at(18, 9, 0)), emptyMap(), emptyList(), categories, defaultAccountId = card.id, history = history, zone = zone)
        assertThat(guess.categoryId).isEqualTo(groceries.id)
        assertThat(guess.reason).isEqualTo(GuessReason.Words)
    }

    @Test fun `a shop paid about the same every month is offered as recurring, until it is one or said no to`() {
        val history = listOf(
            spent(15_99, household, "Netflix", LocalDate.of(2026, 8, 3)),
            spent(15_99, household, "NETFLIX.COM", LocalDate.of(2026, 9, 3)),
            spent(15_99, household, "Netflix", LocalDate.of(2026, 10, 2)),
            spent(16_49, household, "Netflix", LocalDate.of(2026, 11, 3)),
            spent(30_00, groceries, "Bravo", LocalDate.of(2026, 11, 4)),
        )
        val today = LocalDate.of(2026, 11, 18)
        val repeats = repeatingPayments(history, emptyList(), today)
        assertThat(repeats).hasSize(1)
        val netflix = repeats.single()
        assertThat(netflix.frequency).isEqualTo(RecurringFrequency.MONTHLY)
        assertThat(netflix.times).isEqualTo(4)
        assertThat(netflix.amount).isEqualTo(Money(16_49))
        assertThat(netflix.nextDate).isEqualTo(LocalDate.of(2026, 12, 3))
        val existing = RecurringPayment(1, "Netflix", Money(16_49), card.id, household.id, null, RecurringFrequency.MONTHLY, LocalDate.of(2026, 12, 3))
        assertThat(repeatingPayments(history, listOf(existing), today)).isEmpty()
        assertThat(repeatingPayments(history, emptyList(), today, dismissed = setOf(netflix.key))).isEmpty()
    }

    @Test fun `a repeat that stopped isn't offered`() {
        val history = (0..3).map { spent(9_00, household, "Gym", LocalDate.of(2026, 3, 1).plusMonths(it.toLong())) }
        assertThat(repeatingPayments(history, emptyList(), LocalDate.of(2026, 11, 18))).isEmpty()
    }
}
