package com.eminallahverdi.balance.core.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eminallahverdi.balance.core.common.ApplicationScope
import com.eminallahverdi.balance.core.common.bookDailyWork
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch

private const val NIGHTLY_WORK = "drive-backup"
private const val NIGHT_MINUTE = 3 * 60

/** Settings only changes the preferences, and this follows them. */
@Singleton
class NightlyBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            preferences.preferences
                .map { it.driveBackupEnabled to it.backupOnMobileData }
                .distinctUntilChanged()
                .withIndex()
                .collect { (index, setting) ->
                    val (enabled, mobileData) = setting
                    when {
                        !enabled -> WorkManager.getInstance(context).cancelUniqueWork(NIGHTLY_WORK)
                        index == 0 -> context.book(mobileData, ExistingPeriodicWorkPolicy.KEEP)
                        else -> context.book(mobileData, ExistingPeriodicWorkPolicy.UPDATE)
                    }
                }
        }
    }
}

/** With no network at night it runs as soon as there is one, which is why a missed night can still heal by itself. */
private suspend fun Context.book(mobileData: Boolean, policy: ExistingPeriodicWorkPolicy) {
    val network = if (mobileData) NetworkType.CONNECTED else NetworkType.UNMETERED
    bookDailyWork<NightlyBackupWorker>(NIGHTLY_WORK, NIGHT_MINUTE, policy, Constraints.Builder().setRequiredNetworkType(network).build())
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface NightlyBackupEntryPoint {
    fun preferences(): UserPreferencesRepository
    fun backups(): DriveBackups
}

class NightlyBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = EntryPointAccessors.fromApplication(applicationContext, NightlyBackupEntryPoint::class.java)
        val settings = app.preferences().current()
        if (!settings.driveBackupEnabled) return Result.success()
        app.backups().backUp()
        applicationContext.book(settings.backupOnMobileData, ExistingPeriodicWorkPolicy.UPDATE)
        return Result.success()
    }
}
