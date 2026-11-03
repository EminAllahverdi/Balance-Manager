package com.eminallahverdi.balance

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.eminallahverdi.balance.core.data.FirstRun
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.SpokenLanguage
import com.eminallahverdi.balance.feature.transaction.QuickAddViewModel
import com.eminallahverdi.balance.feature.transaction.VoiceCaptureRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Translucent, like quick add: listens over the home screen, then hands what was said to quick add to check and save. */
@AndroidEntryPoint
class VoiceEntryActivity : ComponentActivity() {
    @Inject lateinit var prefs: UserPreferencesRepository
    @Inject lateinit var firstRun: FirstRun

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A debug build takes a sentence from adb, so the whole way to quick add can be tried without a microphone.
        val typed = if (BuildConfig.DEBUG) intent.getStringExtra(EXTRA_HEARD) else null
        lifecycleScope.launch {
            when {
                firstRun.isNeeded() -> {
                    startActivity(Intent(this@VoiceEntryActivity, MainActivity::class.java))
                    finish()
                }
                typed != null -> openQuickAdd(typed, SpokenLanguage.entries.firstOrNull { it.tag == intent.getStringExtra(EXTRA_LANGUAGE) } ?: SpokenLanguage.ENGLISH)
                else -> setContent {
                    PreferredTheme(prefs) {
                        VoiceCaptureRoute(onHeard = ::openQuickAdd, onType = { openQuickAdd(null, null) }, onClose = ::finish)
                    }
                }
            }
        }
    }

    private fun openQuickAdd(heard: String?, language: SpokenLanguage?) {
        val intent = Intent(this, QuickAddActivity::class.java)
        if (heard != null && language != null) intent.putExtra(QuickAddViewModel.ARG_SPOKEN, heard).putExtra(QuickAddViewModel.ARG_SPOKEN_LANGUAGE, language.tag)
        startActivity(intent)
        finish()
    }

    private companion object {
        const val EXTRA_HEARD = "heard"
        const val EXTRA_LANGUAGE = "language"
    }
}
