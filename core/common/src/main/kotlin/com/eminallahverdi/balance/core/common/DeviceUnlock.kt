package com.eminallahverdi.balance.core.common

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Fingerprint or face, with the phone's own PIN, pattern or password as the way in when those fail. */
private const val DEVICE_UNLOCK = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** False on a phone with no screen lock, which has nothing to prompt with. */
fun Context.canUnlockDevice(): Boolean = BiometricManager.from(this).canAuthenticate(DEVICE_UNLOCK) == BiometricManager.BIOMETRIC_SUCCESS

/** Android's own prompt. Its "Use PIN" fallback is built in, and the PIN never reaches this app. */
fun FragmentActivity.askToUnlock(title: String, onUnlocked: () -> Unit, onCancelled: () -> Unit = {}) {
    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onCancelled()
        },
    )
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(DEVICE_UNLOCK).build())
}
