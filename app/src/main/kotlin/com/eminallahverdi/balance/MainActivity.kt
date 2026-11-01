package com.eminallahverdi.balance

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.feature.recurring.EXTRA_OPEN_RECURRING
import com.eminallahverdi.balance.feature.transaction.QuickAddViewModel
import com.eminallahverdi.balance.navigation.BalanceApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var prefs: UserPreferencesRepository

    private var openRecurring by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        publishAppShortcuts(this)
        openRecurring = savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_RECURRING, false)
        setContent {
            PreferredTheme(prefs) {
                BalanceApp(
                    onAdd = { startActivity(Intent(this, QuickAddActivity::class.java)) },
                    onTransfer = {
                        startActivity(Intent(this, QuickAddActivity::class.java).putExtra(QuickAddViewModel.ARG_KIND, TransactionKind.TRANSFER.name))
                    },
                    onOpenTransaction = { id ->
                        startActivity(Intent(this, QuickAddActivity::class.java).putExtra(QuickAddViewModel.ARG_TRANSACTION, id))
                    },
                    onAddPayment = { id ->
                        startActivity(Intent(this, QuickAddActivity::class.java).putExtra(QuickAddViewModel.ARG_PAYMENT, id))
                    },
                    openRecurring = openRecurring,
                    onRecurringOpened = { openRecurring = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_RECURRING, false)) openRecurring = true
    }
}
