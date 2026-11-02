package com.eminallahverdi.balance.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountBalance
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.TransactionDetails

private const val TRANSFER_TILE = 0xFFAEB6C1

@Composable
private fun LoansRow(onClick: () -> Unit) {
    val c = BtTheme.colors
    BtCard(Modifier.clip(BtShapes.md).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryTile("handshake", TRANSFER_TILE)
            Text("Loans & debts", style = BtTheme.type.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = c.textSecondary)
        }
    }
}

internal val AccountType.label: String
    get() = when (this) {
        AccountType.DEBIT_CARD -> "Debit card"
        AccountType.CREDIT_CARD -> "Credit card"
        AccountType.CASH -> "Cash"
        AccountType.SAVINGS -> "Savings"
    }

@Composable
private fun ArchivedHeader(count: Int, open: Boolean, onToggle: () -> Unit) {
    val c = BtTheme.colors
    BtCard(Modifier.clip(BtShapes.md).clickable(onClick = onToggle)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryTile("inventory_2", TRANSFER_TILE)
            Text("Archived", style = BtTheme.type.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text("$count", style = BtTheme.type.caption, color = c.textSecondary)
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (open) "Hide archived accounts" else "Show archived accounts", tint = c.textSecondary)
        }
    }
}

/**
 * Every account and what's in it. [onTransfer] opens quick add on Transfer, [onOpenTransaction] one transfer to edit,
 * and [onDeleteArchived] the page that says what deleting an archived account would take with it.
 */
@Composable
fun AccountsRoute(
    onTransfer: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenLoans: () -> Unit,
    onDeleteArchived: (Long) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = BtTheme.colors
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    fun money(m: Money) = if (state.hideAmounts) MoneyFormat.HIDDEN else MoneyFormat.format(m, signed = true)

    LazyColumn(
        Modifier.fillMaxSize().background(c.bgCanvas),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Accounts", style = BtTheme.type.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                BtIconButton(Icons.Rounded.Add, "Add account", viewModel::newAccount)
            }
        }
        item {
            val count = state.included.size
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Overline(if (count == 1) "Total · 1 account included" else "Total · $count accounts included")
                Text(money(state.total), style = BtTheme.type.amountDisplay.copy(fontSize = 36.sp, lineHeight = 40.sp), color = c.textPrimary)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Transfer", Icons.Rounded.SwapHoriz, enabled = state.canTransfer, onClick = onTransfer, modifier = Modifier.weight(1f))
                ActionButton("Adjust balance", Icons.Rounded.Tune, enabled = true, onClick = viewModel::openAdjust, modifier = Modifier.weight(1f))
            }
        }
        items(state.balances, key = { it.account.id }) { balance ->
            AccountCard(balance, isDefault = balance.account.id == state.defaultAccountId, amount = money(balance.balance), vm = viewModel)
        }
        item(key = "loans") { LoansRow(onOpenLoans) }
        if (state.archived.isNotEmpty()) {
            item(key = "archived") { ArchivedHeader(state.archived.size, archivedOpen) { archivedOpen = !archivedOpen } }
            if (archivedOpen) items(state.archived, key = { "a${it.id}" }) { account -> ArchivedRow(account, viewModel, onDeleteArchived) }
        }
        if (state.transfers.isNotEmpty()) {
            item(key = "transfers") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Overline("Recent transfers", Modifier.padding(top = 8.dp, start = 4.dp))
                    BtCard {
                        Column {
                            state.transfers.forEachIndexed { i, transfer ->
                                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                                TransferRow(transfer, state.hideAmounts, onOpenTransaction)
                            }
                        }
                    }
                }
            }
        }
    }

    when (val sheet = state.sheet) {
        is AccountsSheet.Editor -> AccountEditorSheet(sheet, state, viewModel)
        is AccountsSheet.Adjust -> AdjustBalanceSheet(sheet, state, viewModel)
        null -> Unit
    }
}

/** Both weigh the same: a lime one beside a plain one read as a selected tab. */
@Composable
private fun ActionButton(text: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    Row(
        modifier
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(BtShapes.md)
            .background(c.bgSurface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.accentText, modifier = Modifier.size(20.dp))
        Text(text, style = BtTheme.type.bodyStrong, color = c.textPrimary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountCard(balance: AccountBalance, isDefault: Boolean, amount: String, vm: AccountsViewModel) {
    val c = BtTheme.colors
    val account = balance.account
    BtCard {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { vm.edit(account) }.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoryTile(account.icon, account.color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(account.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (isDefault) {
                            Text(
                                "Default",
                                style = BtTheme.type.overline.copy(letterSpacing = 0.sp),
                                color = c.accentOn,
                                modifier = Modifier.clip(BtShapes.full).background(c.accentFill).padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Text(account.type.label, style = BtTheme.type.caption, color = c.textSecondary)
                }
                Text(amount, style = BtTheme.type.amountMedium, color = c.textPrimary, maxLines = 1)
            }
            FlowRow(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchChip(
                    text = "In total",
                    icon = if (account.includeInTotal) Icons.Rounded.Check else Icons.Rounded.Remove,
                    on = account.includeInTotal,
                    onClick = { vm.setInTotal(account, !account.includeInTotal) },
                )
                SwitchChip(
                    text = if (account.showOnWidget) "On widget" else "Not on widget",
                    icon = Icons.Rounded.Widgets,
                    on = account.showOnWidget,
                    onClick = { vm.setOnWidget(account, !account.showOnWidget) },
                )
                if (!isDefault) {
                    SwitchChip(text = "Make default", icon = Icons.Rounded.StarOutline, on = true, role = Role.Button, onClick = { vm.makeDefault(account) })
                }
            }
        }
    }
}

/** A small on/off chip inside a touch target that's taller than it looks. */
@Composable
private fun SwitchChip(text: String, icon: ImageVector, on: Boolean, onClick: () -> Unit, role: Role = Role.Switch) {
    val c = BtTheme.colors
    Box(Modifier.heightIn(min = 44.dp).clickable(role = role, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .height(30.dp)
                .alpha(if (on) 1f else 0.6f)
                .clip(BtShapes.full)
                .background(c.bgSurfaceRaised)
                .border(1.dp, c.borderDefault, BtShapes.full)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
            Text(text, style = BtTheme.type.caption.copy(fontSize = 12.sp), color = c.textPrimary)
        }
    }
}

@Composable
private fun ArchivedRow(account: Account, vm: AccountsViewModel, onDelete: (Long) -> Unit) {
    val c = BtTheme.colors
    BtCard {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(account.name, style = BtTheme.type.body, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            ArchivedAction("Restore", c.accentText) { vm.restore(account) }
            ArchivedAction("Delete", c.moneyExpense) { onDelete(account.id) }
        }
    }
}

@Composable
private fun ArchivedAction(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).clip(BtShapes.sm).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BtTheme.type.label, color = color) }
}

@Composable
private fun TransferRow(transfer: TransactionDetails, hideAmounts: Boolean, onOpen: (Long) -> Unit) {
    val c = BtTheme.colors
    val t = transfer.transaction
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(t.id) }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile("swap_horiz", TRANSFER_TILE)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${transfer.account.name} → ${transfer.toAccount?.name.orEmpty()}", style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val caption = listOf(DateLabels.relative(t.date), t.note).filter { it.isNotBlank() }.joinToString(" · ")
            Text(caption, style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (hideAmounts) MoneyFormat.HIDDEN else MoneyFormat.format(t.amount), style = BtTheme.type.amountMedium, color = c.textPrimary)
    }
}
