package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Made-up PINs only: the real one never appears in this repo. */
class AccountantLockTest {
    private val lock = AccountantLock(accountantPinHash("4321"))

    @Test fun `the right PIN opens it, with or without spaces around`() {
        assertThat(lock.accepts("4321")).isTrue()
        assertThat(lock.accepts(" 4321 ")).isTrue()
    }

    @Test fun `anything else doesn't`() {
        assertThat(lock.accepts("1234")).isFalse()
        assertThat(lock.accepts("")).isFalse()
        assertThat(lock.accepts("43210")).isFalse()
    }

    @Test fun `a build without a hash can't be unlocked`() {
        assertThat(AccountantLock("").accepts("")).isFalse()
        assertThat(AccountantLock("").accepts("4321")).isFalse()
    }

    @Test fun `the hash is the same however it's written`() {
        assertThat(AccountantLock(accountantPinHash("4321").uppercase()).accepts("4321")).isTrue()
    }

    @Test fun `three tries the first time, then one`() {
        assertThat(accountantTriesAllowed(0)).isEqualTo(3)
        assertThat(accountantTriesAllowed(1)).isEqualTo(1)
        assertThat(accountantTriesAllowed(5)).isEqualTo(1)
    }
}
