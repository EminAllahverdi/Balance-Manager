package com.eminallahverdi.balance.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.component.text
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.RecurringPayment

private const val GRID_COLUMNS = 4

/**
 * Removing a category that's in use: what's filed under it, each row opening its entries, then a choice. Moving them to
 * another category is the main way out, as it is for an account; keeping it archived leaves everything as it was.
 * [onOpenCategory] opens a category's entries in History, all of them.
 */
@Composable
fun RemoveCategoryRoute(onBack: () -> Unit, onOpenCategory: (Long) -> Unit, viewModel: RemoveCategoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = BtTheme.colors
    var picking by rememberSaveable { mutableStateOf(false) }
    val name = state.category?.name.orEmpty()

    LaunchedEffect(state.isGone) { if (state.isGone) onBack() }

    Column(Modifier.fillMaxSize().background(c.bgCanvas).navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.catalog_back), onBack)
            Text(stringResource(R.string.catalog_remove_title, name), style = BtTheme.type.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.loading) return@LazyColumn
            item(key = "intro") { Text(state.intro.text(), style = BtTheme.type.body, color = c.textSecondary, modifier = Modifier.padding(horizontal = 4.dp)) }
            if (state.parts.isNotEmpty()) {
                item(key = "parts") {
                    Section(if (state.subCategories.isNotEmpty()) stringResource(R.string.catalog_sub_categories_title) else null) {
                        state.parts.forEachIndexed { i, part ->
                            if (i > 0) Divider()
                            val count = pluralStringResource(R.plurals.catalog_n_transactions, part.count, part.count)
                            UsageRow(
                                category = part.category,
                                caption = if (part.isParent) stringResource(R.string.catalog_not_in_sub, count) else count,
                                amount = if (state.hidesTotals) MoneyFormat.HIDDEN else MoneyFormat.format(part.total, signed = true),
                                onClick = { onOpenCategory(part.category.id) },
                            )
                        }
                    }
                }
            }
            if (state.payments.isNotEmpty()) {
                item(key = "payments") {
                    Section(stringResource(R.string.catalog_recurring_payments)) {
                        state.payments.forEachIndexed { i, payment ->
                            if (i > 0) Divider()
                            PaymentRow(payment, state.category)
                        }
                    }
                }
            }
            if (state.parts.isNotEmpty()) {
                item(key = "note") { Text(stringResource(R.string.catalog_rows_open_history), style = BtTheme.type.caption, color = c.textMuted, modifier = Modifier.padding(horizontal = 4.dp)) }
            }
        }
        if (!state.loading && state.category != null) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PrimaryButton(stringResource(R.string.catalog_move_all), { picking = true }, enabled = state.targets.isNotEmpty())
                Box(
                    Modifier.padding(top = 4.dp).heightIn(min = 48.dp).clip(BtShapes.full).clickable(role = Role.Button, onClick = viewModel::keepArchived).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.catalog_keep_archived, name), style = BtTheme.type.label, color = c.textSecondary) }
            }
        }
    }

    if (picking) MoveSheet(state, onMove = { picking = false; viewModel.moveAll(it) }, onClose = { picking = false })
}

@Composable
private fun Section(title: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let { Overline(it, Modifier.padding(horizontal = 4.dp)) }
        BtCard { Column { content() } }
    }
}

@Composable
private fun UsageRow(category: Category, caption: String, amount: String, onClick: () -> Unit) {
    val c = BtTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile(category.icon, category.color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(category.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(amount, style = BtTheme.type.amountMedium, color = c.textPrimary)
        Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PaymentRow(payment: RecurringPayment, category: Category?) {
    val c = BtTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        category?.let { CategoryTile(it.icon, it.color) }
        Text(payment.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(MoneyFormat.format(payment.amountDue), style = BtTheme.type.amountMedium, color = c.textPrimary)
    }
}

/**
 * Where everything goes: the categories it can join, in a grid like quick add's, and the one button. Its sub-categories,
 * payments and shops go too, which the note says before the button, because it can't be undone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(state: RemoveCategoryUiState, onMove: (Category) -> Unit, onClose: () -> Unit) {
    val c = BtTheme.colors
    var pickedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val picked = state.targets.firstOrNull { it.id == pickedId }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bgSurface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.catalog_move_where, state.category?.name.orEmpty()), style = BtTheme.type.titleMedium, color = c.textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.targets.chunked(GRID_COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { target -> TargetCell(target, target.id == pickedId, Modifier.weight(1f)) { pickedId = target.id } }
                        repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            val children = state.subCategories.filterNot { it.isArchived }.map { it.name }
            val note = if (children.isEmpty()) stringResource(R.string.catalog_move_note) else stringResource(R.string.catalog_move_note_children, listed(children))
            Text("$note ${stringResource(R.string.catalog_cant_undo)}", style = BtTheme.type.caption, color = c.textSecondary)
            PrimaryButton(
                text = picked?.let { state.moveLabel(it).text() } ?: stringResource(R.string.catalog_move_all),
                onClick = { picked?.let(onMove) },
                enabled = picked != null,
            )
        }
    }
}

/** Fuel, Parking and Service, in the language showing. */
@Composable
private fun listed(names: List<String>): String =
    if (names.size == 1) names[0] else stringResource(R.string.catalog_list_two, names.dropLast(1).joinToString(", "), names.last())

@Composable
private fun TargetCell(category: Category, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = BtTheme.colors
    Column(
        modifier.clip(BtShapes.sm).clickable(role = Role.RadioButton, onClick = onClick).semantics { this.selected = selected }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val ring = if (selected) Modifier.border(2.dp, c.accentFill, RoundedCornerShape(19.dp)).padding(3.dp) else Modifier.padding(3.dp)
        CategoryTile(category.icon, category.color, modifier = ring, size = 48.dp, corner = 16.dp)
        Text(
            category.name,
            style = BtTheme.type.caption,
            color = if (selected) c.textPrimary else c.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(BtTheme.colors.borderDefault))
}
