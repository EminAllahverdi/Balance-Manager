package com.eminallahverdi.balance.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.R
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.feature.insights.InsightsRoute
import com.eminallahverdi.balance.feature.receipts.GroceriesRoute
import com.eminallahverdi.balance.feature.receipts.ReceiptEntry

/** Each report the tab holds. A new one adds a chip here. */
private enum class Report(@StringRes val label: Int, val icon: ImageVector) {
    SPENDING(R.string.app_spending, Icons.Rounded.BarChart),
    GROCERIES(R.string.app_groceries, Icons.Rounded.ShoppingCart),
}

/**
 * The Reports tab: every report in one place, one tap from the bar, without crowding Home. Chips across the top pick one,
 * and the one picked stays picked while the app is open.
 */
@Composable
internal fun ReportsRoute(onAddReceipt: (ReceiptEntry) -> Unit) {
    val c = BtTheme.colors
    var shown by rememberSaveable { mutableStateOf(Report.SPENDING) }
    Column(Modifier.fillMaxSize().background(c.bgCanvas)) {
        Text(stringResource(R.string.app_reports), style = BtTheme.type.titleLarge, color = c.textPrimary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Report.entries.forEach { report ->
                val on = report == shown
                BtChip(
                    text = stringResource(report.label),
                    selected = on,
                    onClick = { shown = report },
                    leading = { Icon(report.icon, null, tint = if (on) c.accentOn else c.textSecondary, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when (shown) {
                Report.SPENDING -> InsightsRoute(embedded = true)
                Report.GROCERIES -> GroceriesRoute(onBack = null, onAddExpense = onAddReceipt)
            }
        }
    }
}
