package com.eminallahverdi.balance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.feature.transaction.QuickAddRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Translucent host for the quick-add sheet, so it can open over the home screen. */
@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {
    @Inject lateinit var prefs: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PreferredTheme(prefs) {
                QuickAddRoute(onClose = ::finish)
            }
        }
    }
}
