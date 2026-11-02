package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class EntryGuessTest {
    private val groceries = Category(1, "Groceries", CategoryType.EXPENSE, icon = "shopping_cart", color = 0, defaultPersonId = 5)
    private val transport = Category(2, "Transport", CategoryType.EXPENSE, icon = "local_taxi", color = 0, defaultPersonId = 6)
    private val taxi = Category(3, "Taxi", CategoryType.EXPENSE, parentId = 2, icon = "local_taxi", color = 0)
    private val car = Category(4, "Car", CategoryType.EXPENSE, icon = "directions_car", color = 0)
    private val parking = Category(5, "Parking", CategoryType.EXPENSE, parentId = 4, icon = "local_parking", color = 0)
    private val subscriptions = Category(6, "Subscriptions", CategoryType.EXPENSE, icon = "subscriptions", color = 0)
    private val apps = Category(7, "Apps", CategoryType.EXPENSE, parentId = 6, icon = "apps", color = 0)
    private val salary = Category(8, "Salary", CategoryType.INCOME, icon = "payments", color = 0)
    private val categories = listOf(groceries, transport, taxi, car, parking, subscriptions, apps, salary)

    private val day = LocalDate.of(2026, 11, 1)
    private val posted = day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()

    private fun entry(merchant: String, amount: Long = 12_40, title: String = "", kind: TransactionKind = TransactionKind.EXPENSE) =
        PendingEntry(1, "x", "Bank", Money(amount), kind, merchant, posted, title = title)

    private fun guess(
        entry: PendingEntry,
        aliases: Map<String, Long> = emptyMap(),
        merchants: List<Merchant> = emptyList(),
        recurring: List<RecurringPayment> = emptyList(),
    ) = guessFor(entry, aliases, merchants, categories, defaultAccountId = 7, recurring)

    @Test fun `a merchant with nothing to go on has no category`() {
        val result = guess(entry("ZZZ TRADING"))
        assertThat(result.merchantName).isEqualTo("Zzz Trading")
        assertThat(result.categoryId).isNull()
        assertThat(result.accountId).isEqualTo(7)
        assertThat(result.isKnown).isFalse()
        assertThat(result.isSuggested).isFalse()
    }

    @Test fun `a confirmed merchant comes back under the name the user gave it`() {
        val bravo = Merchant(3, "Bravo", lastCategoryId = 1, lastAccountId = 2, lastPersonId = 4)
        val result = guess(entry("BRAVO SUPERMARKET"), mapOf("bravo supermarket" to 3L), listOf(bravo))
        assertThat(result.merchantName).isEqualTo("Bravo")
        assertThat(result.categoryId).isEqualTo(1)
        assertThat(result.personId).isEqualTo(4)
        assertThat(result.accountId).isEqualTo(2)
        assertThat(result.isKnown).isTrue()
        assertThat(result.isSuggested).isFalse()
    }

    @Test fun `a merchant only typed in by hand is suggested, but never counts as known`() {
        val typed = Merchant(3, "Bravo Supermarket", lastCategoryId = 1)
        val result = guess(entry("BRAVO SUPERMARKET"), merchants = listOf(typed))
        assertThat(result.categoryId).isEqualTo(1)
        assertThat(result.personId).isEqualTo(5)
        assertThat(result.isKnown).isFalse()
        assertThat(result.isSuggested).isTrue()
    }

    @Test fun `another branch of a merchant you use is recognised, as a suggestion`() {
        val bravo = Merchant(3, "Bravo", lastCategoryId = 1, useCount = 9)
        val result = guess(entry("BRAVO EXPRESS 14"), merchants = listOf(bravo))
        assertThat(result.merchantName).isEqualTo("Bravo")
        assertThat(result.categoryId).isEqualTo(1)
        assertThat(result.isKnown).isFalse()
    }

    @Test fun `a merchant whose category was archived isn't known any more`() {
        val bravo = Merchant(3, "Zeta", lastCategoryId = 1)
        val archived = listOf(groceries.copy(isArchived = true))
        val result = guessFor(entry("ZETA"), mapOf("zeta" to 3L), listOf(bravo), archived, defaultAccountId = 7)
        assertThat(result.categoryId).isNull()
        assertThat(result.isKnown).isFalse()
    }

    private fun payment(name: String, amount: Long, on: LocalDate = day) = RecurringPayment(
        id = 1,
        name = name,
        amount = Money(amount),
        accountId = 2,
        categoryId = 7,
        personId = 4,
        frequency = RecurringFrequency.MONTHLY,
        nextDate = on,
    )

    @Test fun `a recurring payment is matched by name, whatever the sum came to`() {
        val result = guess(entry("NEW_MOBIL ZETAFLIX", amount = 27_54), recurring = listOf(payment("Zetaflix", 16_31)))
        assertThat(result.merchantName).isEqualTo("Zetaflix")
        assertThat(result.categoryId).isEqualTo(7)
        assertThat(result.personId).isEqualTo(4)
        assertThat(result.accountId).isEqualTo(2)
        assertThat(result.isSuggested).isTrue()
    }

    @Test fun `a loan is matched by its amount near its date, even with no merchant in the text`() {
        val loan = payment("Car loan", 1_000_00)
        val result = guess(entry("", amount = 1_000_00), recurring = listOf(loan))
        assertThat(result.merchantName).isEqualTo("Car loan")
        assertThat(result.categoryId).isEqualTo(7)
        assertThat(result.isKnown).isFalse()
    }

    @Test fun `the same amount far from the due date is a coincidence`() {
        val result = guess(entry("", amount = 1_000_00), recurring = listOf(payment("Car loan", 1_000_00, day.plusDays(20))))
        assertThat(result.categoryId).isNull()
    }

    @Test fun `a finished loan matches nothing`() {
        val done = payment("Car loan", 1_000_00).copy(loan = LoanTerms(paymentsMade = 12, paymentsLeft = 0))
        assertThat(guess(entry("", amount = 1_000_00), recurring = listOf(done)).categoryId).isNull()
    }

    @Test fun `the user's own category names are found in the text, sub-categories first`() {
        assertThat(guess(entry("CITY PARKING 12")).categoryId).isEqualTo(parking.id)
    }

    @Test fun `common words point at the right category, whichever bank sent them`() {
        assertThat(guess(entry("YANGO")).categoryId).isEqualTo(taxi.id)
        assertThat(guess(entry("SPOTIFY P1234")).categoryId).isEqualTo(apps.id)
        assertThat(guess(entry("NEPTUN SUPERMARKET")).categoryId).isEqualTo(groceries.id)
    }

    @Test fun `a word in the title counts too`() {
        val result = guess(entry("", title = "Taksi ödənişi"))
        assertThat(result.categoryId).isEqualTo(taxi.id)
        assertThat(result.isSuggested).isTrue()
    }

    @Test fun `the person comes from the parent when a sub-category has none`() {
        assertThat(guess(entry("YANGO")).personId).isEqualTo(6)
    }

    @Test fun `short hints only match whole words`() {
        assertThat(guess(entry("NARGIZ GOZELLIK")).categoryId).isNull()
    }

    @Test fun `suggestions never make a merchant known, so auto-confirm leaves them alone`() {
        assertThat(guess(entry("YANGO")).isKnown).isFalse()
    }

    @Test fun `money coming in isn't given a spending category`() {
        assertThat(guess(entry("YANGO", kind = TransactionKind.INCOME)).categoryId).isNull()
    }

    @Test fun `merchant names read as words`() {
        assertThat(prettyMerchant("  BRAVO   SUPERMARKET ")).isEqualTo("Bravo Supermarket")
    }

    private val debit = Account(1, "Main", AccountType.DEBIT_CARD, "payments", 0, cardEnding = "1111")
    private val credit = Account(2, "Credit", AccountType.CREDIT_CARD, "credit_card", 0, cardEnding = "2222")
    private val wallet = Account(3, "Wallet", AccountType.CASH, "account_balance_wallet", 0)
    private val accounts = listOf(debit, credit, wallet)

    private fun withCard(card: String, merchant: String = "SOME SHOP", kind: TransactionKind = TransactionKind.EXPENSE) =
        entry(merchant, kind = kind).copy(cardEnding = card)

    private fun guessWithAccounts(entry: PendingEntry, accounts: List<Account> = this.accounts, merchants: List<Merchant> = emptyList(), aliases: Map<String, Long> = emptyMap()) =
        guessFor(entry, aliases, merchants, categories, defaultAccountId = 1, accounts = accounts)

    @Test fun `the card in the notification decides the account`() {
        assertThat(guessWithAccounts(withCard("2222")).accountId).isEqualTo(credit.id)
    }

    @Test fun `the card beats the account a merchant was last paid from`() {
        val bravo = Merchant(3, "Bravo", lastCategoryId = 1, lastAccountId = debit.id)
        assertThat(guessWithAccounts(withCard("2222", "BRAVO"), merchants = listOf(bravo)).accountId).isEqualTo(credit.id)
    }

    @Test fun `a card no account has saved falls back as before`() {
        val bravo = Merchant(3, "Bravo", lastCategoryId = 1, lastAccountId = credit.id)
        assertThat(guessWithAccounts(withCard("9999", "BRAVO"), merchants = listOf(bravo)).accountId).isEqualTo(credit.id)
        assertThat(guessWithAccounts(withCard("9999")).accountId).isEqualTo(1)
    }

    @Test fun `an archived account's card is ignored`() {
        val closed = listOf(credit.copy(isArchived = true), debit, wallet)
        assertThat(guessWithAccounts(withCard("2222"), closed).accountId).isEqualTo(1)
    }

    @Test fun `an entry with no card never matches an account with none saved`() {
        val none = listOf(debit.copy(cardEnding = ""), credit.copy(id = 9, cardEnding = ""))
        assertThat(guessFor(entry("SOME SHOP"), emptyMap(), emptyList(), categories, defaultAccountId = 7, accounts = none).accountId).isEqualTo(7)
    }

    @Test fun `cash taken out goes from the card to the cash account`() {
        val result = guessWithAccounts(withCard("2222", "SOME MARKET 8 MKR", TransactionKind.TRANSFER))
        assertThat(result.accountId).isEqualTo(credit.id)
        assertThat(result.toAccountId).isEqualTo(wallet.id)
    }

    @Test fun `cash taken out at a shop you know is never spending there`() {
        val market = Merchant(3, "Some Market", lastCategoryId = 1, lastAccountId = debit.id, lastPersonId = 4)
        val result = guessWithAccounts(withCard("2222", "SOME MARKET", TransactionKind.TRANSFER), merchants = listOf(market), aliases = mapOf("some market" to 3L))
        assertThat(result.categoryId).isNull()
        assertThat(result.personId).isNull()
        assertThat(result.merchantName).isEmpty()
        assertThat(result.accountId).isEqualTo(credit.id)
        assertThat(result.isKnown).isFalse()
        assertThat(result.isSuggested).isFalse()
    }

    @Test fun `with no cash account a withdrawal has nowhere to go yet`() {
        assertThat(guessWithAccounts(withCard("1111", kind = TransactionKind.TRANSFER), listOf(debit, credit)).toAccountId).isNull()
        val archived = listOf(debit, wallet.copy(isArchived = true))
        assertThat(guessWithAccounts(withCard("1111", kind = TransactionKind.TRANSFER), archived).toAccountId).isNull()
    }
}
