package com.eminallahverdi.balance.feature.insights

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.FittedAmount
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.component.SegmentedToggle
import com.eminallahverdi.balance.core.designsystem.component.text
import com.eminallahverdi.balance.core.designsystem.component.toColor
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.CategoryChange
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.InsightBar
import com.eminallahverdi.balance.core.model.InsightOverview
import com.eminallahverdi.balance.core.model.InsightPeriod
import com.eminallahverdi.balance.core.model.Money
import kotlin.math.max

private val CHART_HEIGHT = 224.dp
private val BAR_MAX_HEIGHT = 150.dp
private val BAR_MIN_HEIGHT = 4.dp
private val BAR_MAX_WIDTH = 36.dp
private val BAR_CORNER = 10.dp
private val BAR_FOOT = 4.dp
private const val BAR_WIDTH_SHARE = 0.72f
private const val BAR_FADE = 0.4f
private const val TOP_CHANGES = 3

/**
 * How the money moved: period by period, who it was for, and what changed most. It sits under Reports' tabs, which give it
 * its title, so the period is picked in the corner of its first card. [onOpenCategory] opens a category's entries in the
 * period the chart has selected, as a row on Home does for its month.
 */
@Composable
fun InsightsRoute(onOpenCategory: (categoryId: Long, range: DateRange) -> Unit = { _, _ -> }, viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = BtTheme.colors
    var showAll by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(c.bgCanvas),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val overview = state.overview ?: return@LazyColumn
        item { ChartCard(state.period, overview, viewModel) }
        item { Stats(state, overview) }
        if (overview.changes.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Overline(stringResource(R.string.insights_biggest_changes), Modifier.weight(1f))
                    if (overview.changes.size > TOP_CHANGES) {
                        Text(
                            if (showAll) stringResource(R.string.insights_top_n, TOP_CHANGES) else stringResource(R.string.insights_all_categories),
                            style = BtTheme.type.caption,
                            color = c.accentText,
                            modifier = Modifier.clip(BtShapes.sm).clickable(role = Role.Button) { showAll = !showAll }.padding(horizontal = 8.dp, vertical = 14.dp),
                        )
                    }
                }
            }
            item {
                val previous = state.period.barLabel(overview.previousRange)
                BtCard {
                    Column {
                        (if (showAll) overview.changes else overview.changes.take(TOP_CHANGES)).forEachIndexed { i, change ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                            ChangeRow(change, previous) { onOpenCategory(change.category.id, overview.selected.range) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(period: InsightPeriod, overview: InsightOverview, vm: InsightsViewModel) {
    val c = BtTheme.colors
    BtCard(bordered = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Overline(stringResource(R.string.insights_spending_by_person, period.periodName(overview.selected.range).text()), Modifier.weight(1f))
                    SegmentedToggle(
                        options = listOf(stringResource(R.string.insights_week), stringResource(R.string.insights_month), stringResource(R.string.insights_year)),
                        selected = period.ordinal,
                        onSelect = { vm.setPeriod(InsightPeriod.entries[it]) },
                        compact = true,
                    )
                }
                Text(MoneyFormat.format(overview.spent), style = BtTheme.type.amountLarge, color = c.textPrimary)
                Text(
                    comparison(overview.change, period.periodName(overview.previousRange)).text(),
                    style = BtTheme.type.caption,
                    color = changeColor(overview.change, neutral = c.textSecondary),
                )
            }
            Bars(period, overview, vm)
            Legend(overview)
        }
    }
}

/** Spending less is the good direction, so it's the income colour. */
@Composable
private fun changeColor(change: Money, neutral: Color): Color = when {
    change.minor > 0 -> BtTheme.colors.moneyExpense
    change.minor < 0 -> BtTheme.colors.moneyIncome
    else -> neutral
}

@Composable
private fun Bars(period: InsightPeriod, overview: InsightOverview, vm: InsightsViewModel) {
    val biggest = max(overview.bars.maxOf { it.spent.minor }, 1L)
    Row(Modifier.fillMaxWidth().height(CHART_HEIGHT), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        overview.bars.forEachIndexed { i, bar ->
            BarColumn(period, bar, biggest, selected = i == overview.selectedIndex, onClick = { vm.select(i) }, modifier = Modifier.weight(1f))
        }
    }
}

/** One stacked bar. The rest fade back from the selected one, which alone carries a figure: the heading above has it exact. */
@Composable
private fun BarColumn(period: InsightPeriod, bar: InsightBar, biggest: Long, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    val name = period.barLabel(bar.range)
    val spoken = "$name, ${MoneyFormat.format(bar.spent)}"
    val height by animateDpAsState(max(BAR_MAX_HEIGHT.value * bar.spent.minor / biggest, BAR_MIN_HEIGHT.value).dp, label = "bar")
    val fade by animateFloatAsState(if (selected) 1f else BAR_FADE, label = "bar fade")
    Column(
        modifier
            .fillMaxHeight()
            .clip(BtShapes.sm)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = spoken }
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
    ) {
        if (selected && !bar.spent.isZero) {
            FittedAmount(MoneyFormat.short(bar.spent), BtTheme.type.amountSmall.copy(fontSize = 11.sp, lineHeight = 14.sp))
        }
        Column(
            Modifier
                .fillMaxWidth(BAR_WIDTH_SHARE)
                .widthIn(max = BAR_MAX_WIDTH)
                .height(height)
                .graphicsLayer { alpha = fade }
                .clip(RoundedCornerShape(topStart = BAR_CORNER, topEnd = BAR_CORNER, bottomStart = BAR_FOOT, bottomEnd = BAR_FOOT))
                .background(if (bar.byPerson.isEmpty()) c.bgSurfaceHigh else Color.Transparent),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            bar.byPerson.filter { it.amount.minor > 0 }.forEach { (person, amount) ->
                key(person.id) {
                    Box(Modifier.fillMaxWidth().weight(amount.minor.toFloat()).background(person.color.toColor()))
                }
            }
        }
        Text(
            name,
            style = BtTheme.type.caption.copy(fontSize = if (period == InsightPeriod.WEEK) 11.sp else 12.sp),
            color = if (selected) c.textPrimary else c.textMuted,
            maxLines = 1,
        )
    }
}

/** Doubles as the selected bar's breakdown: who it was for, and how much. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(overview: InsightOverview) {
    if (overview.people.isEmpty()) return
    val c = BtTheme.colors
    val amounts = overview.selected.byPerson.associate { it.key.id to it.amount }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        overview.people.forEach { person ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PersonDot(person.color)
                Text(person.name, style = BtTheme.type.caption, color = c.textSecondary)
                Text(MoneyFormat.format(amounts[person.id] ?: Money.ZERO), style = BtTheme.type.amountSmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = c.textPrimary)
            }
        }
    }
}

@Composable
private fun Stats(state: InsightsUiState, overview: InsightOverview) {
    val c = BtTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat(stringResource(R.string.insights_daily_average), MoneyFormat.format(overview.dailyAverage), c.textPrimary, pluralStringResource(R.plurals.insights_days, overview.days, overview.days), Modifier.weight(1f))
        // Income − spending gives the income away, so the eye covers it.
        Stat(
            title = stringResource(state.period.savedLabel(overview.isCurrent)),
            value = if (state.hideAmounts) MoneyFormat.HIDDEN else MoneyFormat.format(overview.saved, signed = true),
            color = if (state.hideAmounts) c.textPrimary else if (overview.saved.isNegative) c.moneyExpense else c.moneyIncome,
            caption = stringResource(R.string.insights_income_minus_spending),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Stat(title: String, value: String, color: Color, caption: String, modifier: Modifier = Modifier) {
    BtCard(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Overline(title)
            FittedAmount(value, BtTheme.type.amountMedium.copy(fontSize = 20.sp, lineHeight = 25.sp), color = color)
            Text(caption, style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
        }
    }
}

/**
 * A category, what it came to and how that changed, opening its entries. The change sits under the name, so a longer
 * comparison, as Azerbaijani's is, never cuts the name short.
 */
@Composable
private fun ChangeRow(item: CategoryChange, previous: String, onClick: () -> Unit) {
    val c = BtTheme.colors
    val tint = changeColor(item.change, neutral = c.textSecondary)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile(item.category.icon, item.category.color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.category.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(if (item.change.minor > 0) Icons.Rounded.NorthEast else Icons.Rounded.SouthEast, null, tint = tint, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.insights_versus, signedChange(item.change), previous), style = BtTheme.type.caption, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(MoneyFormat.format(item.amount), style = BtTheme.type.amountSmall.copy(fontSize = 15.sp, lineHeight = 20.sp), color = c.textPrimary)
        Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
    }
}

