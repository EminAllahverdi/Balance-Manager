package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class DebtEntryTest {
    private fun owed(id: Long, name: String, minor: Long, paid: Long = 0, day: Int = 1, direction: DebtDirection = DebtDirection.I_OWE) =
        DebtBalance(Debt(id, name, direction, Money(minor), date = LocalDate.of(2026, 11, day)), Money(paid))

    @Test fun `open debts come one line per person and direction, whatever the name's case`() {
        val lines = listOf(
            owed(1, "Sam", 10_00),
            owed(2, "sam", 5_00, day = 3),
            owed(3, "Sam", 20_00, direction = DebtDirection.OWED_TO_ME),
            owed(4, "Alex", 8_00, paid = 8_00),
        ).owedByPerson()
        assertThat(lines.map { it.name to it.direction }).containsExactly("Sam" to DebtDirection.OWED_TO_ME, "Sam" to DebtDirection.I_OWE).inOrder()
        assertThat(lines.last().left).isEqualTo(Money(15_00))
    }

    @Test fun `a payment settles the oldest first and stops at what's owed`() {
        val older = owed(1, "Sam", 10_00, paid = 4_00, day = 1)
        val newer = owed(2, "Sam", 5_00, day = 3)
        assertThat(payBack(Money(8_00), listOf(older, newer))).containsExactly(older to Money(6_00), newer to Money(2_00)).inOrder()
        assertThat(payBack(Money(50_00), listOf(older, newer))).containsExactly(older to Money(6_00), newer to Money(5_00)).inOrder()
    }

    @Test fun `who ends up owing follows the kind`() {
        assertThat(DebtType.LENT.direction).isEqualTo(DebtDirection.OWED_TO_ME)
        assertThat(DebtType.PAID_FOR_OTHERS.direction).isEqualTo(DebtDirection.OWED_TO_ME)
        assertThat(DebtType.BORROWED.direction).isEqualTo(DebtDirection.I_OWE)
        assertThat(DebtType.PAID_FOR_ME.direction).isEqualTo(DebtDirection.I_OWE)
        assertThat(DebtType.PAID_FOR_ME.hasCategory).isTrue()
        assertThat(DebtType.LENT.hasCategory).isFalse()
    }
}
