package com.eminallahverdi.balance.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.designsystem.component.FittedAmount
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.AccountBalance
import com.eminallahverdi.balance.core.model.Money

private const val PER_PAGE = 4
private const val PER_ROW = 2

/**
 * The accounts four at a time, two by two, paging sideways when there are more, with dots under them to say so. The ones
 * left out of the total come last and muted. Each opens its transactions.
 */
@Composable
internal fun AccountGrid(inTotal: List<AccountBalance>, outside: List<AccountBalance>, balance: (Money) -> String, onOpen: (Long) -> Unit) {
    val all = inTotal.map { it to true } + outside.map { it to false }
    if (all.isEmpty()) return
    val pages = all.chunked(PER_PAGE)
    val pager = rememberPagerState { pages.size }
    // With more than one page, each is as tall as a full one, so nothing below moves while swiping between them.
    val rows = if (pages.size > 1) PER_PAGE / PER_ROW else (all.size + PER_ROW - 1) / PER_ROW
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 16.dp, verticalAlignment = Alignment.Top) { page ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(rows) { r ->
                    val row = pages[page].drop(r * PER_ROW).take(PER_ROW)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (b, counted) -> AccountTile(b, counted, balance, Modifier.weight(1f)) { onOpen(b.account.id) } }
                        // An empty place keeps a tile's height without showing one.
                        repeat(PER_ROW - row.size) { AccountTile(all.first().first, true, balance, Modifier.weight(1f).alpha(0f).clearAndSetSemantics {}) {} }
                    }
                }
            }
        }
        if (pages.size > 1) PageDots(pager.currentPage, pages.size)
    }
}

@Composable
private fun AccountTile(item: AccountBalance, counted: Boolean, balance: (Money) -> String, modifier: Modifier, onClick: () -> Unit) {
    val c = BtTheme.colors
    Column(
        modifier.clip(BtShapes.md).background(c.bgSurfaceHigh).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PersonDot(item.account.color, 6.dp)
            Text(item.account.name, style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FittedAmount(balance(item.balance), BtTheme.type.amountSmall, color = if (counted) c.textPrimary else c.textMuted)
    }
}

/** Which page of accounts is showing: a short bar for this one, dots for the rest. */
@Composable
private fun PageDots(current: Int, count: Int) {
    val c = BtTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        repeat(count) { i ->
            val on = i == current
            Spacer(Modifier.size(width = if (on) 16.dp else 6.dp, height = 6.dp).clip(BtShapes.full).background(if (on) c.textSecondary else c.borderStrong))
        }
    }
}

/**
 * Under the total: what's left once this month's planned payments are paid, and how much they come to. One line, so it
 * fits beside the grid too, and it opens Planned payments.
 */
@Composable
internal fun PlannedLine(left: String, planned: String, onClick: () -> Unit) {
    val c = BtTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(BtShapes.sm).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.EventAvailable, null, tint = c.accentText, modifier = Modifier.size(16.dp))
        Text(
            buildAnnotatedString {
                withStyle(BtTheme.type.amountSmall.toSpanStyle().copy(color = c.textPrimary)) { append(left) }
                append(" ")
                append(stringResource(R.string.home_left_after_planned, planned))
            },
            style = BtTheme.type.caption,
            color = c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
    }
}
