package com.eminallahverdi.balance.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.updateAll
import com.eminallahverdi.balance.core.common.Language
import com.eminallahverdi.balance.core.common.askToUnlock
import com.eminallahverdi.balance.core.common.canUnlockDevice
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
            val widgets = listOf(BalanceWidget(), BalanceVoiceWidget())
            try {
                for (left in seconds downTo 1) {
                    BalanceReveal.secondsLeft.value = left
                    widgets.forEach { it.updateAll(context) }
                    delay(1.seconds)
                }
            } finally {
                BalanceReveal.secondsLeft.value = 0
                withContext(NonCancellable) { widgets.forEach { it.updateAll(context) } }
                result.finish()
            }
        }
    }
}

/** The small fingerprint prompt a touch on the balance opens, over the home screen. */
class WidgetUnlockActivity : FragmentActivity() {
    // Starts in the language picked in Settings, so nothing shows in the phone's first.
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Language.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!canUnlockDevice()) {
            // A phone with no lock has nothing to prompt with. The app itself shows the balance.
            startActivity(packageManager.getLaunchIntentForPackage(packageName))
            finish()
            return
        }
        if (savedInstanceState == null) {
            askToUnlock(
                title = getString(R.string.widget_show_balance),
                onUnlocked = {
                    sendBroadcast(Intent(this, BalanceRevealReceiver::class.java))
                    finish()
                },
                onCancelled = ::finish,
            )
        }
    }
}
