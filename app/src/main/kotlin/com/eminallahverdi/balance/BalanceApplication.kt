package com.eminallahverdi.balance

import android.app.Application
import com.eminallahverdi.balance.core.backup.NightlyBackup
import com.eminallahverdi.balance.core.backup.applyPendingRestore
import com.eminallahverdi.balance.core.data.FirstRun
import com.eminallahverdi.balance.feature.recurring.PaymentReminders
import com.eminallahverdi.balance.widget.WidgetRefresher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BalanceApplication : Application() {
    @Inject lateinit var firstRun: FirstRun
    @Inject lateinit var widgets: WidgetRefresher
    @Inject lateinit var reminders: PaymentReminders
    @Inject lateinit var eveningReminder: EveningReminder
    @Inject lateinit var nightlyBackup: NightlyBackup

    override fun onCreate() {
        // Before Hilt: nothing may have the database or settings open while a restore swaps them in.
        applyPendingRestore(this)
        super.onCreate()
        firstRun.settle()
        widgets.start()
        reminders.start()
        eveningReminder.start()
        nightlyBackup.start()
    }
}
