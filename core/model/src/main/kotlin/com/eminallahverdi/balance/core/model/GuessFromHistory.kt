package com.eminallahverdi.balance.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Why a pending entry was filled in as it was, shown beside a suggestion so the owner can judge it at a glance and put it
 * right when it's wrong.
 */
sealed interface GuessReason {
    /** What [times] of the owner's recent entries at [shop] were filed under, or with one, what the last was. */
    data class UsualAt(val shop: String, val times: Int) : GuessReason

    /** Like the owner's other payments of about [amount], at about [minuteOfDay] when that's known. */
    data class LikeAmount(val amount: Money, val minuteOfDay: Int?) : GuessReason

    /** A recurring payment or loan it matches, by name or by amount near its date. */
    data class Recurring(val name: String) : GuessReason

    /** A word in the notification, like SUPERMARKET, or a category's own name. */
    data object Words : GuessReason
}

/** What a shop's entries were usually filed as: the category, and who it was for and where it came from with it. */
data class UsualFiling(val shop: String, val categoryId: Long, val personId: Long?, val accountId: Long?, val times: Int)

/** Shop entries looked at for what's usual: the most recent, so a change of habit wins out within a few payments. */
private const val RECENT_AT_A_SHOP = 12

/** A share of those that has to agree for a usual, and how many at the least. */
private const val USUAL_SHARE = 0.5
private const val USUAL_AT_LEAST = 2

/** Close enough in amount: within this share of it, or this many qəpik for small sums. */
private const val AMOUNT_SHARE = 0.08
private const val AMOUNT_QEPIK = 20L

/** Close enough in time of day, either side. */
private const val MINUTES_EITHER_SIDE = 90

/** How many alike payments, and what share of them agreeing, make a guess from amount and time, or amount alone. */
private const val ALIKE_TIMED = 2
private const val ALIKE_UNTIMED = 3
private const val ALIKE_TIMED_SHARE = 0.6
private const val ALIKE_UNTIMED_SHARE = 0.7

/** Words a shop's name carries that say nothing about which shop: company forms, a city, a branch. */
private val SHOP_NOISE = setOf("mmc", "llc", "ltd", "asc", "qsc", "aqs", "inc", "az", "baku", "baki", "filial", "branch", "the", "www", "com", "net", "org")

/** A shop's name as it's compared: its words in plain case, numbers and noise left out. */
fun shopKey(name: String): List<String> = name.caseKey().replace("ı", "i").split(Regex("""[^\p{L}]+""")).filter { it.length >= 2 && it !in SHOP_NOISE }

/**
 * Whether two names are the same shop: the same words, or one is the other with more after it, as BRAVO SUMQAYIT is
 * Bravo. A first word that short could be anyone's, so it has to be at least four letters.
 */
fun sameShop(a: String, b: String): Boolean {
    val x = shopKey(a)
    val y = shopKey(b)
    if (x.isEmpty() || y.isEmpty()) return false
    if (x == y) return true
    val (short, long) = if (x.size <= y.size) x to y else y to x
    return short.first().length >= 4 && long.take(short.size) == short
}

/**
 * What the owner usually files [shop] as, from their recent entries there: the category most of them share, and who and
 * which account went with it most. Entries named exactly as [shop] count first; only without any, a longer or shorter
 * name of it does. Null with no entries there, or when they're too split to call.
 */
fun usualFiling(shop: String, history: List<TransactionDetails>): UsualFiling? {
    val spent = history.filter { it.transaction.kind == TransactionKind.EXPENSE && it.transaction.categoryId != null && it.merchant != null }
    val key = shopKey(shop)
    if (key.isEmpty()) return null
    val exact = spent.filter { shopKey(it.merchant!!.name) == key }
    val there = exact.ifEmpty { spent.filter { sameShop(it.merchant!!.name, shop) } }
        .sortedWith(compareByDescending<TransactionDetails> { it.transaction.date }.thenByDescending { it.transaction.id })
        .take(RECENT_AT_A_SHOP)
    if (there.isEmpty()) return null
    val (categoryId, rows) = there.groupBy { it.transaction.categoryId!! }.maxBy { (_, rows) -> rows.size }
    if (there.size > 1 && (rows.size < USUAL_AT_LEAST || rows.size < there.size * USUAL_SHARE)) return null
    return UsualFiling(
        shop = there.first().merchant!!.name,
        categoryId = categoryId,
        personId = rows.mapNotNull { it.transaction.personId }.mostCommon(),
        accountId = rows.map { it.transaction.accountId }.mostCommon(),
        times = rows.size,
    )
}

/** What a payment like this one usually is: a category, and the reason, which says whether time of day played a part. */
data class AlikeFiling(val categoryId: Long, val personId: Long?, val reason: GuessReason.LikeAmount)

/**
 * For a shop never seen before: the category of the owner's other payments of about [amount]. When the payment's time is
 * known, ones at about the same time of day on the same kind of day (a weekday or the weekend) come first, as a coffee at
 * nine on a weekday is a breakfast; without enough of those, the amount alone has to be clearer. Null when nothing alike
 * agrees enough.
 */
fun alikeFiling(amount: Money, paidAt: java.time.Instant?, history: List<TransactionDetails>, zone: ZoneId = ZoneId.systemDefault()): AlikeFiling? {
    val tolerance = maxOf((amount.minor * AMOUNT_SHARE).toLong(), AMOUNT_QEPIK)
    val alike = history.filter {
        it.transaction.kind == TransactionKind.EXPENSE && it.transaction.categoryId != null && abs(it.transaction.amount.minor - amount.minor) <= tolerance
    }
    val at = paidAt?.atZone(zone)
    if (at != null) {
        val minute = at.hour * 60 + at.minute
        val weekend = at.dayOfWeek.isWeekend()
        val timed = alike.filter { entry ->
            val then = entry.transaction.paidAt?.atZone(zone) ?: return@filter false
            then.dayOfWeek.isWeekend() == weekend && minutesApart(then.hour * 60 + then.minute, minute) <= MINUTES_EITHER_SIDE
        }
        agreeing(timed, ALIKE_TIMED, ALIKE_TIMED_SHARE)?.let { (categoryId, personId) ->
            return AlikeFiling(categoryId, personId, GuessReason.LikeAmount(amount, minute))
        }
    }
    return agreeing(alike, ALIKE_UNTIMED, ALIKE_UNTIMED_SHARE)?.let { (categoryId, personId) -> AlikeFiling(categoryId, personId, GuessReason.LikeAmount(amount, null)) }
}

/** The category most of [rows] share, with who they were usually for, when at least [atLeast] do and they're [share] of them. */
private fun agreeing(rows: List<TransactionDetails>, atLeast: Int, share: Double): Pair<Long, Long?>? {
    if (rows.size < atLeast) return null
    val (categoryId, same) = rows.groupBy { it.transaction.categoryId!! }.maxBy { (_, r) -> r.size }
    if (same.size < atLeast || same.size < rows.size * share) return null
    return categoryId to same.mapNotNull { it.transaction.personId }.mostCommon()
}

private fun DayOfWeek.isWeekend(): Boolean = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY

/** Minutes between two times of day, the short way round midnight. */
private fun minutesApart(a: Int, b: Int): Int = abs(a - b).let { minOf(it, 24 * 60 - it) }

private fun <T> List<T>.mostCommon(): T? = groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

/**
 * A payment that comes round at the same shop for about the same amount, every month or every week, which isn't a
 * recurring payment yet: offered on Recurring payments to add with a tap. [key] is what a dismissal remembers.
 */
data class RepeatingPayment(
    val key: String,
    val shop: String,
    val categoryId: Long,
    val personId: Long?,
    val accountId: Long,
    val amount: Money,
    val frequency: RecurringFrequency,
    val times: Int,
    val lastDate: LocalDate,
    val nextDate: LocalDate,
)

/** Payments that need to have come round to count as repeating, and how far a usual amount may drift. */
private const val REPEATS_AT_LEAST = 3
private const val REPEAT_AMOUNT_SHARE = 0.1

/**
 * Shops paid about the same amount at a steady pace, three times or more running, the last of them recently enough that
 * it's still going: every 25 to 35 days, or every 6 to 8. One already a recurring payment, by name or by its category and
 * amount, isn't offered, and nor is one the owner said no to ([dismissed] keys).
 */
fun repeatingPayments(history: List<TransactionDetails>, recurring: List<RecurringPayment>, today: LocalDate, dismissed: Set<String> = emptySet()): List<RepeatingPayment> {
    val spent = history.filter { it.transaction.kind == TransactionKind.EXPENSE && it.transaction.categoryId != null && it.merchant != null }
    return spent.groupBy { shopKey(it.merchant!!.name).joinToString(" ") }
        .filterKeys { it.isNotEmpty() }
        .mapNotNull { (key, rows) -> repeatIn(key, rows.sortedBy { it.transaction.date }, today) }
        .filterNot { it.key in dismissed }
        .filterNot { repeat ->
            recurring.any { p ->
                !p.isFinished && (sameShop(p.name, repeat.shop) || (p.categoryId == repeat.categoryId && closeAmounts(p.amount, repeat.amount)))
            }
        }
        .sortedByDescending { it.lastDate }
}

private fun repeatIn(key: String, rows: List<TransactionDetails>, today: LocalDate): RepeatingPayment? {
    if (rows.size < REPEATS_AT_LEAST) return null
    for ((frequency, days, stillGoing) in listOf(Triple(RecurringFrequency.MONTHLY, 25L..35L, 40L), Triple(RecurringFrequency.WEEKLY, 6L..8L, 10L))) {
        // The latest run of payments at this pace, from the newest back.
        val run = mutableListOf(rows.last())
        for (row in rows.dropLast(1).asReversed()) {
            val gap = ChronoUnit.DAYS.between(row.transaction.date, run.last().transaction.date)
            if (gap == 0L) continue
            if (gap !in days || !closeAmounts(row.transaction.amount, run.first().transaction.amount)) break
            run += row
        }
        val last = run.first().transaction
        if (run.size < REPEATS_AT_LEAST || ChronoUnit.DAYS.between(last.date, today) > stillGoing) continue
        var next = if (frequency == RecurringFrequency.MONTHLY) last.date.plusMonths(1) else last.date.plusWeeks(1)
        while (next.isBefore(today)) next = if (frequency == RecurringFrequency.MONTHLY) next.plusMonths(1) else next.plusWeeks(1)
        return RepeatingPayment(
            key = "$key|${frequency.name}",
            shop = run.first().merchant!!.name,
            categoryId = last.categoryId!!,
            personId = last.personId,
            accountId = last.accountId,
            amount = last.amount,
            frequency = frequency,
            times = run.size,
            lastDate = last.date,
            nextDate = next,
        )
    }
    return null
}

private fun closeAmounts(a: Money, b: Money): Boolean = abs(a.minor - b.minor) <= maxOf(a.minor, b.minor) * REPEAT_AMOUNT_SHARE
