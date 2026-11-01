package com.eminallahverdi.balance

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eminallahverdi.balance.core.data.EXTRA_OPEN_CONFIRM
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.feature.recurring.EXTRA_OPEN_RECURRING
import com.eminallahverdi.balance.feature.transaction.QuickAddViewModel
import com.eminallahverdi.balance.navigation.BalanceApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var prefs: UserPreferencesRepository
    @Inject lateinit var appLock: AppLock

    private var openRecurring by mutableStateOf(false)
    private var openConfirm by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        publishAppShortcuts(this)
        openRecurring = savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_RECURRING, false)
        openConfirm = savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_CONFIRM, false)
        setContent {
            PreferredTheme(prefs) {
                AppLockGate(prefs, appLock) {
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
                        onEditPending = { id ->
                            startActivity(Intent(this, QuickAddActivity::class.java).putExtra(QuickAddViewModel.ARG_PENDING, id))
                        },
                        openRecurring = openRecurring,
                        onRecurringOpened = { openRecurring = false },
                        openConfirm = openConfirm,
                        onConfirmOpened = { openConfirm = false },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onReturned()
    }

    override fun onStop() {
        super.onStop()
        appLock.onLeft()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_RECURRING, false)) openRecurring = true
        if (intent.getBooleanExtra(EXTRA_OPEN_CONFIRM, false)) openConfirm = true
    }
}
