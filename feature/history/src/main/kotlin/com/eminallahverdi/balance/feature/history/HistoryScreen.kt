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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.component.SplitBar
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.TransactionKind
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

private fun YearMonth?.label(): String = this?.format(MONTH) ?: "All time"

/** Every transaction, narrowed by search and filters. [onOpen] opens one to edit or delete. */
@Composable
fun HistoryRoute(onOpen: (Long) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Held here, not read back from the filter: results arrive off the main thread, and typing must not wait for them.
    var query by rememberSaveable { mutableStateOf("") }
    var removing by remember { mutableStateOf<Long?>(null) }
    val onTap = { row: TransactionDetails ->
        when {
            row.isEditable -> onOpen(row.transaction.id)
            row.transaction.kind == TransactionKind.ADJUSTMENT -> removing = row.transaction.id
        }
    }
    removing?.let { id ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this adjustment?") },
            text = { Text("The account's balance changes back by this amount. This can't be undone.") },
            confirmButton = { TextButton(onClick = { removing = null; viewModel.delete(id) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    Column(Modifier.fillMaxSize().background(BtTheme.colors.bgCanvas)) {
        Text("History", style = BtTheme.type.titleLarge, color = BtTheme.colors.textPrimary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))
        SearchField(query) {
            query = it
            viewModel.setQuery(it)
        }
        Filters(state, viewModel)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.days.isEmpty()) {
                item {
                    val message = if (state.hasFilters) "Nothing matches these filters." else "Nothing here yet. Tap + to add your first expense."
                    Text(message, style = BtTheme.type.body, color = BtTheme.colors.textMuted, modifier = Modifier.padding(vertical = 24.dp))
                }
            } else {
                item(key = "summary") { Summary(state) }
                items(state.days, key = { it.date.toEpochDay() }) { DayGroup(it, state.hideAmounts, onTap) }
            }
        }
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
            if (query.isEmpty()) Text("Search merchant, note or amount", style = BtTheme.type.body, color = c.textMuted, maxLines = 1)
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
                Icon(Icons.Rounded.Close, "Clear search", tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Filters(state: HistoryUiState, vm: HistoryViewModel) {
    val filter = state.filter
    val person = state.people.firstOrNull { it.id == filter.personId }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            label = filter.month.label(),
            active = false,
            leading = { Icon(Icons.Rounded.CalendarMonth, null, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(16.dp)) },
            options = state.months.map { it.label() to { vm.setMonth(it) } } + ("All time" to { vm.setMonth(null) }),
        )
        FilterChip(
            label = person?.name ?: "Person",
            active = person != null,
            leading = person?.let { p -> { PersonDot(p.color) } },
            options = listOf("Everyone" to { vm.setPerson(null) }) + state.people.map { it.name to { vm.setPerson(it.id) } },
        )
        FilterChip(
            label = state.categories.firstOrNull { it.id == filter.categoryId }?.name ?: "Category",
            active = filter.categoryId != null,
            options = listOf("All categories" to { vm.setCategory(null) }) + state.categories.map { it.name to { vm.setCategory(it.id) } },
        )
        FilterChip(
            label = state.accounts.firstOrNull { it.id == filter.accountId }?.name ?: "Account",
            active = filter.accountId != null,
            options = listOf("All accounts" to { vm.setAccount(null) }) + state.accounts.map { it.name to { vm.setAccount(it.id) } },
        )
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, options: List<Pair<String, () -> Unit>>, leading: (@Composable () -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    Box {
        BtChip(
            text = label,
            selected = active,
            onClick = { open = true },
            leading = leading ?: { Icon(Icons.Rounded.ExpandMore, null, tint = if (active) BtTheme.colors.accentOn else BtTheme.colors.textSecondary, modifier = Modifier.size(16.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, onSelect) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(); open = false })
            }
        }
    }
}

@Composable
private fun Summary(state: HistoryUiState) {
    val c = BtTheme.colors
    val filter = state.filter
    val parts = listOfNotNull(
        if (state.count == 1) "1 transaction" else "${state.count} transactions",
        state.people.firstOrNull { it.id == filter.personId }?.name,
        state.categories.firstOrNull { it.id == filter.categoryId }?.name,
        state.accounts.firstOrNull { it.id == filter.accountId }?.name,
        filter.month?.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)) ?: "All time",
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

@Composable
private fun DayGroup(day: HistoryDay, hideAmounts: Boolean, onTap: (TransactionDetails) -> Unit) {
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
                    TransactionRow(row, hideAmounts, onTap)
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(row: TransactionDetails, hideAmounts: Boolean, onTap: (TransactionDetails) -> Unit) {
    val c = BtTheme.colors
    val style = row.parentCategory ?: row.category
    val tappable = row.isEditable || row.transaction.kind == TransactionKind.ADJUSTMENT
    Row(
        Modifier.fillMaxWidth().clickable(enabled = tappable) { onTap(row) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile(row.category?.icon ?: style?.icon ?: "swap_horiz", row.category?.color ?: style?.color ?: 0xFF8A94A3)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.title, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.meta, style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
