package com.eminallahverdi.balance.feature.history

import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.rawText
import com.eminallahverdi.balance.core.common.uiText
import com.eminallahverdi.balance.core.designsystem.component.label
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.Debt
import com.eminallahverdi.balance.core.model.DebtDirection
import com.eminallahverdi.balance.core.model.DebtEntryRole
import com.eminallahverdi.balance.core.model.Merchant
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.Transaction
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.TransactionKind
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

class HistoryFilterTest {
    private val card = Account(1, "Card", AccountType.DEBIT_CARD, "payments", 0)
    private val cash = Account(2, "Cash", AccountType.CASH, "payments", 0)
    private val me = Person(1, "Me", 0)
    private val transport = Category(10, "Transport", CategoryType.EXPENSE, icon = "local_taxi", color = 0xFF111111)
    private val taxi = Category(11, "Taxi", CategoryType.EXPENSE, parentId = 10, icon = "local_taxi", color = 0xFF111111)
    private val bolt = Merchant(1, "Bolt")

    private fun row(
        amount: Long = 770,
        kind: TransactionKind = TransactionKind.EXPENSE,
        category: Category? = taxi,
        parent: Category? = transport,
        toAccount: Account? = null,
        note: String = "",
    ) = TransactionDetails(
        transaction = Transaction(1, kind, Money(amount), LocalDate.of(2026, 10, 31), accountId = card.id, toAccountId = toAccount?.id, note = note),
        account = card,
        toAccount = toAccount,
        category = category,
        parentCategory = parent,
        person = me,
        merchant = bolt,
    )

    @Test fun `opens on this month with nothing narrowed by default`() {
        val filter = initialFilter(month = null, categoryId = null, personId = null)
        assertThat(filter).isEqualTo(HistoryFilter())
    }

    @Test fun `all the transfers start on all time, and keep only transfers`() {
        val filter = initialFilter(month = null, categoryId = null, personId = null, kind = "TRANSFER")
        assertThat(filter.month).isNull()
        assertThat(filter.kind).isEqualTo(TransactionKind.TRANSFER)
        assertThat(filter.matches(row(kind = TransactionKind.TRANSFER, category = null, parent = null, toAccount = cash))).isTrue()
        assertThat(filter.matches(row())).isFalse()
        assertThat(filter.emptyMessage(YearMonth.now())).isEqualTo(uiText(R.string.history_no_transfers_yet))
    }

    @Test fun `a list of only transfers totals what moved`() {
        val moves = listOf(row(500_00, TransactionKind.TRANSFER, null, null, cash), row(250_00, TransactionKind.TRANSFER, null, null, cash))
        assertThat(moves.headlineTotal()).isEqualTo(Money(750_00))
        assertThat(moves.hasSpending()).isFalse()
    }

    @Test fun `opens on what was tapped on Home`() {
        val filter = initialFilter(month = "2026-10", categoryId = transport.id, personId = me.id)
        assertThat(filter.month).isEqualTo(YearMonth.of(2026, 10))
        assertThat(filter.categoryId).isEqualTo(transport.id)
        assertThat(filter.personId).isEqualTo(me.id)
        assertThat(filter.matches(row())).isTrue()
    }

    @Test fun `an empty list says what's actually true`() {
        val now = YearMonth.of(2026, 11)
        assertThat(HistoryFilter(month = null).emptyMessage(now)).isEqualTo(uiText(R.string.history_nothing_yet_first))
        assertThat(HistoryFilter(month = now).emptyMessage(now)).isEqualTo(uiText(R.string.history_nothing_this_month))
        assertThat(HistoryFilter(month = YearMonth.of(2025, 10)).emptyMessage(now)).isEqualTo(uiText(R.string.history_nothing_in, "October 2025"))
        assertThat(HistoryFilter(month = YearMonth.of(2025, 10), personId = 1).emptyMessage(now)).isEqualTo(uiText(R.string.history_nothing_matches))
    }

    @Test fun `search finds merchant, category, note and amount`() {
        val r = row(amount = 123450, note = "airport")
        assertThat(HistoryFilter(query = "BOLT").matches(r)).isTrue()
        assertThat(HistoryFilter(query = "taxi").matches(r)).isTrue()
        assertThat(HistoryFilter(query = "airport").matches(r)).isTrue()
        assertThat(HistoryFilter(query = "1234.50").matches(r)).isTrue()
        assertThat(HistoryFilter(query = "1234,5").matches(r)).isTrue()
        assertThat(HistoryFilter(query = "zara").matches(r)).isFalse()
    }

    @Test fun `category filter includes its sub-categories`() {
        assertThat(HistoryFilter(categoryId = transport.id).matches(row())).isTrue()
        assertThat(HistoryFilter(categoryId = taxi.id).matches(row())).isTrue()
        assertThat(HistoryFilter(categoryId = 99).matches(row())).isFalse()
    }

    @Test fun `account filter finds both sides of a transfer`() {
        val transfer = row(kind = TransactionKind.TRANSFER, category = null, parent = null, toAccount = cash)
        assertThat(HistoryFilter(accountId = cash.id).matches(transfer)).isTrue()
        assertThat(HistoryFilter(accountId = card.id).matches(transfer)).isTrue()
        assertThat(HistoryFilter(accountId = cash.id).matches(row())).isFalse()
    }

    @Test fun `all filters must match`() {
        assertThat(HistoryFilter(personId = me.id, query = "bolt").matches(row())).isTrue()
        assertThat(HistoryFilter(personId = 2, query = "bolt").matches(row())).isFalse()
    }

    @Test fun `total is what was spent, leaving out income and transfers`() {
        val rows = listOf(row(770), row(1000, TransactionKind.INCOME), row(500, TransactionKind.TRANSFER, toAccount = cash))
        assertThat(rows.headlineTotal()).isEqualTo(Money(770))
    }

    @Test fun `total falls back to income when nothing was spent`() {
        assertThat(listOf(row(1000, TransactionKind.INCOME)).headlineTotal()).isEqualTo(Money(1000))
    }

    @Test fun `the eye leaves spending visible and hides the rest`() {
        assertThat(row(kind = TransactionKind.EXPENSE).isSensitive).isFalse()
        assertThat(row(kind = TransactionKind.INCOME).isSensitive).isTrue()
        assertThat(row(kind = TransactionKind.TRANSFER).isSensitive).isTrue()
        assertThat(row(kind = TransactionKind.ADJUSTMENT).isSensitive).isTrue()
    }

    @Test fun `a total counts as spending only when something was spent`() {
        assertThat(listOf(row(770), row(1000, TransactionKind.INCOME)).hasSpending()).isTrue()
        assertThat(listOf(row(1000, TransactionKind.INCOME)).hasSpending()).isFalse()
    }

    @Test fun `amounts carry their sign`() {
        assertThat(row(770).signedAmount()).isEqualTo("−₼7.70")
        assertThat(row(5000, TransactionKind.INCOME).signedAmount()).isEqualTo("+₼50.00")
        assertThat(row(500, TransactionKind.TRANSFER).signedAmount()).isEqualTo("₼5.00")
    }

    @Test fun `rows describe themselves`() {
        assertThat(row().title).isEqualTo(rawText("Bolt"))
        assertThat(row().meta).containsExactly(rawText("Transport"), rawText("Taxi"), rawText("Card")).inOrder()
        assertThat(row(kind = TransactionKind.TRANSFER, toAccount = cash).meta).containsExactly(rawText("Card → Cash"))
    }

    private val owed = Debt(1, "Sam", DebtDirection.I_OWE, Money(50_00), date = LocalDate.of(2026, 10, 1))
    private val lunch = owed.copy(id = 2, categoryId = taxi.id)

    private fun debtRow(debt: Debt, amount: Long, kind: TransactionKind = TransactionKind.DEBT, note: String = "") = row(amount, kind, category = null, parent = null, note = note)
        .let { it.copy(transaction = it.transaction.copy(debtId = debt.id), debt = debt, merchant = null) }

    @Test fun `debt entries say which way the money went, and neither is income or spending`() {
        val borrowed = debtRow(owed, 50_00)
        val paid = debtRow(owed, -15_00, note = "Rent")
        assertThat(borrowed.title).isEqualTo(DebtEntryRole.BORROWED_FROM.label("Sam"))
        assertThat(borrowed.signedAmount()).isEqualTo("+₼50.00")
        assertThat(paid.title).isEqualTo(DebtEntryRole.PAID_BACK_TO.label("Sam"))
        assertThat(paid.signedAmount()).isEqualTo("−₼15.00")
        assertThat(paid.meta).containsExactly(rawText("Card"), rawText("Rent")).inOrder()
        assertThat(borrowed.isSensitive).isTrue()
        assertThat(listOf(borrowed, paid).headlineTotal()).isEqualTo(Money.ZERO)
    }

    @Test fun `a debt paid back as an expense keeps its category and says who it went to`() {
        val paid = row(10_00, category = taxi, parent = transport).let { it.copy(transaction = it.transaction.copy(debtId = lunch.id), debt = lunch, merchant = null) }
        assertThat(paid.title).isEqualTo(rawText("Taxi"))
        assertThat(paid.meta).containsExactly(rawText("Transport"), rawText("Taxi"), DebtEntryRole.PAID_BACK_TO.label("Sam"), rawText("Card")).inOrder()
        assertThat(paid.isEditable).isTrue()
    }

    @Test fun `only payments can be taken back from the list, not what a debt began with`() {
        assertThat(debtRow(owed, -15_00).isRemovable).isTrue()
        assertThat(debtRow(owed, 50_00).isRemovable).isFalse()
        assertThat(debtRow(owed, 50_00).isEditable).isFalse()
    }

    @Test fun `deleting asks about the entry by name, amount and day`() {
        val today = LocalDate.of(2026, 10, 31)
        val removal = row().removal(hideAmounts = false, today = today)
        assertThat(removal.title).isEqualTo(R.string.history_delete_transaction_q)
        assertThat(removal.named).containsExactly(rawText("Bolt"), rawText("−₼7.70"), rawText("Today")).inOrder()
        assertThat(removal.action).isEqualTo(R.string.history_delete)
        assertThat(row().removal(hideAmounts = false, today = today.plusDays(5)).named).containsExactly(rawText("Bolt"), rawText("−₼7.70"), rawText("Sat 31 Oct")).inOrder()
    }

    @Test fun `the eye hides the amount in the question as it does on the row`() {
        val today = LocalDate.of(2026, 10, 31)
        assertThat(row().removal(hideAmounts = true, today = today).named).containsExactly(rawText("Bolt"), rawText("−₼7.70"), rawText("Today")).inOrder()
        assertThat(row(50_00, TransactionKind.INCOME).removal(hideAmounts = true, today = today).named).containsExactly(rawText("Bolt"), rawText("Today")).inOrder()
    }

    @Test fun `a debt payment is removed, and says the debt changes back`() {
        val removal = debtRow(owed, -15_00).removal(hideAmounts = false, today = LocalDate.of(2026, 10, 31))
        assertThat(removal.title).isEqualTo(R.string.history_remove_payment_q)
        assertThat(removal.named).containsExactly(DebtEntryRole.PAID_BACK_TO.label("Sam"), rawText("−₼15.00"), rawText("Today")).inOrder()
        assertThat(removal.consequence).isEqualTo(R.string.history_remove_payment_text)
        assertThat(removal.action).isEqualTo(R.string.history_remove)
    }

    @Test fun `search finds who a debt is with`() {
        assertThat(HistoryFilter(query = "sam").matches(debtRow(owed, 50_00))).isTrue()
    }

    @Test fun `a whole month from Reports reads as that month`() {
        val october = DateRange.month(YearMonth.of(2026, 10))
        val filter = initialFilter(null, transport.id, null, from = october.start.toEpochDay(), to = october.endInclusive.toEpochDay())
        assertThat(filter.month).isEqualTo(YearMonth.of(2026, 10))
        assertThat(filter.span).isNull()
        assertThat(filter.categoryId).isEqualTo(transport.id)
    }

    @Test fun `a week from Reports keeps its days, and names them`() {
        val week = DateRange(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1))
        val filter = initialFilter(null, transport.id, null, from = week.start.toEpochDay(), to = week.endInclusive.toEpochDay())
        assertThat(filter.month).isNull()
        assertThat(filter.range).isEqualTo(week)
        assertThat(filter.matches(row())).isTrue()
        val label = "${DateLabels.dayMonth(week.start)} – ${DateLabels.dayMonth(week.endInclusive)}".replace(' ', ' ').replace(" – ", " – ")
        assertThat(filter.periodLabel { it.toString() }).isEqualTo(label)
        assertThat(filter.copy(categoryId = null).emptyMessage(YearMonth.of(2026, 11))).isEqualTo(uiText(R.string.history_nothing_in, label))
    }

    @Test fun `a year from Reports is named by its number`() {
        val year = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        assertThat(year.label()).isEqualTo("2026")
        assertThat(initialFilter(null, null, null, from = year.start.toEpochDay(), to = year.endInclusive.toEpochDay()).span).isEqualTo(year)
    }

    @Test fun `Clear goes back to this month with nothing narrowed, and keeps the search`() {
        val now = YearMonth.of(2026, 11)
        assertThat(HistoryFilter(month = now).canClear(now)).isFalse()
        val narrowed = HistoryFilter(month = YearMonth.of(2026, 9), span = DateRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13)), query = "bolt", personId = me.id, categoryId = transport.id, accountId = card.id)
        assertThat(narrowed.canClear(now)).isTrue()
        assertThat(narrowed.cleared(now)).isEqualTo(HistoryFilter(month = now, query = "bolt"))
    }

    @Test fun `Clear on a list of only transfers keeps it to transfers, over all time`() {
        val transfers = initialFilter(null, null, null, kind = "TRANSFER")
        assertThat(transfers.canClear()).isFalse()
        assertThat(transfers.copy(accountId = card.id).cleared()).isEqualTo(transfers)
    }
}
