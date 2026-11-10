package com.eminallahverdi.balance.feature.accounts

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.eminallahverdi.balance.core.designsystem.component.FittedAmount
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountBalance
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.TransactionDetails
import kotlinx.coroutines.delay

private const val TRANSFER_TILE = 0xFFAEB6C1

@Composable
private fun PlaceRow(icon: String, title: String, caption: String?, onClick: () -> Unit) {
    val c = BtTheme.colors
    BtCard(Modifier.clip(BtShapes.md).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryTile(icon, TRANSFER_TILE)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = BtTheme.type.bodyStrong, color = c.textPrimary)
                caption?.let { Text(it, style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
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
    onSeeAllTransfers: () -> Unit,
    onOpenRecurring: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = BtTheme.colors
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    fun money(m: Money) = if (state.hideAmounts) MoneyFormat.HIDDEN else MoneyFormat.format(m, signed = true)

    Box(Modifier.fillMaxSize().background(c.bgCanvas)) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Accounts", style = BtTheme.type.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                BtIconButton(Icons.Rounded.Add, "Add account", viewModel::newAccount)
            }
        }
        item { TotalCard(state, money(state.total), onTransfer, viewModel::openAdjust) }
        items(state.balances, key = { it.account.id }) { balance ->
            AccountCard(balance, isDefault = balance.account.id == state.defaultAccountId, amount = money(balance.balance), vm = viewModel)
        }
        // What's owed and what's coming round again sit together, below the balances they come out of.
        item(key = "loans") { PlaceRow("handshake", "Loans & debts", caption = null, onOpenLoans) }
        item(key = "recurring") { PlaceRow("event_repeat", "Recurring payments", state.recurringSummary, onOpenRecurring) }
        if (state.archived.isNotEmpty()) {
            item(key = "archived") { ArchivedHeader(state.archived.size, archivedOpen) { archivedOpen = !archivedOpen } }
            if (archivedOpen) items(state.archived, key = { "a${it.id}" }) { account -> ArchivedRow(account, viewModel, onDeleteArchived) }
        }
        if (state.transfers.isNotEmpty()) {
            item(key = "transfers") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Only the last few here, so the page stays light; the rest are one tap away.
                    Row(Modifier.padding(top = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Overline("Recent transfers", Modifier.weight(1f))
                        Box(
                            Modifier.heightIn(min = 44.dp).clip(BtShapes.full).clickable(role = Role.Button, onClick = onSeeAllTransfers).padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("See all", style = BtTheme.type.label, color = c.accentText)
                        }
                    }
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
    state.defaultChange?.let { change ->
        DefaultChangeBar(change, viewModel::undoDefault, viewModel::dismissDefaultChange, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    }

    when (val sheet = state.sheet) {
        is AccountsSheet.Editor -> AccountEditorSheet(sheet, state, viewModel)
        is AccountsSheet.Adjust -> AdjustBalanceSheet(sheet, state, viewModel)
        null -> Unit
    }
}

/**
 * The total and the two things done to balances, in one box: Transfer and Adjust balance sit along its foot, split by a
 * hairline, so they read as the box's actions rather than as tabs.
 */
@Composable
private fun TotalCard(state: AccountsUiState, total: String, onTransfer: () -> Unit, onAdjust: () -> Unit) {
    val c = BtTheme.colors
    val count = state.included.size
    BtCard(shape = BtShapes.lg, bordered = true) {
        Column {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Overline(if (count == 1) "Total · 1 account" else "Total · $count accounts")
                FittedAmount(total, BtTheme.type.amountDisplay.copy(fontSize = 34.sp, lineHeight = 40.sp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                BoxAction("Transfer", Icons.Rounded.SwapHoriz, enabled = state.canTransfer, onClick = onTransfer, modifier = Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(c.borderDefault))
                BoxAction("Adjust balance", Icons.Rounded.Tune, enabled = true, onClick = onAdjust, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BoxAction(text: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    Row(
        modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.accentText, modifier = Modifier.size(20.dp))
        Text(text, style = BtTheme.type.bodyStrong, color = c.textPrimary)
    }
}

/**
 * One account: its name, what kind it is and its card's digits, and the balance. A star says which one new expenses start
 * on, and tapping another moves it there. What it counts in is set in its editor and only shown here: a small widget mark
 * when it's on the widget, and a dimmed balance with "not in the total" when it isn't counted.
 */
@Composable
private fun AccountCard(balance: AccountBalance, isDefault: Boolean, amount: String, vm: AccountsViewModel) {
    val c = BtTheme.colors
    val account = balance.account
    BtCard {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Edit ${account.name}") { vm.edit(account) }.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryTile(account.icon, account.color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(account.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val details = listOfNotNull(
                        account.type.label,
                        account.cardEnding.takeIf { it.isNotBlank() }?.let { "•••• $it" },
                        "not in the total".takeUnless { account.includeInTotal },
                    )
                    Text(details.joinToString(" · "), style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (account.showOnWidget) Icon(Icons.Rounded.Widgets, "On the widget", tint = c.textMuted, modifier = Modifier.size(14.dp))
                }
            }
            Text(amount, style = BtTheme.type.amountMedium, color = if (account.includeInTotal) c.textPrimary else c.textMuted, maxLines = 1)
            StarButton(account.name, isDefault) { vm.makeDefault(account) }
        }
    }
}

/** Filled lime on the default account, an outline on the others; it springs as it fills, so the move is seen. */
@Composable
private fun StarButton(name: String, isDefault: Boolean, onClick: () -> Unit) {
    val c = BtTheme.colors
    val scale = remember { Animatable(1f) }
    var wasDefault by remember { mutableStateOf(isDefault) }
    LaunchedEffect(isDefault) {
        if (isDefault && !wasDefault) {
            scale.snapTo(0.5f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow))
        }
        wasDefault = isDefault
    }
    val tint by animateColorAsState(if (isDefault) c.accentText else c.textMuted, label = "star")
    Box(
        Modifier
            .size(44.dp)
            .clip(BtShapes.full)
            .clickable(enabled = !isDefault, role = Role.Button, onClickLabel = "Make $name the default", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isDefault) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            if (isDefault) "$name is the default" else null,
            tint = tint,
            modifier = Modifier.size(22.dp).scale(scale.value),
        )
    }
}

/** "New expenses start on Taksit", with Undo, for a few seconds after the star moves. */
@Composable
private fun DefaultChangeBar(change: DefaultChange, onUndo: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    LaunchedEffect(change) {
        delay(DEFAULT_BAR_MS)
        onDone()
    }
    Row(
        modifier.fillMaxWidth().clip(BtShapes.md).background(c.bgSurfaceHigh).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Star, null, tint = c.accentText, modifier = Modifier.size(20.dp))
        Text("New expenses start on ${change.account.name}", style = BtTheme.type.body, color = c.textPrimary, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
        if (change.previousId != null) {
            Box(Modifier.heightIn(min = 44.dp).clip(BtShapes.full).clickable(role = Role.Button, onClick = onUndo).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text("Undo", style = BtTheme.type.label, color = c.accentText)
            }
        }
    }
}

private const val DEFAULT_BAR_MS = 4_000L

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
