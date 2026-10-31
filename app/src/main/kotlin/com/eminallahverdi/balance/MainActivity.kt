package com.eminallahverdi.balance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.eminallahverdi.balance.core.designsystem.theme.BalanceTheme
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BalanceTheme {
                Box(Modifier.fillMaxSize().background(BtTheme.colors.bgCanvas), contentAlignment = Alignment.Center) {
                    Text("Balance Manager", style = BtTheme.type.titleLarge, color = BtTheme.colors.textPrimary)
                }
            }
        }
    }
}
