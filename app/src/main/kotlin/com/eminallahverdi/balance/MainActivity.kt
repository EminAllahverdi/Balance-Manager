package com.eminallahverdi.balance

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.feature.transaction.QuickAddViewModel
import com.eminallahverdi.balance.navigation.BalanceApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var prefs: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
                )
            }
        }
    }
}
