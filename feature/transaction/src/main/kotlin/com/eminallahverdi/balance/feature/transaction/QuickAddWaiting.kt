package com.eminallahverdi.balance.feature.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.delay

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
    val skipped = state.skipped
    if (waiting.isEmpty() && skipped == null) return
    LaunchedEffect(skipped) {
        if (skipped != null) {
            delay(UNDO_MILLIS)
            vm.forgetSkipped()
        }
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        if (waiting.isNotEmpty()) {
            Overline(pluralStringResource(R.plurals.quickadd_from_bank_waiting, waiting.size, waiting.size), Modifier.padding(bottom = 8.dp))
            BtCard(shape = BtShapes.md) {
                Column {
                    waiting.sortedByDescending { it.entry.postedAt }.take(WAITING_SHOWN).forEachIndexed { i, item ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                        key(item.entry.id) { SwipeToSkip(item, vm) { WaitingRow(item, state, vm) } }
                    }
                }
            }
        }
        if (skipped != null) SkippedNote(skipped, vm::undoSkip)
        if (waiting.isNotEmpty()) {
            Text(stringResource(R.string.quickadd_tap_to_check_or_add), style = BtTheme.type.caption, color = c.textMuted, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        }
    }
}

/** How long Undo stays after a payment is skipped. */
private const val UNDO_MILLIS = 4_000L

/** How far a row has to go before letting go skips it. A quick flick skips too. */
private const val SKIP_THRESHOLD = 0.4f

/**
 * A bank payment swiped left is skipped, as To confirm's Skip does. It moves no money, so it goes without a question,
 * and Undo brings it back for a few seconds. A widget's + opens this same sheet, so it works from there too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToSkip(item: PendingItem, vm: QuickAddViewModel, content: @Composable () -> Unit) {
    val current by rememberUpdatedState(item)
    val skipLabel = stringResource(R.string.quickadd_skip)
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) vm.skipPending(current)
            value == SwipeToDismissBoxValue.EndToStart
        },
        positionalThreshold = { width -> width * SKIP_THRESHOLD },
    )
    SwipeToDismissBox(
        state = swipe,
        // Only while it's being swiped: at rest the row hides it, and one with no Add chip would show it beside its amount.
        backgroundContent = { if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) SkipBehind(skipLabel) },
        enableDismissFromStartToEnd = false,
        modifier = Modifier.semantics { customActions = listOf(CustomAccessibilityAction(skipLabel) { vm.skipPending(current); true }) },
    ) {
        // The card's own colour, so what's behind shows only where the row has moved away.
        Box(Modifier.background(BtTheme.colors.bgSurface)) { content() }
    }
}

/** What a swipe uncovers: Skip, in plain colours, since nothing is lost. */
@Composable
private fun SkipBehind(label: String) {
    val c = BtTheme.colors
    Row(
        Modifier.fillMaxSize().background(c.bgSurfaceHigh).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
    ) {
        Icon(Icons.Rounded.SkipNext, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        Text(label, style = BtTheme.type.label, color = c.textSecondary)
    }
}

/** "Skipped Bravo" with Undo, under the list, for a few seconds after a swipe. */
@Composable
private fun SkippedNote(item: PendingItem, onUndo: () -> Unit) {
    val c = BtTheme.colors
    val name = item.guess.merchantName.ifBlank { item.entry.merchant.ifBlank { item.entry.source } }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.quickadd_skipped, name), style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
        Text(
            stringResource(R.string.quickadd_undo),
            style = BtTheme.type.label,
            color = c.accentText,
            modifier = Modifier.clip(BtShapes.sm).clickable(role = Role.Button, onClick = onUndo).padding(horizontal = 10.dp, vertical = 12.dp),
        )
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
