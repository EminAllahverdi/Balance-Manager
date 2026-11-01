package com.eminallahverdi.balance.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eminallahverdi.balance.core.common.ApplicationScope
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

private const val MIDNIGHT_WORK = "widget-midnight"

/** Redraws the widgets whenever their data changes, wherever the change came from, and rolls Today over at midnight. */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactions: TransactionRepository,
    private val catalog: CatalogRepository,
    private val preferences: UserPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        scheduleMidnight()
        scope.launch {
            // Room re-emits on every write to the table, so balances also fires for edits that leave the sums alone.
            combine(transactions.balances, catalog.categories, preferences.preferences) { _, _, _ -> }
                .debounce(300.milliseconds)
                .collect {
                    BalanceWidget().updateAll(context)
                    QuickActionsWidget().updateAll(context)
                    TodayWidget().updateAll(context)
                }
        }
    }

    private fun scheduleMidnight() {
        val now = LocalDateTime.now()
        val request = PeriodicWorkRequestBuilder<MidnightRefreshWorker>(Duration.ofDays(1))
            .setInitialDelay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(MIDNIGHT_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class MidnightRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        TodayWidget().updateAll(applicationContext)
        return Result.success()
    }
}
