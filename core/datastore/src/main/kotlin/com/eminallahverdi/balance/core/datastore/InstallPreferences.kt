package com.eminallahverdi.balance.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.installStore: DataStore<Preferences> by preferencesDataStore(name = "install_prefs")

/**
 * Facts about this install rather than the user's data. A backup holds only `user_prefs`, so nothing here comes back with
 * a restore: Android's permissions belong to the install, and a new phone has to be asked again.
 */
@Singleton
class InstallPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val notificationsAskedKey = booleanPreferencesKey("notifications_asked")

    val notificationsAsked: Flow<Boolean> = context.installStore.data.map { it[notificationsAskedKey] ?: false }

    suspend fun setNotificationsAsked() { context.installStore.edit { it[notificationsAskedKey] = true } }
}
