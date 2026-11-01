package com.eminallahverdi.balance.feature.recurring

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkerParameters
import com.eminallahverdi.balance.core.common.ApplicationScope
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.common.bookDailyWork
import com.eminallahverdi.balance.core.data.RecurringRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.model.RecurringPayment
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

private const val DAILY_WORK = "payment-reminders"
private const val CHANNEL = "payment-reminders"
private const val NOTIFICATION_ID = 1
private const val REMINDER_MINUTE = 10 * 60

/** Tells MainActivity to open the recurring screen, when a reminder is tapped. */
const val EXTRA_OPEN_RECURRING = "openRecurring"

/** Reminds once a day about payments still missing from the list, and clears any the moment their entry appears. */
@Singleton
class PaymentReminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactions: TransactionRepository,
    private val recurring: RecurringRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch { context.bookDailyWork<PaymentReminderWorker>(DAILY_WORK, REMINDER_MINUTE, ExistingPeriodicWorkPolicy.KEEP) }
        scope.launch {
            // Room re-emits on every write to the table, so this runs after any entry is added, edited or removed.
            transactions.balances.debounce(300.milliseconds).collect { recurring.settleLogged() }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PaymentReminderEntryPoint {
    fun recurring(): RecurringRepository
}

class PaymentReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val recurring = EntryPointAccessors.fromApplication(applicationContext, PaymentReminderEntryPoint::class.java).recurring()
        recurring.settleLogged()
        showReminder(applicationContext, recurring.due())
        applicationContext.bookDailyWork<PaymentReminderWorker>(DAILY_WORK, REMINDER_MINUTE, ExistingPeriodicWorkPolicy.UPDATE)
        return Result.success()
    }
}

/** Never adds anything: the notification only sends the user to the payment, to log it themselves. */
@SuppressLint("MissingPermission") // areNotificationsEnabled covers it.
private fun showReminder(context: Context, due: List<RecurringPayment>) {
    val manager = NotificationManagerCompat.from(context)
    if (due.isEmpty() || !manager.areNotificationsEnabled()) return
    manager.createNotificationChannel(
        NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("Payment reminders").build(),
    )
    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        ?.putExtra(EXTRA_OPEN_RECURRING, true)
        ?: return
    val first = due.first()
    val notification = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification_payment)
        .setContentTitle(if (due.size == 1) "${first.name} is due" else "${due.size} payments due")
        .setContentText(if (due.size == 1) "${MoneyFormat.format(first.amountDue)} · Tap to log it" else due.joinToString(", ") { it.name })
        .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setAutoCancel(true)
        .build()
    manager.notify(NOTIFICATION_ID, notification)
}
