package com.eminallahverdi.balance

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eminallahverdi.balance.core.designsystem.theme.BalanceTheme
import com.eminallahverdi.balance.navigation.BalanceApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BalanceTheme {
                BalanceApp(onAdd = { startActivity(Intent(this, QuickAddActivity::class.java)) })
            }
        }
    }
}
