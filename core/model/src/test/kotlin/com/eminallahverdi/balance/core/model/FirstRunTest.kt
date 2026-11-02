package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FirstRunTest {
    @Test fun `a fresh install gets onboarding`() {
        assertThat(needsOnboarding(done = false, hasData = false)).isTrue()
    }

    @Test fun `an install from before onboarding has no flag but has data, and never sees it`() {
        assertThat(needsOnboarding(done = false, hasData = true)).isFalse()
    }

    @Test fun `once it's done it's done, even if everything was deleted since`() {
        assertThat(needsOnboarding(done = true, hasData = false)).isFalse()
        assertThat(needsOnboarding(done = true, hasData = true)).isFalse()
    }

    private fun ask(sdk: Int = 34, remindersOn: Boolean = true, allowed: Boolean = false, asked: Boolean = false) =
        needsNotificationAsk(sdk, remindersOn, allowed, asked)

    @Test fun `reminders that are on but not allowed get asked about`() {
        assertThat(ask()).isTrue()
    }

    @Test fun `before Android 13 there's nothing to ask`() {
        assertThat(ask(sdk = 32)).isFalse()
        assertThat(ask(sdk = 33)).isTrue()
    }

    @Test fun `with reminders off there's nothing to allow`() {
        assertThat(ask(remindersOn = false)).isFalse()
    }

    @Test fun `already allowed needs no question`() {
        assertThat(ask(allowed = true)).isFalse()
    }

    @Test fun `it only asks once on this install`() {
        assertThat(ask(asked = true)).isFalse()
    }
}
