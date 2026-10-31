package com.eminallahverdi.balance.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PersonAvatar
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.component.SegmentedToggle
import com.eminallahverdi.balance.core.designsystem.component.SplitBar
import com.eminallahverdi.balance.core.designsystem.component.toColor
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.CategorySpending
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.PersonSpending
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeRoute(onOpenSettings: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, viewModel, onOpenSettings)
}

@Composable
private fun HomeScreen(state: HomeUiState, vm: HomeViewModel, onOpenSettings: () -> Unit) {
    val c = BtTheme.colors
    fun money(m: Money, whole: Boolean = false) = if (state.hideAmounts) MoneyFormat.HIDDEN else MoneyFormat.format(m, whole)

    LazyColumn(
        Modifier.fillMaxSize().background(c.bgCanvas),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonthSwitcher(state.month, vm::previousMonth, vm::nextMonth, Modifier.weight(1f))
                BtIconButton(if (state.hideAmounts) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show or hide amounts", vm::toggleReveal)
                Spacer(Modifier.width(8.dp))
                BtIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings)
            }
        }
        item {
            BtCard(bordered = true, shape = BtShapes.lg) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Overline("Total balance")
                    Text(money(state.total), style = BtTheme.type.amountDisplay, color = c.textPrimary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.balances, key = { it.account.id }) { b ->
                            Row(
                                Modifier.clip(BtShapes.full).background(c.bgSurfaceHigh).padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                PersonDot(b.account.color, 6.dp)
                                Text(b.account.name, style = BtTheme.type.caption, color = c.textSecondary)
                                Text(money(b.balance), style = BtTheme.type.amountSmall, color = c.textPrimary)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("Spent this month", money(state.summary?.spent ?: Money.ZERO, whole = true), Icons.AutoMirrored.Rounded.TrendingDown, c.moneyExpense, Modifier.weight(1f))
                        Stat("Income", money(state.summary?.income ?: Money.ZERO, whole = true), Icons.AutoMirrored.Rounded.TrendingUp, c.moneyIncome, Modifier.weight(1f))
                    }
                }
            }
        }
        val people = state.summary?.byPerson.orEmpty().filter { it.total.minor > 0 }
        if (people.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.take(4).forEach { p ->
                        BtCard(bordered = true, modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PersonDot(p.person.color)
                                    Text(p.person.name, style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1)
                                }
                                Text(money(p.total, whole = true), style = BtTheme.type.amountSmall, color = c.textPrimary)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Spending", style = BtTheme.type.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                SegmentedToggle(listOf("Category", "Person"), state.view.ordinal, { vm.setView(HomeView.entries[it]) })
            }
        }
        val summary = state.summary
        if (summary == null || summary.spent.isZero) {
            item {
                Text("Nothing spent this month yet. Tap + to add your first expense.", style = BtTheme.type.body, color = c.textMuted, modifier = Modifier.padding(vertical = 24.dp))
            }
        } else if (state.view == HomeView.CATEGORY) {
            items(summary.byCategory, key = { "c" + it.category.id }) { CategoryRow(it, ::money) }
        } else {
            items(summary.byPerson, key = { "p" + it.person.id }) { PersonRow(it, state.expandedPersonId == it.person.id, ::money) { vm.togglePerson(it.person.id) } }
        }
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier) {
    val c = BtTheme.colors
    val label = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + if (month.year != YearMonth.now().year) " ${month.year}" else ""
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.ChevronLeft, "Previous month", tint = c.textSecondary, modifier = Modifier.clip(BtShapes.full).clickable(onClick = onPrev).padding(4.dp))
        Text(label, style = BtTheme.type.titleLarge, color = c.textPrimary)
        if (month < YearMonth.now()) {
            Icon(Icons.Rounded.ChevronRight, "Next month", tint = c.textSecondary, modifier = Modifier.clip(BtShapes.full).clickable(onClick = onNext).padding(4.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Text(label, style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
        }
        Text(value, style = BtTheme.type.amountLarge, color = BtTheme.colors.textPrimary)
    }
}

@Composable
private fun CategoryRow(item: CategorySpending, money: (Money, Boolean) -> String) {
    val c = BtTheme.colors
    BtCard {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryTile(item.category.icon, item.category.color)
                Column(Modifier.weight(1f)) {
                    Text(item.category.name, style = BtTheme.type.bodyStrong, color = c.textPrimary)
                    Text(item.byPerson.joinToString(" · ") { it.key.name }, style = BtTheme.type.caption, color = c.textMuted, maxLines = 1)
                }
                Text(money(item.total, true), style = BtTheme.type.amountMedium, color = c.textPrimary)
            }
            SplitBar(item.byPerson.map { it.key.color to it.amount.minor })
        }
    }
}

@Composable
private fun PersonRow(item: PersonSpending, expanded: Boolean, money: (Money, Boolean) -> String, onClick: () -> Unit) {
    val c = BtTheme.colors
    BtCard(bordered = expanded, modifier = Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonAvatar(item.person)
                Column(Modifier.weight(1f)) {
                    Text(item.person.name, style = BtTheme.type.bodyStrong, color = c.textPrimary)
                    Text(if (item.byCategory.isEmpty()) "No spending this month" else "${item.byCategory.size} categories", style = BtTheme.type.caption, color = c.textMuted)
                }
                Text(money(item.total, true), style = BtTheme.type.amountMedium, color = c.textPrimary)
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.textMuted)
            }
            SplitBar(item.byCategory.map { it.key.color to it.amount.minor })
            if (expanded) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                item.byCategory.forEach { share ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CategoryTile(share.key.icon, share.key.color, size = 28.dp, iconSize = 16.dp, corner = 8.dp)
                        Text(share.key.name, style = BtTheme.type.label, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text(money(share.amount, true), style = BtTheme.type.amountSmall, color = c.textPrimary)
                        val pct = if (item.total.minor > 0) share.amount.minor * 100 / item.total.minor else 0
                        Text("$pct%", style = BtTheme.type.caption, color = c.textMuted, modifier = Modifier.width(36.dp))
                    }
                }
            }
        }
    }
}
