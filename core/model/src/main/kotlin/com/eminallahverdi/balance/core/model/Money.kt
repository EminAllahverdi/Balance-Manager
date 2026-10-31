package com.eminallahverdi.balance.core.model

import kotlin.math.absoluteValue

/**
 * An amount in minor units (qəpik for AZN). Never use floating point for money.
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(minor + other.minor)
    operator fun minus(other: Money) = Money(minor - other.minor)
    operator fun unaryMinus() = Money(-minor)
    override fun compareTo(other: Money) = minor.compareTo(other.minor)

    val isZero: Boolean get() = minor == 0L
    val isNegative: Boolean get() = minor < 0L
    fun abs() = Money(minor.absoluteValue)

    companion object {
        val ZERO = Money(0)
        fun ofMajor(major: Long) = Money(major * 100)

        /** Parses user input like "16.31", "16,31" or "1 184". Returns null when it isn't a number. */
        fun parse(text: String): Money? {
            val clean = text.trim().replace(" ", "").replace(",", ".")
            if (clean.isEmpty()) return null
            val negative = clean.startsWith("-")
            val body = clean.removePrefix("-")
            val parts = body.split(".")
            if (parts.size > 2 || parts.any { p -> p.any { !it.isDigit() } }) return null
            val major = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
            val fraction = parts.getOrNull(1).orEmpty()
            if (fraction.length > 2) return null
            val minor = major * 100 + fraction.padEnd(2, '0').ifEmpty { "00" }.toLong()
            return Money(if (negative) -minor else minor)
        }
    }
}

fun Iterable<Money>.sum(): Money = Money(sumOf { it.minor })
