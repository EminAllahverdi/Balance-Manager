package com.eminallahverdi.balance.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.FittedAmount
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
import kotlinx.coroutines.launch

/** How far back Home can swipe. The last page is the current month. */
private const val MONTH_PAGES = 600

private val HEADER_TOP = 16.dp
private val HEADER_ROW = 44.dp
private val HEADER_GAP = 14.dp

/** [onOpenTransactions] opens a month's transactions, narrowed to a category, a person, or both. */
@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    onOpenTransactions: (categoryId: Long?, personId: Long?, month: YearMonth) -> Unit,
    onOpenRecurring: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val thisMonth = remember { YearMonth.now() }
    val pager = rememberPagerState(initialPage = MONTH_PAGES - 1) { MONTH_PAGES }
    val scope = rememberCoroutineScope()
    fun monthOf(page: Int) = thisMonth.minusMonths((MONTH_PAGES - 1 - page).toLong())

    Box(Modifier.fillMaxSize().background(BtTheme.colors.bgCanvas)) {
        // The pager runs behind the header, so a swipe on the month row is the same gesture as a swipe on the page.
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { it }) { page ->
            val month = monthOf(page)
            MonthPage(month, state, viewModel, onOpenRecurring) { categoryId, personId -> onOpenTransactions(categoryId, personId, month) }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = HEADER_TOP).height(HEADER_ROW), verticalAlignment = Alignment.CenterVertically) {
            MonthSwitcher(
                month = monthOf(pager.currentPage),
                onPrev = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier,
            )
            // Only the month swipes: this catches touches on the empty part of the row before the pager sees them.
            Spacer(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) {})
            BtIconButton(if (state.hideAmounts) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show or hide amounts", viewModel::toggleHideAmounts)
            Spacer(Modifier.width(8.dp))
            BtIconButton(Icons.Rounded.Settings, "Settings", onOpenSettings)
        }
    }
}

@Composable
private fun MonthPage(month: YearMonth, state: HomeUiState, vm: HomeViewModel, onOpenRecurring: () -> Unit, onOpen: (categoryId: Long?, personId: Long?) -> Unit) {
    val c = BtTheme.colors
    val summary by remember(month) { vm.summary(month) }.collectAsStateWithLifecycle(null)
    val isCurrent = month == YearMonth.now()
    // The eye covers what you have and what comes in. Spending stays visible.
    fun money(m: Money) = MoneyFormat.format(m)
    fun income(m: Money) = if (state.hideAmounts) MoneyFormat.HIDDEN else MoneyFormat.format(m)
    // A balance can go below zero; totals of spending can't.
    fun balance(m: Money) = if (state.hideAmounts) MoneyFormat.HIDDEN else MoneyFormat.format(m, signed = true)

    LazyColumn(
        Modifier.fillMaxSize().padding(top = HEADER_TOP + HEADER_ROW + HEADER_GAP),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            BtCard(bordered = true, shape = BtShapes.lg) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Balances are what you have today, so they'd be wrong beside a past month's figures.
                    if (isCurrent) {
                        Overline("Total balance")
                        Text(balance(state.total), style = BtTheme.type.amountDisplay, color = c.textPrimary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.balances, key = { it.account.id }) { b ->
                                Row(
                                    Modifier.clip(BtShapes.full).background(c.bgSurfaceHigh).padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    PersonDot(b.account.color, 6.dp)
                                    Text(b.account.name, style = BtTheme.type.caption, color = c.textSecondary)
                                    Text(balance(b.balance), style = BtTheme.type.amountSmall, color = c.textPrimary)
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat(if (isCurrent) "Spent this month" else "Spent", money(summary?.spent ?: Money.ZERO), Icons.AutoMirrored.Rounded.TrendingDown, c.moneyExpense, Modifier.weight(1f))
                        Stat("Income", income(summary?.income ?: Money.ZERO), Icons.AutoMirrored.Rounded.TrendingUp, c.moneyIncome, Modifier.weight(1f))
                    }
                }
            }
        }
        if (isCurrent && state.duePayments > 0) {
            item(key = "due") { DuePayments(state.duePayments, onOpenRecurring) }
        }
        val people = summary?.byPerson.orEmpty().filter { it.total.minor > 0 }
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
                                FittedAmount(money(p.total), BtTheme.type.amountSmall)
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
        val loaded = summary
        if (loaded == null) {
            // Still loading: an empty message here would flash on every swipe.
        } else if (loaded.spent.isZero) {
            item {
                val message = if (isCurrent) "Nothing spent this month yet. Tap + to add your first expense." else "Nothing spent this month."
                Text(message, style = BtTheme.type.body, color = c.textMuted, modifier = Modifier.padding(vertical = 24.dp))
            }
        } else if (state.view == HomeView.CATEGORY) {
            items(loaded.byCategory, key = { "c" + it.category.id }) { row -> CategoryRow(row, ::money) { onOpen(row.category.id, null) } }
        } else {
            items(loaded.byPerson, key = { "p" + it.person.id }) { row ->
                PersonRow(
                    item = row,
                    expanded = state.expandedPersonId == row.person.id,
                    money = ::money,
                    onToggle = { vm.togglePerson(row.person.id) },
                    onOpen = { categoryId -> onOpen(categoryId, row.person.id) },
                )
            }
        }
    }
}

@Composable
private fun DuePayments(count: Int, onClick: () -> Unit) {
    val c = BtTheme.colors
    BtCard(Modifier.clip(BtShapes.md).clickable(role = Role.Button, onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.EventRepeat, null, tint = c.statusWarning, modifier = Modifier.size(22.dp))
            Text(if (count == 1) "1 payment due" else "$count payments due", style = BtTheme.type.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
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
        FittedAmount(value, BtTheme.type.amountLarge)
    }
}

@Composable
private fun CategoryRow(item: CategorySpending, money: (Money) -> String, onClick: () -> Unit) {
    val c = BtTheme.colors
    BtCard(Modifier.clip(BtShapes.md).clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryTile(item.category.icon, item.category.color)
                Column(Modifier.weight(1f)) {
                    Text(item.category.name, style = BtTheme.type.bodyStrong, color = c.textPrimary)
                    Text(item.byPerson.joinToString(" · ") { it.key.name }, style = BtTheme.type.caption, color = c.textMuted, maxLines = 1)
                }
                Text(money(item.total), style = BtTheme.type.amountMedium, color = c.textPrimary)
                Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
            }
            SplitBar(item.byPerson.map { it.key.color to it.amount.minor }, Modifier.padding(end = 6.dp))
        }
    }
}

@Composable
private fun PersonRow(item: PersonSpending, expanded: Boolean, money: (Money) -> String, onToggle: () -> Unit, onOpen: (categoryId: Long?) -> Unit) {
    val c = BtTheme.colors
    BtCard(bordered = expanded, modifier = Modifier.clip(BtShapes.md).clickable(onClick = onToggle)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonAvatar(item.person)
                Column(Modifier.weight(1f)) {
                    Text(item.person.name, style = BtTheme.type.bodyStrong, color = c.textPrimary)
                    val count = item.byCategory.size
                    Text(if (count == 0) "No spending this month" else if (count == 1) "1 category" else "$count categories", style = BtTheme.type.caption, color = c.textMuted)
                }
                Text(money(item.total), style = BtTheme.type.amountMedium, color = c.textPrimary)
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.textMuted)
            }
            SplitBar(item.byCategory.map { it.key.color to it.amount.minor })
            if (expanded) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                item.byCategory.forEach { share ->
                    Row(
                        Modifier.fillMaxWidth().clip(BtShapes.sm).clickable(role = Role.Button) { onOpen(share.key.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CategoryTile(share.key.icon, share.key.color, size = 28.dp, iconSize = 16.dp, corner = 8.dp)
                        Text(share.key.name, style = BtTheme.type.label, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text(money(share.amount), style = BtTheme.type.amountSmall, color = c.textPrimary)
                        val pct = if (item.total.minor > 0) share.amount.minor * 100 / item.total.minor else 0
                        Text("$pct%", style = BtTheme.type.caption, color = c.textMuted, modifier = Modifier.width(36.dp))
                        Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                    }
                }
                if (item.byCategory.isNotEmpty()) {
                    Text(
                        "See all for ${item.person.name}",
                        style = BtTheme.type.label,
                        color = c.accentText,
                        modifier = Modifier.fillMaxWidth().clip(BtShapes.sm).clickable(role = Role.Button) { onOpen(null) }.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
