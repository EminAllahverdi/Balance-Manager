package com.eminallahverdi.balance.importer.moneymanager

import com.eminallahverdi.balance.core.model.TransactionKind
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

class ImportPlanTest {
    private val day = LocalDate.of(2026, 3, 1)
    private val time = Instant.parse("2026-03-01T10:00:00Z")

    private fun entry(
        amount: Long = 10_00,
        income: Boolean = false,
        category: String? = "Food",
        tags: List<String> = emptyList(),
        note: String = "",
        uid: String = "t1",
    ) = BackupEntry(uid, income, amount, day, time, "card", category, tags, note)

    private fun rule(category: String? = null, tag: String? = null, note: String? = null, type: String? = null, set: RuleChange = RuleChange()) =
        MappingRule(RuleMatch(type, category, tag, note), set)

    @Test fun `with no rules a category keeps its name and a tag becomes its sub-category`() {
        val planned = planEntry(entry(tags = listOf("Lunch")), emptyList())
        assertThat(planned.kind).isEqualTo(TransactionKind.EXPENSE)
        assertThat(planned.category).isEqualTo("Food")
        assertThat(planned.sub).isEqualTo("Lunch")
    }

    @Test fun `later rules refine earlier ones and set only what they name`() {
        val rules = listOf(
            rule(category = "Food", set = RuleChange(category = "Eating out", person = "Sam")),
            rule(tag = "Takeaway", set = RuleChange(sub = "Delivery", merchant = "Pizza Place")),
        )
        val planned = planEntry(entry(tags = listOf("Takeaway")), rules)
        assertThat(planned.category).isEqualTo("Eating out")
        assertThat(planned.sub).isEqualTo("Delivery")
        assertThat(planned.person).isEqualTo("Sam")
        assertThat(planned.merchant).isEqualTo("Pizza Place")
    }

    @Test fun `changing the category drops a sub-category set before it`() {
        val rules = listOf(rule(tag = "Parking", set = RuleChange(sub = "Parking")), rule(tag = "Parking", set = RuleChange(category = "Car")))
        assertThat(planEntry(entry(tags = listOf("Parking")), rules).sub).isNull()
    }

    @Test fun `a tag a rule explains doesn't become a sub-category`() {
        val planned = planEntry(entry(tags = listOf("Sam")), listOf(rule(tag = "Sam", set = RuleChange(person = "Sam"))))
        assertThat(planned.sub).isNull()
    }

    @Test fun `rules can match on a note, ignoring case, and on income or expense`() {
        val rules = listOf(rule(type = "Income", note = "pizza", set = RuleChange(merchant = "Wrong")), rule(type = "Expense", note = "pizza", set = RuleChange(merchant = "Pizza Place")))
        assertThat(planEntry(entry(note = "Friday PIZZA"), rules).merchant).isEqualTo("Pizza Place")
    }

    @Test fun `an entry with no category is a signed adjustment`() {
        assertThat(planEntry(entry(amount = 5_00, category = null), emptyList()).let { it.kind to it.amount }).isEqualTo(TransactionKind.ADJUSTMENT to -5_00L)
        assertThat(planEntry(entry(amount = 5_00, category = null, income = true), emptyList()).amount).isEqualTo(5_00L)
    }

    @Test fun `a rule can take old bookkeeping out of income, keeping the balance and a note`() {
        val rules = listOf(rule(type = "Income", category = "Card offset", set = RuleChange(kind = "INTERNAL", note = "Card offset")))
        val planned = planEntry(entry(amount = 7_00, income = true, category = "Card offset"), rules)
        assertThat(planned.kind).isEqualTo(TransactionKind.INTERNAL)
        assertThat(planned.amount).isEqualTo(7_00L)
        assertThat(planned.category).isNull()
        assertThat(planned.note).isEqualTo("Card offset")
    }

    @Test fun `an entry's own note is trimmed and wins over a rule's`() {
        val rules = listOf(rule(category = "Food", set = RuleChange(note = "From a rule")))
        assertThat(planEntry(entry(note = " Dinner "), rules).note).isEqualTo("Dinner")
    }

    @Test fun `a plan that adds up has no differences`() {
        val backup = Backup(
            accounts = listOf(BackupAccount("card", "Card", balance = 90_00), BackupAccount("cash", "Cash", balance = 25_00)),
            entries = listOf(entry(amount = 10_00, income = true, uid = "t1"), entry(amount = 20_00, uid = "t2"), entry(amount = 125_00, category = null, income = true, uid = "t3")),
            transfers = listOf(BackupTransfer("x1", 25_00, day, time, "card", "cash")),
        )
        assertThat(planImport(backup, ImportMapping()).differences).isEmpty()
    }

    @Test fun `a balance that doesn't add up is reported`() {
        val backup = Backup(listOf(BackupAccount("card", "Card", balance = 1_00)), listOf(entry(amount = 3_00, income = true)), emptyList())
        assertThat(planImport(backup, ImportMapping()).differences).containsExactly(BalanceDifference("Card", 1_00, 3_00))
    }

    @Test fun `people named only in rules are still created, and the mapping picks the default account`() {
        val backup = Backup(listOf(BackupAccount("card", "Card")), listOf(entry(tags = listOf("Sam"))), emptyList(), defaultAccountUid = "other")
        val mapping = ImportMapping(defaultAccount = "Card", rules = listOf(rule(tag = "Sam", set = RuleChange(person = "Sam"))))
        val plan = planImport(backup, mapping)
        assertThat(plan.people.map { it.name }).containsExactly("Sam")
        assertThat(plan.defaultAccountUid).isEqualTo("card")
    }

    @Test fun `a mapping file parses, including rules that set nothing`() {
        val mapping = ImportMapping.parse(
            """
            { "defaultAccount": "Card", "unknown": 1,
              "people": [{ "name": "Sam", "color": "5B9DFF" }],
              "accounts": [{ "name": "Card", "type": "CREDIT_CARD" }],
              "rules": [{ "when": { "category": "Food", "tag": "Sam" } }, { "when": { "tag": "Lunch" }, "set": { "sub": "Lunch" } }] }
            """,
        )
        assertThat(mapping.rules.map { it.set.sub }).containsExactly(null, "Lunch").inOrder()
        assertThat(hexColor(mapping.people.single().color)).isEqualTo(0xFF5B9DFF)
    }
}
