package com.eminallahverdi.balance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.eminallahverdi.balance.core.common.Language
import com.eminallahverdi.balance.core.data.FirstRun
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.feature.transaction.QuickAddRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Translucent host for the quick-add sheet, so it can open over the home screen. */
@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {
    // Starts in the language picked in Settings, so nothing shows in the phone's first.
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Language.wrap(newBase))

    @Inject lateinit var prefs: UserPreferencesRepository
    @Inject lateinit var firstRun: FirstRun

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A widget or the Quick Settings tile can get here on a fresh install, before there's anything to add to.
        lifecycleScope.launch {
            if (firstRun.isNeeded()) {
                startActivity(Intent(this@QuickAddActivity, MainActivity::class.java))
                finish()
            } else {
                setContent {
                    PreferredTheme(prefs) {
                        QuickAddRoute(
                            onClose = ::finish,
                            onVoice = {
                                startActivity(Intent(this@QuickAddActivity, VoiceEntryActivity::class.java))
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }
}
