package com.eminallahverdi.balance

import android.app.Application
import com.eminallahverdi.balance.widget.WidgetRefresher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BalanceApplication : Application() {
    @Inject lateinit var seeder: FirstRunSeeder
    @Inject lateinit var widgets: WidgetRefresher

    override fun onCreate() {
        super.onCreate()
        seeder.seedIfEmpty()
        widgets.start()
    }
}
