package com.eminallahverdi.balance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eminallahverdi.balance.core.designsystem.theme.BalanceTheme
import com.eminallahverdi.balance.feature.transaction.QuickAddRoute
import dagger.hilt.android.AndroidEntryPoint

/** Translucent host for the quick-add sheet, so it can open over the home screen. */
@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BalanceTheme {
                QuickAddRoute(onClose = ::finish)
            }
        }
    }
}
