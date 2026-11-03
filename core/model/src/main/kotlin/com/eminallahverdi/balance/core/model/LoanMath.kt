package com.eminallahverdi.balance.core.model

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/*
 * Loan sums the way banks work them out: equal monthly payments, each paying that month's interest and some of what was
 * borrowed. Decimals stay exact until the end, and only the result is rounded to the qəpik.
 */

private val MC = MathContext.DECIMAL64

/** Hundredths of a percent a year, as a fraction a month: 1850 is 0.185 / 12. */
private const val BP_PER_YEAR_TO_MONTHLY = 120_000
private const val MAX_RATE_BP = 10_000

private fun monthlyRate(annualRateBp: Int): BigDecimal = BigDecimal(annualRateBp).divide(BigDecimal(BP_PER_YEAR_TO_MONTHLY), MC)

private fun BigDecimal.toMoney(): Money = Money(setScale(0, RoundingMode.HALF_UP).longValueExact())

/** "18.5" or "18,5" as hundredths of a percent. Nothing over 100%. */
fun parseRate(text: String): Int? {
    val value = text.trim().removeSuffix("%").trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    val bp = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
    return bp.toInt().takeIf { bp.signum() >= 0 && bp <= BigDecimal(MAX_RATE_BP) }
}

/** 1850 as "18.5", for a field to start with. */
fun formatRate(annualRateBp: Int): String = BigDecimal(annualRateBp).movePointLeft(2).stripTrailingZeros().toPlainString()

/** The monthly payment that pays [principal] off in [months] at [annualRateBp]. Without interest it's split evenly. */
fun annuityPayment(principal: Money, annualRateBp: Int, months: Int): Money? {
    if (months <= 0 || principal.minor <= 0) return null
    val p = BigDecimal(principal.minor)
    if (annualRateBp == 0) return p.divide(BigDecimal(months), MC).toMoney()
    val r = monthlyRate(annualRateBp)
    val growth = BigDecimal.ONE.add(r).pow(months, MC)
    return p.multiply(r).multiply(growth).divide(growth.subtract(BigDecimal.ONE), MC).toMoney()
}

/**
 * What's still owed of what was borrowed: each payment left, less the interest it carries. Interest is charged on what's
 * owed, so this is the payments discounted back by the monthly rate. Null without a rate.
 */
val RecurringPayment.principalLeft: Money?
    get() {
        val terms = loan ?: return null
        val rate = terms.annualRateBp ?: return null
        val left = terms.paymentsLeft
        if (left <= 0) return Money.ZERO
        if (rate == 0) return stillToPay
        val step = BigDecimal.ONE.add(monthlyRate(rate))
        val last = BigDecimal((terms.lastPayment ?: amount).minor).divide(step.pow(left, MC), MC)
        val regular = (1 until left).fold(BigDecimal.ZERO) { sum, k -> sum.add(BigDecimal(amount.minor).divide(step.pow(k, MC), MC)) }
        return regular.add(last).toMoney()
    }

/** The interest still to come, out of [RecurringPayment.stillToPay]. */
val RecurringPayment.interestLeft: Money? get() = principalLeft?.let { stillToPay - it }

/** What the loan costs over its whole term on top of what was borrowed: its interest and the bank's commission. */
val RecurringPayment.totalCost: Money?
    get() {
        val terms = loan ?: return null
        val principal = terms.principal ?: return null
        if (terms.term == 0) return null
        val paid = Money(amount.minor * (terms.term - 1) + (terms.lastPayment ?: amount).minor)
        return paid - principal + (terms.commission ?: Money.ZERO)
    }
