package com.eminallahverdi.balance.core.data

import androidx.room.withTransaction
import com.eminallahverdi.balance.core.common.ApplicationScope
import com.eminallahverdi.balance.core.database.BalanceDatabase
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.CsvImport
import com.eminallahverdi.balance.core.model.needsOnboarding
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Whether onboarding is due, and writing what it collected. */
@Singleton
class FirstRun @Inject constructor(
    private val db: BalanceDatabase,
    private val preferences: UserPreferencesRepository,
    private val catalog: CatalogRepository,
    private val csv: CsvImporter,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /**
     * An install with data has done with onboarding, so it's marked here and never checked again. That covers updating
     * from before onboarding existed and restoring a backup made back then.
     */
    suspend fun isNeeded(): Boolean {
        val settings = preferences.current()
        if (settings.onboardingDone) return false
        val needed = needsOnboarding(done = false, hasData = db.maintenanceDao().hasData())
        if (!needed) preferences.setOnboardingDone()
        return needed
    }

    /** Every start marks an existing install, so background work never sees it as a fresh one. */
    fun settle() {
        scope.launch { isNeeded() }
    }

    /**
     * All or nothing: a file that fails halfway leaves a fresh install, and the user can try again. Backups only turn on
     * here, once there's something to back up, so leaving halfway never sends an empty app to Drive.
     */
    suspend fun finish(template: StarterTemplate, ownerName: String, import: CsvImport?, backUp: Boolean = false) {
        db.withTransaction {
            catalog.applyTemplate(template)
            import?.let { csv.write(it) }
        }
        preferences.setOnboardingDone(ownerName)
        if (backUp) preferences.setDriveBackup(true)
    }
}
