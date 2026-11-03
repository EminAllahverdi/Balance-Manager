package com.eminallahverdi.balance.core.designsystem.component

import com.eminallahverdi.balance.core.designsystem.theme.CategoryPalette
import com.eminallahverdi.balance.core.designsystem.theme.PersonPalette
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OwnColourTest {
    private fun channels(colour: Long) = listOf((colour shr 16) and 0xFF, (colour shr 8) and 0xFF, colour and 0xFF)

    @Test fun `every palette colour comes back as itself from the picker`() {
        (CategoryPalette + PersonPalette).forEach { colour ->
            val back = Hsl.of(colour).toColour()
            channels(back).zip(channels(colour)).forEach { (a, b) -> assertThat(a).isWithin(1).of(b) }
            assertThat(back shr 24).isEqualTo(0xFF)
        }
    }

    @Test fun `the palettes fill two rows, with the last place for a colour of one's own`() {
        assertThat(CategoryPalette).hasSize(13)
        assertThat(PersonPalette).hasSize(13)
        assertThat(CategoryPalette.toSet()).hasSize(13)
        assertThat(PersonPalette.toSet()).hasSize(13)
    }

    @Test fun `a colour of one's own stays where it reads on both themes`() {
        val black = Hsl.of(0xFF000000).usable()
        val white = Hsl.of(0xFFFFFFFF).usable()
        assertThat(black.lightness).isAtLeast(0.45f)
        assertThat(white.lightness).isAtMost(0.82f)
        assertThat(Hsl.of(0xFF808080).usable().saturation).isAtLeast(0.15f)
    }
}
