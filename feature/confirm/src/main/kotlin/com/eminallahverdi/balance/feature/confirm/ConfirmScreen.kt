package com.eminallahverdi.balance.feature.confirm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.BtSwitch
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.TransactionKind
import java.time.ZoneId

/** Ready-made entries read from bank notifications. [onEdit] opens one in quick add. */
@Composable
fun ConfirmRoute(onBack: () -> Unit, onEdit: (Long) -> Unit, viewModel: ConfirmViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = BtTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().background(c.bgCanvas).navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Text("To confirm", style = BtTheme.type.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                if (state.waiting > 0) BtChip("${state.waiting} new", selected = false, onClick = {})
            }
        }
        item {
            Text(hint(state), style = BtTheme.type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
        }
        items(state.rows, key = { it.item.entry.id }) { row -> EntryCard(row, viewModel, onEdit) }
        if (state.listening) item(key = "auto") { AutoConfirmRow(state.autoConfirm, viewModel::setAutoConfirm) }
    }
    if (state.askingAutoConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::declineAutoConfirm,
            title = { Text("Add known merchants without asking?") },
            text = {
                Text(
                    "Payments from merchants you've already confirmed will be added for you, with the same category, " +
                        "person and account as last time. New merchants still wait here. You can turn this off at any time.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::acceptAutoConfirm) { Text("Turn on") } },
            dismissButton = { TextButton(onClick = viewModel::declineAutoConfirm) { Text("Cancel") } },
        )
    }
}

private fun hint(state: ConfirmUiState): String = when {
    !state.listening -> "Turn on Bank notifications in Settings, and payments from your bank apps will appear here, ready to add."
    state.rows.isEmpty() -> "Nothing waiting. New payments from your bank apps will appear here, ready to add."
    else -> "Read from your bank notifications. The category, person and account come from what you picked last time. Anything marked Suggested is a best guess, so check it first."
}

@Composable
private fun EntryCard(row: ConfirmRow, vm: ConfirmViewModel, onEdit: (Long) -> Unit) {
    val c = BtTheme.colors
    val entry = row.item.entry
    val tileCategory = row.category ?: row.parent
    val posted = entry.postedAt.atZone(ZoneId.systemDefault())
    val income = entry.kind == TransactionKind.INCOME
    BtCard(if (row.isWaiting) Modifier else Modifier.alpha(0.55f), bordered = row.isWaiting) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Notifications, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                Text(
                    "${entry.source} · ${DateLabels.timeOfDay(posted.hour * 60 + posted.minute)}",
                    style = BtTheme.type.caption,
                    color = c.textSecondary,
                    maxLines = 1,
                )
                Text(
                    entry.merchant,
                    style = BtTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryTile(tileCategory?.icon ?: "notifications", tileCategory?.color ?: 0xFF8A94A3)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.title, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.person?.takeIf { row.category != null }?.let { PersonDot(it.color, 7.dp) }
                        Text(row.caption, style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(
                    (if (income) "+" else "") + MoneyFormat.format(entry.amount),
                    style = BtTheme.type.amountMedium,
                    color = if (income) c.moneyIncome else c.textPrimary,
                )
            }
            if (row.isWaiting) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardButton("Edit", Icons.Rounded.Edit, false, { onEdit(entry.id) }, Modifier.weight(1f))
                    CardButton("Skip", Icons.Rounded.Close, false, { vm.skip(entry.id) }, Modifier.weight(1f))
                    if (row.canConfirm) {
                        CardButton("Confirm", Icons.Rounded.Check, true, { vm.confirm(entry.id) }, Modifier.weight(1.4f))
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = c.moneyIncome, modifier = Modifier.size(18.dp))
                    Text("Added", style = BtTheme.type.caption, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun CardButton(text: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val tint = if (primary) BtTheme.colors.accentOn else BtTheme.colors.textSecondary
    BtChip(text, selected = primary, onClick = onClick, modifier = modifier, leading = { Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp)) })
}

@Composable
private fun AutoConfirmRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val c = BtTheme.colors
    BtCard {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Bolt, null, tint = c.accentText, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Auto-confirm known merchants", style = BtTheme.type.bodyStrong, color = c.textPrimary)
                Text("Add merchants you've confirmed before, without asking", style = BtTheme.type.caption, color = c.textSecondary)
            }
            BtSwitch(enabled, onChange)
        }
    }
}
