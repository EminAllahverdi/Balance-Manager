package com.eminallahverdi.balance.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkerParameters
import com.eminallahverdi.balance.core.common.ApplicationScope
import com.eminallahverdi.balance.core.common.bookDailyWork
import com.eminallahverdi.balance.core.data.BankAlertRepository
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private const val MIDNIGHT_WORK = "widget-midnight"
private const val MIDNIGHT = 0

/** Redraws the widgets whenever their data changes, wherever the change came from, and rolls Today over at midnight. */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactions: TransactionRepository,
    private val catalog: CatalogRepository,
    private val bankAlerts: BankAlertRepository,
    private val preferences: UserPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch { context.bookDailyWork<MidnightRefreshWorker>(MIDNIGHT_WORK, MIDNIGHT, ExistingPeriodicWorkPolicy.KEEP) }
        scope.launch {
            // Its own trigger: the To confirm widget shows only this count, and the 4x1 balance shows it on its +.
            bankAlerts.waitingCount.distinctUntilChanged().drop(1).collect {
                ToConfirmWidget().updateAll(context)
                BalanceVoiceWidget().updateAll(context)
            }
        }
        scope.launch {
            // Room re-emits on every write to the table, so balances also fires for edits that leave the sums alone.
            combine(transactions.balances, catalog.categories, preferences.preferences) { _, _, _ -> }
                // The first value is what's already drawn. Each redraw keeps a job awake for about 45 s, and the process also starts for background work.
                .drop(1)
                .debounce(300.milliseconds)
                .collect {
                    BalanceWidget().updateAll(context)
                    BalanceVoiceWidget().updateAll(context)
                    QuickActionsWidget().updateAll(context)
                    TodayWidget().updateAll(context)
                }
        }
    }
}

class MidnightRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        TodayWidget().updateAll(applicationContext)
        applicationContext.bookDailyWork<MidnightRefreshWorker>(MIDNIGHT_WORK, MIDNIGHT, ExistingPeriodicWorkPolicy.UPDATE)
        return Result.success()
    }
}
