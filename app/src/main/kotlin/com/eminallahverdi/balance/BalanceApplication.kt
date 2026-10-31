package com.eminallahverdi.balance

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BalanceApplication : Application() {
    @Inject lateinit var seeder: FirstRunSeeder

    override fun onCreate() {
        super.onCreate()
        seeder.seedIfEmpty()
    }
}
