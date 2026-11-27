package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Test

/**
 * The accountant's snapshot from made-up data, compared with a saved copy of it. A change to the snapshot, or to anything
 * whose numbers it shows, fails here until the saved copy is looked at and replaced on purpose: run the test, read
 * `build/accountant-snapshot.html`, and copy it over `src/test/resources/accountant-snapshot.html` if it's right.
 */
class AccountantSnapshotTest {
    private val today = LocalDate.of(2026, 1, 20)

    private val groceries = Category(1, "Groceries", CategoryType.EXPENSE, icon = "shopping_cart", color = 0, sortOrder = 0)
    private val car = Category(2, "Car", CategoryType.EXPENSE, icon = "directions_car", color = 0, sortOrder = 1)
    private val fuel = Category(3, "Fuel", CategoryType.EXPENSE, parentId = 2, icon = "local_gas_station", color = 0)
    private val eatingOut = Category(4, "Eating out", CategoryType.EXPENSE, icon = "restaurant", color = 0, sortOrder = 2)
    private val loanCategory = Category(5, "Bank loan", CategoryType.EXPENSE, icon = "account_balance", color = 0, sortOrder = 3)
    private val salary = Category(6, "Salary", CategoryType.INCOME, icon = "payments", color = 0)
    private val categories = listOf(groceries, car, fuel, eatingOut, loanCategory, salary)

    private val me = Person(1, "Me", 0, sortOrder = 0)
    private val family = Person(2, "Family", 0, sortOrder = 1)
    private val card = Account(1, "Card", AccountType.DEBIT_CARD, "payments", 0, sortOrder = 0)
    private val cash = Account(2, "Cash", AccountType.CASH, "payments", 0, sortOrder = 1)
    private val savings = Account(3, "Savings", AccountType.SAVINGS, "savings", 0, includeInTotal = false, sortOrder = 2)

    private var nextId = 1L
    private fun entry(kind: TransactionKind, minor: Long, date: LocalDate, category: Category? = null, person: Person? = null, shop: String? = null, account: Account = card, to: Account? = null, note: String = "") =
        TransactionDetails(
            Transaction(nextId++, kind, Money(minor), date, account.id, to?.id, category?.id, person?.id, note = note),
            account,
            to,
            category,
            categories.firstOrNull { it.id == category?.parentId },
            person,
            shop?.let { Merchant(nextId, it) },
        )

    private val entries = listOf(
        entry(TransactionKind.INCOME, 250_000, LocalDate.of(2025, 12, 1), salary, me),
        entry(TransactionKind.EXPENSE, 4_240, LocalDate.of(2025, 12, 3), groceries, family, "Bravo"),
        entry(TransactionKind.EXPENSE, 6_000, LocalDate.of(2025, 12, 9), fuel, me, "Azpetrol"),
        entry(TransactionKind.EXPENSE, 30_000, LocalDate.of(2025, 12, 10), loanCategory, me, "Bank"),
        entry(TransactionKind.TRANSFER, 20_000, LocalDate.of(2025, 12, 12), account = card, to = cash),
        entry(TransactionKind.INCOME, 250_000, LocalDate.of(2026, 1, 2), salary, me),
        entry(TransactionKind.EXPENSE, 3_000, LocalDate.of(2026, 1, 5), eatingOut, me, "Mangal House", note = "My share"),
        entry(TransactionKind.DEBT, -6_000, LocalDate.of(2026, 1, 5), note = "Sam and Alex"),
        entry(TransactionKind.EXPENSE, 1_890, LocalDate.of(2026, 1, 8), groceries, family, "Araz", account = cash),
        entry(TransactionKind.ADJUSTMENT, 150, LocalDate.of(2026, 1, 15), account = cash),
    )

    private val input = AccountantInput(
        madeAt = LocalDateTime.of(2026, 1, 20, 3, 0),
        appVersion = "1.8.0",
        balances = listOf(AccountBalance(card, Money(1_204_870)), AccountBalance(cash, Money(18_260)), AccountBalance(savings, Money(500_000))),
        entries = entries,
        categories = categories,
        people = listOf(me, family),
        debts = listOf(
            DebtBalance(Debt(1, "Sam and Alex", DebtDirection.OWED_TO_ME, Money(9_000), categoryId = 4, note = "Mangal House", date = LocalDate.of(2026, 1, 5)), paid = Money(3_000), mine = Money(3_000)),
            DebtBalance(Debt(2, "Kamal", DebtDirection.I_OWE, Money(45_000), date = LocalDate.of(2025, 11, 1), monthly = Money(15_000)), paid = Money(15_000)),
            DebtBalance(Debt(3, "Settled", DebtDirection.OWED_TO_ME, Money(1_000), date = LocalDate.of(2025, 10, 1)), paid = Money(1_000)),
        ),
        recurring = listOf(
            RecurringPayment(1, "Car loan", Money(30_000), 1, 5, 1, RecurringFrequency.MONTHLY, LocalDate.of(2026, 2, 10), loan = LoanTerms(paymentsMade = 10, paymentsLeft = 14, principal = Money(600_000), annualRateBp = 1850)),
            RecurringPayment(2, "Internet", Money(3_500), 1, 1, 2, RecurringFrequency.MONTHLY, LocalDate.of(2026, 1, 25)),
        ),
        planned = listOf(PlannedStatus(PlannedPayment(1, "Car insurance", Money(12_000), LocalDate.of(2026, 1, 28), plannedOn = LocalDate.of(2026, 1, 3)))),
        receipts = listOf(
            Receipt(1, "Bravo", LocalDate.of(2025, 12, 3), Money(4_240), "F1", listOf(ReceiptItem("Milk 1l", 2_000, Money(180), Money(360), ItemGroup.entries.first()))),
        ),
    )

    @Test fun `the window is the month running and the six before it`() {
        assertThat(accountantWindow(today)).isEqualTo(DateRange(LocalDate.of(2025, 7, 1), today))
        assertThat(accountantMonths(today)).hasSize(7)
    }

    @Test fun `the snapshot is the saved copy`() {
        val html = accountantSnapshotHtml(input)
        File("build").mkdirs()
        File("build/accountant-snapshot.html").writeText(html)
        val saved = javaClass.getResource("/accountant-snapshot.html")?.readText()
        assertThat(html).isEqualTo(saved)
    }

    @Test fun `totals leave out transfers, debts and corrections`() {
        val html = accountantSnapshotHtml(input)
        // January: ₼2,500.00 in, ₼30.00 + ₼18.90 out.
        assertThat(html).contains("<td>Jan 2026 (so far)</td><td>₼2,500.00</td><td>₼48.90</td><td>₼2,451.10</td>")
        // December: the loan payment is spending.
        assertThat(html).contains("<td>Dec 2025</td><td>₼2,500.00</td><td>₼402.40</td><td>₼2,097.60</td>")
    }

    @Test fun `the total leaves out accounts kept out of it`() {
        assertThat(accountantSnapshotHtml(input)).contains("<td>Total</td><td></td><td>₼12,231.30</td>")
    }

    @Test fun `settled debts don't show`() {
        val html = accountantSnapshotHtml(input)
        assertThat(html).contains("<td>Kamal</td><td>Owner owes them</td><td>Owner borrowed</td><td>₼300.00</td><td>₼150.00</td>")
        assertThat(html).doesNotContain("<td>Settled</td>")
    }

    @Test fun `unconfirmed bank payments are never in it`() {
        assertThat(accountantSnapshotHtml(input)).doesNotContain("not yet logged")
    }

    @Test fun `Dev's copy says it's test data, at the top`() {
        val html = accountantSnapshotHtml(input.copy(testData = true))
        assertThat(html).contains("<h1>Cibim Dev snapshot – test data</h1><p>Test data from the Dev app")
        assertThat(accountantSnapshotHtml(input)).doesNotContain("test data")
    }

    @Test fun `names are escaped`() {
        val odd = input.copy(people = listOf(Person(9, "<b>Me</b>", 0)))
        assertThat(accountantSnapshotHtml(odd)).contains("&lt;b&gt;Me&lt;/b&gt;")
    }

    @Test fun `the sheet is Export CSV's for the window`() {
        assertThat(accountantTransactionsCsv(input)).startsWith("Date,Type,Amount,Account,To account,Category,Sub-category,For whom,Merchant,Note")
    }
}
