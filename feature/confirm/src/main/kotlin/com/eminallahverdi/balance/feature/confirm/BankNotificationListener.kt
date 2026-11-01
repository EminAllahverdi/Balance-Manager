package com.eminallahverdi.balance.feature.confirm

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.eminallahverdi.balance.core.common.ApplicationScope
import com.eminallahverdi.balance.core.data.BankAlertRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface BankNotificationEntryPoint {
    fun bankAlerts(): BankAlertRepository

    @ApplicationScope fun scope(): CoroutineScope
}

/**
 * Hears about notifications as they're posted, so there's no polling. The system binds this once the user grants
 * notification access. [BankAlertRepository] reads a notification's text only for the apps the user picked.
 */
class BankNotificationListener : NotificationListenerService() {
    private val app by lazy { EntryPointAccessors.fromApplication(applicationContext, BankNotificationEntryPoint::class.java) }

    override fun onListenerConnected() {
        val packages = activeNotifications.orEmpty().map { it.packageName }.toSet() - packageName
        app.scope().launch { app.bankAlerts().noteApps(packages) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing || sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val posted = Instant.ofEpochMilli(sbn.notification.`when`.takeIf { it > 0 } ?: sbn.postTime)
        val source = packageManager.runCatching { getApplicationLabel(getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        app.scope().launch { app.bankAlerts().receive(sbn.packageName, source, title, text, posted) }
    }
}
