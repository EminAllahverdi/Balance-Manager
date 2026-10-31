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
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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

internal val AccountType.label: String
    get() = when (this) {
        AccountType.DEBIT_CARD -> "Debit card"
        AccountType.CREDIT_CARD -> "Credit card"
        AccountType.CASH -> "Cash"
        AccountType.SAVINGS -> "Savings"
    }

/** Every account and what's in it. [onTransfer] opens quick add on Transfer, [onOpenTransaction] one transfer to edit. */
@Composable
fun AccountsRoute(onTransfer: () -> Unit, onOpenTransaction: (Long) -> Unit, viewModel: AccountsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = BtTheme.colors
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
                ActionButton("Transfer", Icons.Rounded.SwapHoriz, primary = true, enabled = state.canTransfer, onClick = onTransfer, modifier = Modifier.weight(1f))
                ActionButton("Adjust balance", Icons.Rounded.Tune, primary = false, enabled = true, onClick = viewModel::openAdjust, modifier = Modifier.weight(1f))
            }
        }
        items(state.balances, key = { it.account.id }) { balance ->
            AccountCard(balance, isDefault = balance.account.id == state.defaultAccountId, amount = money(balance.balance), vm = viewModel)
        }
        if (state.archived.isNotEmpty()) {
            item(key = "archived") { Overline("Archived", Modifier.padding(top = 8.dp, start = 4.dp)) }
            items(state.archived, key = { "a${it.id}" }) { account -> ArchivedRow(account, viewModel) }
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

@Composable
private fun ActionButton(text: String, icon: ImageVector, primary: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    val content = if (primary) c.accentOn else c.textPrimary
    Row(
        modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(BtShapes.full)
            .background(if (primary) c.accentFill else c.bgSurfaceRaised)
            .then(if (primary) Modifier else Modifier.border(1.dp, c.borderDefault, BtShapes.full))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (primary) content else c.textSecondary, modifier = Modifier.size(20.dp))
        Text(text, style = BtTheme.type.bodyStrong, color = content)
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
private fun ArchivedRow(account: Account, vm: AccountsViewModel) {
    BtCard {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(account.name, style = BtTheme.type.body, color = BtTheme.colors.textSecondary, modifier = Modifier.weight(1f))
            Box(
                Modifier.height(44.dp).clip(BtShapes.sm).clickable(role = Role.Button) { vm.restore(account) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Restore", style = BtTheme.type.label, color = BtTheme.colors.accentText) }
        }
    }
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
