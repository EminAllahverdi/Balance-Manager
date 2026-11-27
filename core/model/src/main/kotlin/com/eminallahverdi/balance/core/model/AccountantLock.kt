package com.eminallahverdi.balance.core.model

import java.security.MessageDigest

/** Mixed into the PIN before hashing, so the hash isn't one a lookup table already has. It isn't secret. */
private const val SALT = "cibim-accountant-1:"

/** Taps on the version number in Settings that bring up the PIN box. */
const val ACCOUNTANT_UNLOCK_TAPS = 7

/**
 * The lock on the owner's accountant copy. The repo and its builds are public, so the PIN is never in them: CI builds in
 * only its hash, from a secret, and a build without one can't be unlocked at all.
 */
data class AccountantLock(val pinHash: String) {
    /** Whether [typed] is the PIN. Spaces around it don't count. */
    fun accepts(typed: String): Boolean = pinHash.isNotBlank() && accountantPinHash(typed) == pinHash.trim().lowercase()
}

/** The hash CI is given as a secret: run with the PIN to make it. */
fun accountantPinHash(pin: String): String =
    MessageDigest.getInstance("SHA-256").digest((SALT + pin.trim()).toByteArray()).joinToString("") { "%02x".format(it) }

/**
 * Tries in one go at the PIN box. The first time three; after a round has been used up, only one, and each wrong round
 * sends the user out to Home to start again with seven taps, so guessing takes a long time.
 */
fun accountantTriesAllowed(failedRounds: Int): Int = if (failedRounds == 0) 3 else 1
