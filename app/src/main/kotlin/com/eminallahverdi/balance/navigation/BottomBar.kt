package com.eminallahverdi.balance.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme

enum class TopLevelTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val route: Any) {
    HOME("Home", Icons.Outlined.Home, Icons.Rounded.Home, HomeDestination),
    HISTORY("History", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Rounded.ReceiptLong, HistoryDestination),
    INSIGHTS("Insights", Icons.Outlined.Insights, Icons.Rounded.Insights, InsightsDestination),
    ACCOUNTS("Accounts", Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet, AccountsDestination),
}

/** Two tabs, the centre lime add button, two tabs. The add button rises 6 dp above the bar. */
@Composable
fun BottomBar(isSelected: (TopLevelTab) -> Boolean, onSelect: (TopLevelTab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    val (left, right) = TopLevelTab.entries.chunked(2).let { it[0] to it[1] }
    Column(modifier.fillMaxWidth().background(c.bgSurface).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
        Row(
            Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            left.forEach { TabItem(it, isSelected(it), onSelect) }
            AddButton(onAdd)
            right.forEach { TabItem(it, isSelected(it), onSelect) }
        }
    }
}

@Composable
private fun TabItem(tab: TopLevelTab, selected: Boolean, onSelect: (TopLevelTab) -> Unit) {
    val c = BtTheme.colors
    val tint = if (selected) c.textPrimary else c.textMuted
    Column(
        Modifier.width(64.dp).clip(BtShapes.sm).clickable(role = Role.Tab) { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.clip(BtShapes.full).background(if (selected) c.bgSurfaceHigh else Color.Transparent).padding(horizontal = 16.dp, vertical = 4.dp)) {
            Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(tab.label, style = BtTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = tint)
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val c = BtTheme.colors
    Box(
        Modifier
            .offset(y = (-14).dp)
            .size(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.accentFill)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "Add transaction", tint = c.accentOn, modifier = Modifier.size(32.dp))
    }
}
