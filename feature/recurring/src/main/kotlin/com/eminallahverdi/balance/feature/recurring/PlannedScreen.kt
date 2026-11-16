package com.eminallahverdi.balance.feature.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.BtDatePickerDialog
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.BtTextField
import com.eminallahverdi.balance.core.designsystem.component.CategoryChipRow
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.EditorField
import com.eminallahverdi.balance.core.designsystem.component.EditorTitle
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.PlannedStatus
import com.eminallahverdi.balance.core.model.RecurringPayment
import java.time.YearMonth

private const val PLAIN_TILE = 0xFF8A94A3

/**
 * One-off payments the owner means to make this month, and what's left in the accounts after them, which Home shows under
 * the total. Each leaves the sum once an entry for it is added, or when it's ticked. Recurring payments due this month are
 * offered to add in one tap.
 */
@Composable
fun PlannedRoute(onBack: () -> Unit, viewModel: PlannedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = BtTheme.colors

    LazyColumn(
        Modifier.fillMaxSize().background(c.bgCanvas).navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.recurring_back), onBack)
                Text(stringResource(R.string.recurring_planned_payments), style = BtTheme.type.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                BtIconButton(Icons.Rounded.Add, stringResource(R.string.recurring_plan_a_payment), viewModel::newPayment)
            }
        }
        if (state.loading) return@LazyColumn
        if (state.toPay.isNotEmpty()) {
            item(key = "sum") { Sum(state) }
        } else if (state.statuses.isEmpty()) {
            item(key = "hint") { Text(stringResource(R.string.recurring_planned_hint), style = BtTheme.type.body, color = c.textMuted, modifier = Modifier.padding(4.dp)) }
            item(key = "first") { PrimaryButton(stringResource(R.string.recurring_plan_a_payment), viewModel::newPayment) }
        }
        section("pay", R.string.recurring_to_pay, state.toPay) { PlannedRow(it, state, viewModel) }
        section("later", R.string.recurring_later, state.later) { PlannedRow(it, state, viewModel) }
        section("paid", R.string.recurring_paid, state.paid) { PlannedRow(it, state, viewModel) }
        section("offered", R.string.recurring_from_recurring, state.offered) { OfferedRow(it, state, viewModel) }
        if (state.statuses.isNotEmpty() || state.offered.isNotEmpty()) {
            item(key = "how") { Text(stringResource(R.string.recurring_planned_how), style = BtTheme.type.caption, color = c.textMuted, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
    }

    state.editor?.let { PlannedSheet(it, state, viewModel) }
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.section(key: String, title: Int, rows: List<T>, row: @Composable (T) -> Unit) {
    if (rows.isEmpty()) return
    item(key = key) {
        val c = BtTheme.colors
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline(stringResource(title), Modifier.padding(top = 8.dp, start = 4.dp))
            BtCard {
                Column {
                    rows.forEachIndexed { i, item ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                        row(item)
                    }
                }
            }
        }
    }
}

/** This month's figure: what's still to pay, and what that leaves in the accounts. The eye hides what's in them. */
@Composable
private fun Sum(state: PlannedUiState) {
    val c = BtTheme.colors
    val month = DateLabels.monthName(YearMonth.from(state.today).atDay(1))
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Overline(pluralStringResource(R.plurals.recurring_left_to_pay, state.toPay.size, month, state.toPay.size))
        Text(MoneyFormat.format(state.toPayTotal), style = BtTheme.type.amountLarge, color = c.textPrimary)
        if (!state.hideAmounts) {
            Text(stringResource(R.string.recurring_leaves, MoneyFormat.format(state.total, signed = true), MoneyFormat.format(state.left, signed = true)), style = BtTheme.type.caption, color = c.textSecondary)
        }
    }
}

@Composable
private fun PlannedRow(status: PlannedStatus, state: PlannedUiState, vm: PlannedViewModel) {
    val c = BtTheme.colors
    val payment = status.payment
    val category = state.category(payment.categoryId)
    val caption = if (status.isPaid) {
        stringResource(R.string.recurring_paid_on, DateLabels.relative(status.paidOn ?: state.today, state.today))
    } else {
        listOfNotNull(DateLabels.relative(payment.date, state.today), state.account(payment.accountId)?.name).joinToString(" · ")
    }
    Row(
        Modifier.fillMaxWidth().clickable { vm.edit(payment) }.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).alpha(if (status.isPaid) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile(category?.icon ?: "event_available", category?.color ?: PLAIN_TILE)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(payment.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(MoneyFormat.format(payment.amount), style = BtTheme.type.amountMedium, color = c.textPrimary)
        // Paid by an entry, it stays paid until the entry goes; a tick can be taken back.
        val canTick = !status.isPaid || status.isTicked
        Box(
            Modifier.size(48.dp).clip(BtShapes.full).then(
                if (canTick) Modifier.clickable(role = Role.Checkbox, onClickLabel = stringResource(if (status.isPaid) R.string.recurring_not_paid else R.string.recurring_mark_paid)) { vm.toggleTick(status) } else Modifier,
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (status.isPaid) {
                Icon(Icons.Rounded.CheckCircle, stringResource(R.string.recurring_paid), tint = c.accentText, modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = c.textMuted, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun OfferedRow(payment: RecurringPayment, state: PlannedUiState, vm: PlannedViewModel) {
    val c = BtTheme.colors
    val category = state.category(payment.categoryId)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile(category?.icon ?: "event_repeat", category?.color ?: PLAIN_TILE)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(payment.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${payment.frequency.label} · ${DateLabels.relative(payment.nextDate, state.today)}", style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(MoneyFormat.format(payment.amountDue), style = BtTheme.type.amountMedium, color = c.textPrimary)
        BtChip(stringResource(R.string.recurring_add), selected = true, onClick = { vm.plan(payment) })
    }
}

/** Plan a payment: what it's called, how much, when, and optionally where it comes from and its category. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlannedSheet(draft: PlannedDraft, state: PlannedUiState, vm: PlannedViewModel) {
    val c = BtTheme.colors
    val category = state.category(draft.subCategoryId ?: draft.categoryId)
    val subCategories = state.subCategoriesOf(draft.categoryId)
    val monthEnd = YearMonth.from(state.today).atEndOfMonth()
    var pickDate by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = vm::closeEditor, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bgSurface) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EditorTitle(
                if (draft.isNew) stringResource(R.string.recurring_plan_a_payment) else stringResource(R.string.recurring_edit_planned),
                onRemove = if (draft.isNew) null else ({ confirmRemove = true }),
            ) { CategoryTile(category?.icon ?: "event_available", category?.color ?: PLAIN_TILE) }
            BtTextField(draft.name, stringResource(R.string.recurring_name_like_rent), vm::setName)
            BtTextField(
                value = draft.amountText,
                placeholder = "${MoneyFormat.SYMBOL}0.00",
                onChange = vm::setAmount,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            EditorField(stringResource(R.string.recurring_when)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BtChip(stringResource(R.string.recurring_end_of_month), draft.date == monthEnd, { vm.setDate(monthEnd) })
                    BtChip(
                        text = if (draft.date == monthEnd) stringResource(R.string.recurring_pick_a_day) else DateLabels.relative(draft.date, state.today),
                        selected = draft.date != monthEnd,
                        onClick = { pickDate = true },
                        leading = { Icon(Icons.Rounded.CalendarMonth, null, tint = if (draft.date != monthEnd) c.accentOn else c.textSecondary, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            EditorField(stringResource(R.string.recurring_from)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.activeAccounts.forEach { account ->
                        BtChip(account.name, account.id == draft.accountId, { vm.setAccount(account.id) }, leading = { PersonDot(account.color) })
                    }
                }
            }
            EditorField(stringResource(R.string.recurring_category_optional)) { CategoryChipRow(state.topCategories, draft.categoryId, vm::selectCategory) }
            if (subCategories.isNotEmpty()) {
                EditorField(stringResource(R.string.recurring_sub_category)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        subCategories.forEach { sub -> BtChip(sub.name, sub.id == draft.subCategoryId, { vm.selectSubCategory(sub) }) }
                    }
                }
            }
            PrimaryButton(stringResource(R.string.recurring_save), vm::saveEditor, enabled = draft.canSave)
        }
    }

    // Outside the sheet: a dialog created inside it takes on the sheet's window and sticks to the bottom edge.
    if (pickDate) BtDatePickerDialog(draft.date, onPick = vm::setDate, onClose = { pickDate = false })
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.recurring_remove_named, draft.name)) },
            text = { Text(stringResource(R.string.recurring_remove_planned_text)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeEditing() }) { Text(stringResource(R.string.recurring_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.recurring_cancel)) } },
        )
    }
}
