package com.eminallahverdi.balance.feature.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.DebtDirection
import com.eminallahverdi.balance.core.model.DebtType
import com.eminallahverdi.balance.core.model.OwedTo
import com.eminallahverdi.balance.core.model.evenShare

/** Grey, like every debt's tile elsewhere: a debt isn't a category, so it doesn't borrow one's colour. */
private const val DEBT_TILE = 0xFF8A94A3

/** The four new kinds, in the order the tab shows them. */
private val NEW_DEBTS = listOf(DebtType.PAID_FOR_OTHERS, DebtType.PAID_FOR_ME, DebtType.LENT, DebtType.BORROWED)

internal val DebtType.label: String
    get() = when (this) {
        DebtType.PAID_FOR_OTHERS -> "I paid for others"
        DebtType.PAID_FOR_ME -> "They paid for me"
        DebtType.LENT -> "I lent"
        DebtType.BORROWED -> "I borrowed"
        DebtType.PAID_BACK -> "Paid back"
    }

internal val DebtType.icon: String
    get() = when (this) {
        DebtType.PAID_FOR_OTHERS -> "groups"
        DebtType.PAID_FOR_ME -> "volunteer_activism"
        DebtType.LENT -> "call_made"
        DebtType.BORROWED -> "call_received"
        DebtType.PAID_BACK -> "handshake"
    }

/** Payments a loan between people is paid back in: once, or monthly over a few. */
private val PARTS = listOf<Int?>(null, 2, 3, 6, 12)

/**
 * The Debt tab: start a new one of the four kinds, or pay someone back, or say they paid. People are one line each, with
 * what's left between you, so a payback is two taps and the amount.
 */
@Composable
internal fun DebtStep(state: QuickAddUiState, vm: QuickAddViewModel, onVoice: (() -> Unit)?, modifier: Modifier) {
    val c = BtTheme.colors
    Column(modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { KindToggle(state, vm) }
            onVoice?.let { BtIconButton(Icons.Rounded.Mic, "Add by voice", it, Modifier.padding(end = 16.dp)) }
        }
        Overline("New", Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NEW_DEBTS.forEach { type -> DebtKindCell(type, Modifier.weight(1f)) { vm.pickDebt(type) } }
        }
        val owed = state.owedLines
        if (owed.isNotEmpty()) {
            Overline("Paid back", Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp))
            BtCard(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp), shape = BtShapes.md) {
                Column {
                    owed.forEachIndexed { i, line ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                        OwedRow(line) { vm.payBack(line) }
                    }
                }
            }
        } else {
            Text(
                "Anyone you owe, or who owes you, shows here to pay back in two taps.",
                style = BtTheme.type.caption,
                color = c.textMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun DebtKindCell(type: DebtType, modifier: Modifier, onClick: () -> Unit) {
    val c = BtTheme.colors
    Column(modifier.clip(BtShapes.md).clickable(role = Role.Button, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.padding(3.dp)) { CategoryTile(type.icon, DEBT_TILE, size = 60.dp, iconSize = 28.dp, corner = 18.dp) }
        Text(type.label, style = BtTheme.type.caption.copy(lineHeight = 15.sp), color = c.textSecondary, textAlign = TextAlign.Center, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Someone and what's left between you, with Pay when it's the user who owes and Received when it's them. */
@Composable
private fun OwedRow(line: OwedTo, onClick: () -> Unit) {
    val c = BtTheme.colors
    val iOwe = line.direction == DebtDirection.I_OWE
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(BtShapes.full).background(c.bgSurfaceHigh), contentAlignment = Alignment.Center) {
            Text(line.name.take(1).uppercase(), style = BtTheme.type.bodyStrong, color = c.textPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (iOwe) "I owe" else "Owes me", style = BtTheme.type.caption, color = if (iOwe) c.moneyExpense else c.moneyIncome)
        }
        Text(MoneyFormat.format(line.left), style = BtTheme.type.amountSmall, color = c.textPrimary)
        BtChip(if (iOwe) "Pay" else "Received", selected = false, onClick = onClick)
    }
}

/**
 * The top of a debt's page: its kind, which taps back to the Debt tab, and the account money leaves or comes into. A
 * share someone paid for you moves no money until it's paid back, so it has no account here.
 */
@Composable
internal fun DebtHeader(state: QuickAddUiState, vm: QuickAddViewModel) {
    val c = BtTheme.colors
    val debt = state.debt ?: return
    val owed = debt.owed
    val title = when {
        owed == null -> debt.type.label
        owed.direction == DebtDirection.I_OWE -> "Pay back ${owed.name}"
        else -> "${owed.name} paid back"
    }
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 44.dp).clip(BtShapes.md).clickable(role = Role.Button, onClickLabel = "Pick another kind", onClick = vm::showDebtKinds),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CategoryTile(debt.type.icon, DEBT_TILE)
            Text(title, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Rounded.ExpandMore, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
        if (debt.type != DebtType.PAID_FOR_ME) {
            val into = debt.type == DebtType.BORROWED || (owed != null && owed.direction == DebtDirection.OWED_TO_ME)
            AccountPicker(label = if (into) "Into" else "From", accounts = state.accounts, selected = state.account, onSelect = vm::selectAccount)
        }
    }
}

/** A bill or a share of one keeps the category page's header; this line under it says which of the two it is. */
@Composable
internal fun DebtKindLine(state: QuickAddUiState, vm: QuickAddViewModel) {
    val c = BtTheme.colors
    val debt = state.debt ?: return
    Row(
        Modifier.padding(start = 16.dp, top = 4.dp).heightIn(min = 36.dp).clip(BtShapes.full).clickable(role = Role.Button, onClickLabel = "Pick another kind", onClick = vm::showDebtKinds).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CategoryTile(debt.type.icon, DEBT_TILE, size = 24.dp, iconSize = 14.dp, corner = 8.dp)
        Text(debt.type.label, style = BtTheme.type.label, color = c.textSecondary)
        Icon(Icons.Rounded.ExpandMore, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
    }
}

/** What saving will do with the money, in plain words, under the amount. */
@Composable
internal fun DebtCaption(state: QuickAddUiState) {
    val debt = state.debt ?: return
    val amount = state.amount
    val who = debt.who.trim().ifBlank { "They" }
    val account = state.account?.name.orEmpty()
    val text = when (debt.type) {
        DebtType.LENT -> "Leaves $account, and isn't spending." + (amount?.let { " $who owes you ${MoneyFormat.format(it)}." }.orEmpty())
        DebtType.BORROWED -> "Comes into $account, and isn't income."
        DebtType.PAID_FOR_ME -> "Nothing moves now. It becomes spending in this category when you pay them back."
        DebtType.PAID_FOR_OTHERS -> {
            val share = debt.share
            when {
                amount == null || share == null -> "The whole bill leaves $account. Your share is spending, and what they send back isn't income."
                share >= amount -> "Your share can't be the whole bill. Add it as an expense instead."
                share.isZero -> "The whole bill leaves $account. Whatever isn't paid back can be marked as yours later."
                else -> "${MoneyFormat.format(share)} is spending now, and they owe you ${MoneyFormat.format(amount - share)}."
            }
        }
        DebtType.PAID_BACK -> {
            val owed = debt.owed ?: return
            val over = amount != null && amount > owed.left
            when {
                over -> "That's more than the ${MoneyFormat.format(owed.left)} left."
                owed.direction == DebtDirection.I_OWE -> "${MoneyFormat.format(owed.left)} left to pay. The oldest is settled first."
                else -> "${MoneyFormat.format(owed.left)} left to come back, into $account. It isn't income."
            }
        }
    }
    val warn = debt.type == DebtType.PAID_BACK && amount != null && debt.owed != null && amount > debt.owed.left ||
        debt.type == DebtType.PAID_FOR_OTHERS && amount != null && (debt.share ?: amount) >= amount
    Text(text, style = BtTheme.type.caption, color = if (warn) BtTheme.colors.statusWarning else BtTheme.colors.textSecondary, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp))
}

/** Who it's with: names used before as chips, and the pencil for a new one. A bill can name several. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DebtWho(state: QuickAddUiState, vm: QuickAddViewModel) {
    val debt = state.debt ?: return
    val picked = debt.names.map { it.lowercase() }.toSet()
    val shown = (debt.names + state.knownNames.filterNot { it.lowercase() in picked }).take(WHO_CHIPS)
    Section(if (debt.type == DebtType.PAID_FOR_OTHERS) "Who owes you" else "Who") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.forEach { name -> BtChip(name, name.lowercase() in picked, { vm.pickName(name) }, outlined = true) }
            IconChip(Icons.Rounded.Edit, "Type a name", selected = state.typing == QuickAddText.NAME) { vm.startTyping(QuickAddText.NAME) }
        }
    }
}

private const val WHO_CHIPS = 8

/** Money lent or borrowed: back at once, or monthly over a few payments, which Loans & debts then counts down. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DebtParts(state: QuickAddUiState, vm: QuickAddViewModel) {
    val debt = state.debt ?: return
    Section("Paid back in") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PARTS.forEach { parts ->
                val label = if (parts == null) "One go" else "$parts months"
                BtChip(label, debt.parts == parts || (parts == null && debt.parts == 1), { vm.setParts(parts) }, outlined = true)
            }
        }
    }
}

/** A bill's own share: not known yet, or an even split with everyone named, counting the user. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DebtShare(state: QuickAddUiState, vm: QuickAddViewModel) {
    val debt = state.debt ?: return
    val amount = state.amount
    val others = debt.names.size
    val even = amount?.takeIf { others > 0 }?.let { evenShare(it, others) }
    Section("Your share") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BtChip("Not yet", debt.share?.isZero == true, { vm.setShare(null) }, outlined = true)
            if (even != null) BtChip("Split by ${others + 1}: ${MoneyFormat.format(even)}", debt.share == even, { vm.setShare(even) }, outlined = true)
        }
    }
    Spacer(Modifier.height(2.dp))
}
