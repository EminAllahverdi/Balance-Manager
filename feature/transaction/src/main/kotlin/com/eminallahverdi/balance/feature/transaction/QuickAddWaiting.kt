package com.eminallahverdi.balance.feature.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.data.PendingItem
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import java.time.ZoneId

/** The few newest, so the grid stays in view below them. To confirm has the rest. */
private const val WAITING_SHOWN = 3
private const val BANK_TILE = 0xFF8A94A3

/**
 * What the bank has sent and is still waiting on To confirm, on top of a new entry's grid. The payment just made is
 * usually here already: Add saves it as guessed, and tapping it opens it filled in, to check first.
 */
@Composable
internal fun WaitingFromBank(state: QuickAddUiState, vm: QuickAddViewModel) {
    val c = BtTheme.colors
    val waiting = state.waitingHere
    if (waiting.isEmpty()) return
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Overline(pluralStringResource(R.plurals.quickadd_from_bank_waiting, waiting.size, waiting.size), Modifier.padding(bottom = 8.dp))
        BtCard(shape = BtShapes.md) {
            Column {
                waiting.sortedByDescending { it.entry.postedAt }.take(WAITING_SHOWN).forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                    WaitingRow(item, state, vm)
                }
            }
        }
        Text(stringResource(R.string.quickadd_tap_to_check_or_add), style = BtTheme.type.caption, color = c.textMuted, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    }
}

@Composable
private fun WaitingRow(item: PendingItem, state: QuickAddUiState, vm: QuickAddViewModel) {
    val c = BtTheme.colors
    val guess = item.guess
    val category = state.allCategories.firstOrNull { it.id == guess.categoryId }
    val account = state.accounts.firstOrNull { it.id == guess.accountId }
    val posted = item.entry.postedAt.atZone(ZoneId.systemDefault())
    val caption = listOfNotNull(
        stringResource(R.string.quickadd_suggested).takeIf { guess.isSuggested },
        category?.name,
        account?.name,
        DateLabels.timeOfDay(posted.hour * 60 + posted.minute),
    ).joinToString(" · ")
    // Adding as it is needs everything To confirm's Confirm needs; anything short of that is opened to fill in.
    val canAdd = guess.accountId != null && (guess.categoryId != null || guess.toAccountId != null)
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.quickadd_check_it_first)) { vm.openPending(item) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (category != null) CategoryTile(category.icon, category.color) else CategoryTile("account_balance", BANK_TILE)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(guess.merchantName.ifBlank { item.entry.merchant.ifBlank { item.entry.source } }, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(MoneyFormat.format(item.entry.amount), style = BtTheme.type.amountSmall, color = c.textPrimary)
        if (canAdd) {
            BtChip(
                text = stringResource(R.string.quickadd_add),
                selected = false,
                onClick = { vm.addPending(item) },
                leading = { Icon(Icons.Rounded.Check, null, tint = c.accentText, modifier = Modifier.size(16.dp)) },
            )
        }
    }
}
