package com.eminallahverdi.balance

import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.askToUnlock
import com.eminallahverdi.balance.core.common.canUnlockDevice
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

/** A quick look at another app, or Android's own PIN screen, shouldn't lock the app again. */
private const val GRACE_MILLIS = 30_000L

/** Whether the app is locked right now. It starts locked, and in memory on purpose: a new process has to unlock again. */
@Singleton
class AppLock @Inject constructor() {
    var isLocked by mutableStateOf(true)
        private set

    private var leftAt = 0L

    fun onLeft() {
        leftAt = SystemClock.elapsedRealtime()
    }

    fun onReturned() {
        if (leftAt != 0L && SystemClock.elapsedRealtime() - leftAt >= GRACE_MILLIS) isLocked = true
    }

    fun unlock() {
        isLocked = false
        leftAt = 0L
    }
}

/**
 * Covers [content] while the app is locked. It stays composed underneath, so unlocking returns to the same screen.
 * Quick add stays outside this: it shows no amounts, and adding an expense has to stay fast.
 */
@Composable
fun AppLockGate(prefs: UserPreferencesRepository, lock: AppLock, content: @Composable () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val setting = remember(prefs) { prefs.preferences.map<_, Boolean?> { it.appLockEnabled } }
    val enabled by setting.collectAsStateWithLifecycle(null)
    LaunchedEffect(enabled) {
        // Off means nothing to unlock, or turning it on would lock the screen the user is on.
        if (enabled == false || !activity.canUnlockDevice()) lock.unlock()
        // Recents would otherwise show a picture of the last screen, balances included.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity.setRecentsScreenshotEnabled(enabled == false)
    }
    Box(Modifier.fillMaxSize()) {
        content()
        when {
            // Until the setting is read, so a locked app never flashes what's behind it.
            enabled == null -> Box(Modifier.fillMaxSize().background(BtTheme.colors.bgCanvas))
            enabled == true && lock.isLocked -> {
                val unlock = { activity.askToUnlock("Unlock Cibim", lock::unlock) }
                LaunchedEffect(Unit) { unlock() }
                LockScreen(onUnlock = unlock, onBack = { activity.moveTaskToBack(true) })
            }
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit, onBack: () -> Unit) {
    val c = BtTheme.colors
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .clickable(interactionSource = null, indication = null) {}
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(c.bgSurfaceRaised), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Lock, null, tint = c.textSecondary, modifier = Modifier.size(32.dp))
        }
        Text("Cibim is locked", style = BtTheme.type.titleLarge, color = c.textPrimary)
        Text("Use your fingerprint or your phone's PIN", style = BtTheme.type.body, color = c.textSecondary, modifier = Modifier.padding(bottom = 12.dp))
        PrimaryButton("Unlock", onUnlock)
    }
}
