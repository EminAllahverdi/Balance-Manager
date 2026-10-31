package com.eminallahverdi.balance.feature.transaction

import com.eminallahverdi.balance.core.model.Money
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmountInputTest {
    @Test fun `amount round-trips through the keypad text`() {
        listOf(440L, 1200L, 5L, 123456L).forEach { minor ->
            assertThat(Money.parse(Money(minor).toInputText())).isEqualTo(Money(minor))
        }
    }

    @Test fun `whole amounts have no fraction to type over`() {
        assertThat(Money(1200).toInputText()).isEqualTo("12")
        assertThat(Money(440).toInputText()).isEqualTo("4.40")
        assertThat(Money(5).toInputText()).isEqualTo("0.05")
    }
}
