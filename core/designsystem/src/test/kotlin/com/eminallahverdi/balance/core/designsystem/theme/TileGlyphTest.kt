package com.eminallahverdi.balance.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TileGlyphTest {
    private val yellow = Color(0xFFFFD166)

    private fun contrastOnTile(glyph: Color, hue: Color): Float {
        val tile = hue.copy(alpha = TILE_TINT).compositeOver(LightBtColors.bgSurface).luminance()
        return (tile + 0.05f) / (glyph.luminance() + 0.05f)
    }

    @Test fun `dark surfaces keep the hue`() {
        assertThat(DarkBtColors.tileGlyph(yellow)).isEqualTo(yellow)
    }

    @Test fun `every palette hue stands out on a light tile`() {
        CategoryPalette.map { Color(it) }.forEach { hue ->
            assertThat(contrastOnTile(LightBtColors.tileGlyph(hue), hue)).isAtLeast(3f)
        }
    }

    @Test fun `a pale hue stays warm when it darkens`() {
        val glyph = LightBtColors.tileGlyph(yellow)
        assertThat(glyph.red).isGreaterThan(glyph.green)
        assertThat(glyph.green).isGreaterThan(glyph.blue)
    }

    @Test fun `a hue that already stands out is left alone`() {
        val navy = Color(0xFF1F3A6E)
        assertThat(LightBtColors.tileGlyph(navy)).isEqualTo(navy)
    }
}
