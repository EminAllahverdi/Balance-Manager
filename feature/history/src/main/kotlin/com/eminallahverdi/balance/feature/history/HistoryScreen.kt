package com.eminallahverdi.balance.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.component.SplitBar
import com.eminallahverdi.balance.core.designsystem.component.text
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.designsystem.theme.TILE_TINT
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.TransactionKind
import java.time.YearMonth

internal fun YearMonth.label(): String = DateLabels.monthTitle(this, withYear = true)

/**
 * Every transaction, narrowed by search and filters. [onOpen] opens one to edit or delete.
 * [onBack] is set when this was opened from another screen rather than as the History tab.
 */
@Composable
fun HistoryRoute(onOpen: (Long) -> Unit, onBack: (() -> Unit)? = null, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Held here, not read back from the filter: results arrive off the main thread, and typing must not wait for them.
    var query by rememberSaveable { mutableStateOf("") }
    var sheet by rememberSaveable { mutableStateOf<FilterSheet?>(null) }
    val actions = rememberRowActions(onOpen, viewModel::delete, state.hideAmounts)
    sheet?.let { HistoryFilterSheet(it, state, viewModel, onClose = { sheet = null }) }
    // Without the bottom bar, the list runs under the system navigation.
    val bottomInset = if (onBack != null) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
    Column(Modifier.fillMaxSize().background(BtTheme.colors.bgCanvas)) {
        Header(state, onBack)
        SearchField(query) {
            query = it
            viewModel.setQuery(it)
        }
        Filters(state, onOpen = { sheet = it }, onClear = viewModel::clearFilters)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.days.isEmpty()) {
                item {
                    Text(state.filter.emptyMessage(YearMonth.now()).text(), style = BtTheme.type.body, color = BtTheme.colors.textMuted, modifier = Modifier.padding(vertical = 24.dp))
                }
            } else {
                item(key = "summary") { Summary(state) }
                items(state.days, key = { it.date.toEpochDay() }) { DayGroup(it, state.hideAmounts, actions) }
            }
        }
    }
}

/** What a row does when it's tapped and when it's swiped away. Made by [rememberRowActions]. */
class RowActions internal constructor(internal val tap: (TransactionDetails) -> Unit, internal val remove: (TransactionDetails) -> Unit)

/**
 * What rows do, wherever they're listed: a tap opens one quick add can edit with [onOpen]. Swiping one away, or tapping one
 * quick add can't open but that can be taken back, asks first, then [onRemove]. Anything else, like an imported internal
 * move, stays as it is.
 */
@Composable
fun rememberRowActions(onOpen: (Long) -> Unit, onRemove: (Long) -> Unit, hideAmounts: Boolean): RowActions {
    var removing by remember { mutableStateOf<TransactionDetails?>(null) }
    removing?.let { row ->
        val removal = row.removal(hideAmounts)
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(removal.title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(removal.named.map { it.text() }.joinToString(" · "), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(removal.consequence))
                }
            },
            confirmButton = { TextButton(onClick = { removing = null; onRemove(row.transaction.id) }) { Text(stringResource(removal.action)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.history_cancel)) } },
        )
    }
    val open by rememberUpdatedState(onOpen)
    return remember {
        RowActions(
            tap = { row ->
                when {
                    row.isEditable -> open(row.transaction.id)
                    row.isRemovable -> removing = row
                }
            },
            remove = { row -> removing = row },
        )
    }
}

@Composable
private fun Header(state: HistoryUiState, onBack: (() -> Unit)?) {
    val filter = state.filter
    val title = when {
        onBack == null -> stringResource(R.string.history_history)
        filter.kind == TransactionKind.TRANSFER -> stringResource(R.string.history_transfers)
        else -> state.categories.firstOrNull { it.id == filter.categoryId }?.name ?: state.people.firstOrNull { it.id == filter.personId }?.name ?: stringResource(R.string.history_history)
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) BtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.history_back), onBack)
        Text(title, style = BtTheme.type.titleLarge, color = BtTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val c = BtTheme.colors
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(BtShapes.full)
            .background(c.bgSurfaceRaised)
            .border(1.dp, c.borderDefault, BtShapes.full)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.textMuted, modifier = Modifier.size(22.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(stringResource(R.string.history_search_hint), style = BtTheme.type.body, color = c.textMuted, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = BtTheme.type.body.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accentFill),
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(40.dp).clip(BtShapes.full).clickable(role = Role.Button) { onChange("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, stringResource(R.string.history_clear_search), tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The filters as chips. While any is narrowed, Clear comes first, in reach before the row scrolls. */
@Composable
private fun Filters(state: HistoryUiState, onOpen: (FilterSheet) -> Unit, onClear: () -> Unit) {
    val filter = state.filter
    val person = state.people.firstOrNull { it.id == filter.personId }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filter.canClear()) {
            BtChip(
                text = stringResource(R.string.history_clear_filters),
                selected = false,
                onClick = onClear,
                leading = { Icon(Icons.Rounded.Close, null, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(16.dp)) },
            )
        }
        FilterChip(
            label = filter.periodLabel { it.label() } ?: stringResource(R.string.history_all_time),
            active = false,
            onClick = { onOpen(FilterSheet.MONTH) },
            leading = { Icon(Icons.Rounded.CalendarMonth, null, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(16.dp)) },
        )
        FilterChip(
            label = person?.name ?: stringResource(R.string.history_person),
            active = person != null,
            onClick = { onOpen(FilterSheet.PERSON) },
            leading = person?.let { p -> { PersonDot(p.color) } },
        )
        FilterChip(
            label = state.categories.firstOrNull { it.id == filter.categoryId }?.name ?: stringResource(R.string.history_category),
            active = filter.categoryId != null,
            onClick = { onOpen(FilterSheet.CATEGORY) },
        )
        FilterChip(
            label = state.accounts.firstOrNull { it.id == filter.accountId }?.name ?: stringResource(R.string.history_account),
            active = filter.accountId != null,
            onClick = { onOpen(FilterSheet.ACCOUNT) },
        )
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    BtChip(
        text = label,
        selected = active,
        onClick = onClick,
        leading = leading,
        trailing = { Icon(Icons.Rounded.ExpandMore, null, tint = if (active) BtTheme.colors.accentOn else BtTheme.colors.textSecondary, modifier = Modifier.size(16.dp)) },
    )
}

@Composable
private fun Summary(state: HistoryUiState) {
    val c = BtTheme.colors
    val filter = state.filter
    val parts = listOfNotNull(
        when {
            filter.kind == TransactionKind.TRANSFER -> pluralStringResource(R.plurals.history_transfers, state.count, state.count)
            else -> pluralStringResource(R.plurals.history_transactions, state.count, state.count)
        },
        state.people.firstOrNull { it.id == filter.personId }?.name,
        state.categories.firstOrNull { it.id == filter.categoryId }?.name,
        state.accounts.firstOrNull { it.id == filter.accountId }?.name,
        filter.periodLabel { DateLabels.monthName(it.atDay(1)) } ?: stringResource(R.string.history_all_time),
    )
    BtCard(bordered = true) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(parts.joinToString(" · "), style = BtTheme.type.caption, color = c.textSecondary)
                Text(amount(state.total, state.hideAmounts && !state.totalIsSpending), style = BtTheme.type.amountMedium, color = c.textPrimary)
            }
            if (state.shares.isNotEmpty()) SplitBar(state.shares, Modifier.width(120.dp))
        }
    }
}

private fun amount(money: Money, hidden: Boolean) = if (hidden) MoneyFormat.HIDDEN else MoneyFormat.format(money)

/** One day's transactions under its date and total, as History lists them. */
@Composable
fun DayGroup(day: HistoryDay, hideAmounts: Boolean, actions: RowActions) {
    val c = BtTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Overline(DateLabels.dayHeader(day.date), Modifier.weight(1f))
            Text(amount(day.total, hideAmounts && !day.totalIsSpending), style = BtTheme.type.caption, color = c.textSecondary)
        }
        BtCard {
            Column {
                day.rows.forEachIndexed { i, row ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                    // Keyed, so a swipe stays with its own row when the one above it goes.
                    key(row.transaction.id) { TransactionRow(row, hideAmounts, actions) }
                }
            }
        }
    }
}

/** How far a row has to go before letting go asks to delete it. A quick flick asks too. */
private const val SWIPE_THRESHOLD = 0.4f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionRow(row: TransactionDetails, hideAmounts: Boolean, actions: RowActions) {
    val tappable = row.isEditable || row.isRemovable
    val current by rememberUpdatedState(row)
    val deleteLabel = stringResource(R.string.history_delete)
    // The row springs back as the question opens, and only goes once the answer is yes.
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) actions.remove(current)
            value != SwipeToDismissBoxValue.EndToStart
        },
        positionalThreshold = { width -> width * SWIPE_THRESHOLD },
    )
    SwipeToDismissBox(
        state = swipe,
        backgroundContent = { SwipeBehind(if (row.isEditable) stringResource(R.string.history_delete) else stringResource(R.string.history_remove)) },
        enableDismissFromStartToEnd = false,
        gesturesEnabled = tappable,
    ) {
        RowContent(
            row,
            hideAmounts,
            Modifier.clickable(enabled = tappable) { actions.tap(row) }.semantics {
                if (tappable) customActions = listOf(CustomAccessibilityAction(deleteLabel) { actions.remove(current); true })
            },
        )
    }
}

/** What a swipe uncovers at the row's end. */
@Composable
private fun SwipeBehind(label: String) {
    val c = BtTheme.colors
    Row(
        Modifier.fillMaxSize().background(c.moneyExpense.copy(alpha = TILE_TINT)).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
    ) {
        Icon(Icons.Rounded.Delete, null, tint = c.moneyExpense, modifier = Modifier.size(20.dp))
        Text(label, style = BtTheme.type.label, color = c.moneyExpense)
    }
}

@Composable
private fun RowContent(row: TransactionDetails, hideAmounts: Boolean, modifier: Modifier) {
    val c = BtTheme.colors
    val style = row.parentCategory ?: row.category
    val fallbackIcon = if (row.transaction.kind == TransactionKind.DEBT) "handshake" else "swap_horiz"
    // Opaque, so what's behind only shows where the row has moved away.
    Row(
        Modifier.fillMaxWidth().background(c.bgSurface).then(modifier).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile(row.category?.icon ?: style?.icon ?: fallbackIcon, row.category?.color ?: style?.color ?: 0xFF8A94A3)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.title.text(), style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.meta.map { it.text() }.joinToString(" · "), style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (hideAmounts && row.isSensitive) MoneyFormat.HIDDEN else row.signedAmount(),
                style = BtTheme.type.amountMedium,
                color = if (row.transaction.kind == TransactionKind.INCOME) c.moneyIncome else c.textPrimary,
            )
            row.person?.let { person ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonDot(person.color, 7.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(person.name, style = BtTheme.type.caption, color = c.textSecondary)
                }
            }
        }
    }
}
