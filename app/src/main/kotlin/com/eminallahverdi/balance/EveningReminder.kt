package com.eminallahverdi.balance

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eminallahverdi.balance.core.common.ApplicationScope
import com.eminallahverdi.balance.core.common.Language
import com.eminallahverdi.balance.core.common.bookDailyWork
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.core.model.isReminderTimeReached
import com.eminallahverdi.balance.feature.transaction.QuickAddViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch

private const val DAILY_WORK = "evening-reminder"
private const val CHANNEL = "evening-reminder"
private const val NOTIFICATION_ID = 2

/** Asks each evening, at the time set in Settings, if nothing was added that day. Settings only changes the preference, and this follows it. */
@Singleton
class EveningReminder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val transactions: TransactionRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            preferences.preferences
                .map { it.eveningReminderEnabled to it.eveningReminderMinute }
                .distinctUntilChanged()
                .withIndex()
                .collect { (index, setting) ->
                    val (enabled, minute) = setting
                    when {
                        !enabled -> WorkManager.getInstance(context).cancelUniqueWork(DAILY_WORK)
                        index == 0 -> context.bookDailyWork<EveningReminderWorker>(DAILY_WORK, minute, ExistingPeriodicWorkPolicy.KEEP)
                        else -> context.bookDailyWork<EveningReminderWorker>(DAILY_WORK, minute, ExistingPeriodicWorkPolicy.UPDATE)
                    }
                }
        }
        scope.launch {
            // Room re-emits on every write to the table, so an entry added any way at all answers the question.
            transactions.balances.drop(1).collect { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface EveningReminderEntryPoint {
    fun preferences(): UserPreferencesRepository
    fun transactions(): TransactionRepository
}

class EveningReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = EntryPointAccessors.fromApplication(applicationContext, EveningReminderEntryPoint::class.java)
        val settings = app.preferences().current()
        if (!settings.eveningReminderEnabled) return Result.success()
        val minute = settings.eveningReminderMinute
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        // Still booking tomorrow's run: someone who hasn't finished onboarding has nothing to be reminded about yet.
        if (settings.onboardingDone && isReminderTimeReached(now, minute) && app.transactions().observeDetails(DateRange(today, today)).first().isEmpty()) {
            showReminder(applicationContext)
        }
        applicationContext.bookDailyWork<EveningReminderWorker>(DAILY_WORK, minute, ExistingPeriodicWorkPolicy.UPDATE)
        return Result.success()
    }
}

/** Never adds anything: the notification only opens quick add, for the user to log it themselves. */
@SuppressLint("MissingPermission") // areNotificationsEnabled covers it.
private fun showReminder(context: Context) {
    val texts = Language.wrap(context)
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    manager.createNotificationChannel(
        NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(texts.getString(R.string.app_evening_reminder)).build(),
    )
    val add = Intent(context, QuickAddActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(QuickAddViewModel.ARG_KIND, TransactionKind.EXPENSE.name)
    val notification = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification_evening)
        .setContentTitle(texts.getString(R.string.app_nothing_added_today))
        .setContentText(texts.getString(R.string.app_tap_to_add_spent))
        .setContentIntent(PendingIntent.getActivity(context, 0, add, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setAutoCancel(true)
        .build()
    manager.notify(NOTIFICATION_ID, notification)
}
