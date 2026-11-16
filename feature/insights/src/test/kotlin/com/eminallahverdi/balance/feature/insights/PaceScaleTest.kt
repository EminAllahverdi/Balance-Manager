package com.eminallahverdi.balance.feature.insights

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PaceScaleTest {
    @Test fun `the step is the smallest round one that reaches the top in four lines`() {
        assertThat(gridStep(1_737_41)).isEqualTo(500_00)
        assertThat(gridStep(2_000_00)).isEqualTo(500_00)
        assertThat(gridStep(2_000_01)).isEqualTo(1_000_00)
        assertThat(gridStep(90_00)).isEqualTo(25_00)
        assertThat(gridStep(0)).isEqualTo(1_00)
    }

    @Test fun `the scale is in whole manats, and thousands once it reaches them`() {
        assertThat(axisLabel(250_00, 750_00)).isEqualTo("250")
        assertThat(axisLabel(500_00, 2_000_00)).isEqualTo("0.5k")
        assertThat(axisLabel(1_000_00, 2_000_00)).isEqualTo("1k")
        assertThat(axisLabel(250_00, 1_000_00)).isEqualTo("0.25k")
        assertThat(axisLabel(12_500_00, 50_000_00)).isEqualTo("12.5k")
    }
}
