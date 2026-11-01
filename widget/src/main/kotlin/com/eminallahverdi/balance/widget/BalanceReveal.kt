package com.eminallahverdi.balance.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.updateAll
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Seconds the balance has left on screen. In memory on purpose: if the process dies mid-reveal, the next draw is hidden. */
internal object BalanceReveal {
    val secondsLeft = MutableStateFlow(0)
}

/** Shows the balance for the chosen time, then hides it again. */
class BalanceRevealReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val data = context.widgetData
        data.scope().launch {
            val seconds = data.preferences().current().widgetRevealSeconds
            if (!BalanceReveal.secondsLeft.compareAndSet(0, seconds)) {
                result.finish()
                return@launch
            }
            val widget = BalanceWidget()
            try {
                for (left in seconds downTo 1) {
                    BalanceReveal.secondsLeft.value = left
                    widget.updateAll(context)
                    delay(1.seconds)
                }
            } finally {
                BalanceReveal.secondsLeft.value = 0
                withContext(NonCancellable) { widget.updateAll(context) }
                result.finish()
            }
        }
    }
}

/** The small fingerprint prompt a touch on the balance opens, over the home screen. */
class WidgetUnlockActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
            // A phone with no lock has nothing to prompt with. The app itself shows the balance.
            startActivity(packageManager.getLaunchIntentForPackage(packageName))
            finish()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    sendBroadcast(Intent(this@WidgetUnlockActivity, BalanceRevealReceiver::class.java))
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = finish()
            },
        )
        if (savedInstanceState == null) {
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Show balance").setAllowedAuthenticators(AUTHENTICATORS).build())
        }
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}
