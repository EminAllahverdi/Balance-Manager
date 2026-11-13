package com.eminallahverdi.balance.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.R
import com.eminallahverdi.balance.core.designsystem.component.ScrollingTabs
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.feature.insights.InsightsRoute
import com.eminallahverdi.balance.feature.receipts.GroceriesRoute
import com.eminallahverdi.balance.feature.receipts.ReceiptEntry

/** Each report the tab holds, in the order of its tabs. A new one adds a tab here, and the row scrolls once they don't fit. */
private enum class Report(@StringRes val label: Int) {
    SPENDING(R.string.app_spending),
    GROCERIES(R.string.app_groceries),
}

/**
 * The Reports tab: every report in one place, one tap from the bar, without crowding Home. A row of tabs under the title
 * picks one, and the one picked stays picked while the app is open. Each report keeps its period in its own first card, so
 * the top holds one title and one control. [onOpenCategory] opens a category's entries in a period a report showed.
 */
@Composable
internal fun ReportsRoute(onAddReceipt: (ReceiptEntry) -> Unit, onOpenCategory: (categoryId: Long, range: DateRange) -> Unit) {
    val c = BtTheme.colors
    var shown by rememberSaveable { mutableStateOf(Report.SPENDING) }
    Column(Modifier.fillMaxSize().background(c.bgCanvas)) {
        Text(stringResource(R.string.app_reports), style = BtTheme.type.titleLarge, color = c.textPrimary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        ScrollingTabs(
            options = Report.entries.map { stringResource(it.label) },
            selected = shown.ordinal,
            onSelect = { shown = Report.entries[it] },
            modifier = Modifier.padding(top = 4.dp),
        )
        Box(Modifier.weight(1f)) {
            when (shown) {
                Report.SPENDING -> InsightsRoute(onOpenCategory)
                Report.GROCERIES -> GroceriesRoute(onAddExpense = onAddReceipt)
            }
        }
    }
}
