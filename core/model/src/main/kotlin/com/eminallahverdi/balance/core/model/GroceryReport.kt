package com.eminallahverdi.balance.core.model

import java.time.YearMonth

/**
 * One month of groceries against the months before it. [spent] comes from the entries in the groceries category, so it
 * counts every shop. What was bought comes from the receipts scanned, which may be only some of them.
 */
data class GroceryReport(
    val month: YearMonth,
    val spent: Money,
    val lastMonth: Money,
    /** The average month over the 3, 6 and 12 before this one, among those with any groceries. Null with none. */
    val averages: Map<Int, Money?>,
    val receipts: Int,
    val itemsTotal: Money,
    val groups: List<GroupSpend>,
    val topItems: List<ItemSpend>,
)

/** A group's spending this month, and its average month over the receipts of the 3 months before. */
data class GroupSpend(val group: ItemGroup, val amount: Money, val before: Money?)

/** What one product came to this month, how much of it, how often, and its average month before. */
data class ItemSpend(val key: String, val name: String, val amount: Money, val quantityMilli: Long, val times: Int, val before: Money?)

val REPORT_SPANS = listOf(3, 6, 12)
private const val TOP_ITEMS = 10
private const val COMPARE_MONTHS = 3

/** [spentByMonth] is the groceries category's spending per month, from the entries. */
fun groceryReport(month: YearMonth, spentByMonth: Map<YearMonth, Money>, receipts: List<Receipt>): GroceryReport {
    val thisMonth = receipts.filter { YearMonth.from(it.date) == month }
    val items = thisMonth.flatMap { it.items }
    val earlier = (1..COMPARE_MONTHS).map { month.minusMonths(it.toLong()) }.toSet()
    val before = receipts.filter { YearMonth.from(it.date) in earlier }
    // Only months that have receipts say anything about what was bought.
    val monthsBefore = before.map { YearMonth.from(it.date) }.distinct().size

    fun averageBefore(amount: (List<ReceiptItem>) -> Money): Money? =
        if (monthsBefore == 0) null else Money(amount(before.flatMap { it.items }).minor / monthsBefore)

    return GroceryReport(
        month = month,
        spent = spentByMonth[month] ?: Money.ZERO,
        lastMonth = spentByMonth[month.minusMonths(1)] ?: Money.ZERO,
        averages = REPORT_SPANS.associateWith { span ->
            val months = (1..span).map { month.minusMonths(it.toLong()) }.mapNotNull { spentByMonth[it]?.takeIf { m -> m.minor > 0 } }
            if (months.isEmpty()) null else Money(months.sumOf { it.minor } / months.size)
        },
        receipts = thisMonth.size,
        itemsTotal = items.map { it.total }.sum(),
        groups = items.groupBy { it.group }
            .map { (group, list) -> GroupSpend(group, list.map { it.total }.sum(), averageBefore { all -> all.filter { it.group == group }.map { it.total }.sum() }) }
            .sortedByDescending { it.amount.minor },
        topItems = items.groupBy { it.key }
            .filterKeys { it.isNotEmpty() }
            .map { (key, list) ->
                ItemSpend(
                    key = key,
                    name = list.last().name,
                    amount = list.map { it.total }.sum(),
                    quantityMilli = list.sumOf { it.quantityMilli },
                    times = list.size,
                    before = averageBefore { all -> all.filter { it.key == key }.map { it.total }.sum() },
                )
            }
            .sortedByDescending { it.amount.minor }
            .take(TOP_ITEMS),
    )
}

/** The change from [before] to [now] in whole percent, or null when there's nothing to compare with. */
fun percentChange(now: Money, before: Money?): Int? {
    val base = before?.minor?.takeIf { it > 0 } ?: return null
    return ((now.minor - base) * 100 / base).toInt()
}
