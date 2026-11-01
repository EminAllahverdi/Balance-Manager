package com.eminallahverdi.balance

import android.app.Application
import com.eminallahverdi.balance.feature.recurring.PaymentReminders
import com.eminallahverdi.balance.widget.WidgetRefresher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BalanceApplication : Application() {
    @Inject lateinit var seeder: FirstRunSeeder
    @Inject lateinit var widgets: WidgetRefresher
    @Inject lateinit var reminders: PaymentReminders
    @Inject lateinit var eveningReminder: EveningReminder

    override fun onCreate() {
        super.onCreate()
        seeder.seedIfEmpty()
        widgets.start()
        reminders.start()
        eveningReminder.start()
    }
}
