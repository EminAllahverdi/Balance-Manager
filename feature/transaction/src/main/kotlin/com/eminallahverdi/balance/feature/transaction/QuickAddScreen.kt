package com.eminallahverdi.balance.feature.transaction

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PersonAvatar
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.component.SegmentedToggle
import com.eminallahverdi.balance.core.designsystem.icon.BtIcons
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.TransactionKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/** The quick-add sheet. [onClose] is called after saving or when the user dismisses it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddRoute(onClose: () -> Unit, viewModel: QuickAddViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Nothing shows until the data is in, so the sheet rises at its final height instead of growing once it's up.
    if (state.loading) return

    val c = BtTheme.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            sheet.hide()
            onClose()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        shape = BtShapes.sheet,
        containerColor = c.bgSurface,
        scrimColor = c.scrim,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp).width(36.dp).height(4.dp).clip(BtShapes.full).background(c.borderStrong))
        },
        contentWindowInsets = { WindowInsets(0) },
        // Back steps out of the details first, which the sheet can't do on its own.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler {
            if (state.step == QuickAddStep.DETAILS) viewModel.showAmount() else scope.launch { sheet.hide() }.invokeOnCompletion { onClose() }
        }
        Column(Modifier.fillMaxWidth().animateContentSize().navigationBarsPadding().imePadding()) {
            if (state.editing != null) EditHeader(onDelete = { confirmDelete = true })
            when (state.step) {
                QuickAddStep.AMOUNT -> AmountStep(state, viewModel)
                QuickAddStep.DETAILS -> DetailsStep(state, viewModel, onOpenCalendar = { showCalendar = true })
            }
        }
    }

    // Outside the sheet: a dialog created inside it takes on the sheet's window and sticks to the bottom edge.
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this transaction?") },
            text = { Text("It's removed from your history and balances. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (showCalendar) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showCalendar = false
                }) { Text("Use date") }
            },
            dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun EditHeader(onDelete: () -> Unit) {
    val c = BtTheme.colors
    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Edit transaction", style = BtTheme.type.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
        Box(
            Modifier.heightIn(min = 44.dp).clip(BtShapes.full).clickable(role = Role.Button, onClick = onDelete).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Delete", style = BtTheme.type.label, color = c.moneyExpense) }
    }
}

// ---------------------------------------------------------------- step 1

@Composable
private fun AmountStep(state: QuickAddUiState, vm: QuickAddViewModel) {
    val kinds = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.TRANSFER)
    Column {
        SegmentedToggle(
            options = listOf("Expense", "Income", "Transfer"),
            selected = kinds.indexOf(state.kind),
            onSelect = { vm.setKind(kinds[it]) },
            fill = true,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth(),
        )
        AmountBox(state, vm)
        if (state.kind == TransactionKind.TRANSFER) {
            TransferAccounts(state, vm)
        } else {
            Column(Modifier.padding(top = 16.dp)) {
                Overline("Category", Modifier.padding(start = 16.dp, bottom = 8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.categories, key = { it.id }) { cat ->
                        val on = cat.id == state.categoryId
                        Column(
                            Modifier.width(64.dp).clickable(role = Role.RadioButton) { vm.selectCategory(cat) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(if (on) Modifier.border(2.dp, BtTheme.colors.accentFill, RoundedCornerShape(19.dp)).padding(3.dp) else Modifier.padding(3.dp)) {
                                CategoryTile(cat.icon, cat.color, size = 52.dp, iconSize = 24.dp, corner = 16.dp)
                            }
                            Text(cat.name, style = BtTheme.type.caption.copy(fontSize = 11.sp), color = if (on) BtTheme.colors.textPrimary else BtTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            DetailsSummary(state, vm)
        }
        Spacer(Modifier.height(12.dp))
        Keypad(state, vm)
    }
}

@Composable
private fun AmountBox(state: QuickAddUiState, vm: QuickAddViewModel) {
    val c = BtTheme.colors
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.bgSurfaceRaised)
            .border(1.dp, if (state.step == QuickAddStep.AMOUNT) c.accentFill else c.borderDefault, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Overline("Amount")
            Text(
                text = MoneyFormat.SYMBOL + state.amountText.ifEmpty { "0" },
                style = BtTheme.type.amountDisplay,
                color = if (state.amountText.isEmpty()) c.textMuted else c.textPrimary,
                maxLines = 1,
            )
        }
        AccountPicker(label = if (state.kind == TransactionKind.INCOME) "To" else "From", accounts = state.accounts, selected = state.account, onSelect = vm::selectAccount)
    }
}

@Composable
private fun AccountPicker(label: String, accounts: List<Account>, selected: Account?, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.clip(BtShapes.sm).clickable { open = true }.padding(4.dp)) {
            Overline(label)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountBalanceWallet, null, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(selected?.name ?: "Account", style = BtTheme.type.bodyStrong, color = BtTheme.colors.textPrimary)
                Icon(Icons.Rounded.ExpandMore, null, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            accounts.forEach { a ->
                DropdownMenuItem(text = { Text(a.name) }, onClick = { onSelect(a.id); open = false }, leadingIcon = { Icon(BtIcons.of(a.icon), null) })
            }
        }
    }
}

@Composable
private fun TransferAccounts(state: QuickAddUiState, vm: QuickAddViewModel) {
    Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { AccountPicker("From", state.accounts, state.account, vm::selectAccount) }
        Icon(Icons.Rounded.ArrowForward, null, tint = BtTheme.colors.textSecondary)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            AccountPicker("To", state.accounts.filter { it.id != state.accountId }, state.accounts.firstOrNull { it.id == state.toAccountId }, vm::selectToAccount)
        }
    }
}

@Composable
private fun DetailsSummary(state: QuickAddUiState, vm: QuickAddViewModel) {
    val c = BtTheme.colors
    val parts = listOfNotNull(
        state.subCategory?.name,
        state.merchant.takeIf { it.isNotBlank() },
        state.person?.name,
        DateLabels.relative(state.date),
    )
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, c.borderDefault, RoundedCornerShape(16.dp))
            .clickable(enabled = state.categoryId != null, onClick = vm::showDetails)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Tune, null, tint = c.textSecondary)
        Column(Modifier.weight(1f)) {
            Overline("Details")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.person?.let { PersonDot(it.color, 7.dp) }
                Text(
                    if (state.categoryId == null) "Pick a category first" else parts.joinToString("  ·  "),
                    style = BtTheme.type.label,
                    color = if (state.categoryId == null) c.textMuted else c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted)
    }
}

@Composable
private fun Keypad(state: QuickAddUiState, vm: QuickAddViewModel) {
    val c = BtTheme.colors
    val rows = listOf("123", "456", "789", ".0<")
    Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.weight(1f).height(54.dp).clip(BtShapes.key).background(c.bgSurfaceRaised)
                                .clickable(role = Role.Button) { if (k == '<') vm.backspace() else vm.key(k) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (k == '<') Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = c.textPrimary)
                            else Text(k.toString(), style = BtTheme.type.amountLarge.copy(fontSize = 24.sp), color = c.textPrimary)
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(BtShapes.key).background(c.bgSurfaceHigh).clickable(role = Role.Button, onClick = vm::clear),
                contentAlignment = Alignment.Center,
            ) { Text("C", style = BtTheme.type.amountLarge.copy(fontSize = 22.sp), color = c.textPrimary) }
            Column(
                Modifier.fillMaxWidth().height(54.dp * 3 + 16.dp).clip(BtShapes.key)
                    .background(if (state.canSave) c.accentFill else c.bgSurfaceHigh)
                    .clickable(enabled = state.canSave, role = Role.Button, onClick = vm::save),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = if (state.canSave) c.accentOn else c.textMuted, modifier = Modifier.size(28.dp))
                Text("Save", style = BtTheme.type.bodyStrong, color = if (state.canSave) c.accentOn else c.textMuted)
            }
        }
    }
}

// ---------------------------------------------------------------- step 2

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(state: QuickAddUiState, vm: QuickAddViewModel, onOpenCalendar: () -> Unit) {
    val c = BtTheme.colors
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(BtShapes.full).background(c.bgSurfaceRaised).clickable(onClick = vm::showAmount), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to amount", tint = c.textPrimary)
            }
            state.category?.let { CategoryTile(it.icon, it.color, size = 36.dp, iconSize = 20.dp, corner = 10.dp) }
            Column(Modifier.weight(1f)) {
                Text(state.category?.name.orEmpty(), style = BtTheme.type.bodyStrong, color = c.textPrimary)
                Text(state.account?.name.orEmpty(), style = BtTheme.type.caption, color = c.textSecondary)
            }
            Text(state.amount?.let { MoneyFormat.format(it) } ?: "", style = BtTheme.type.amountLarge, color = c.textPrimary)
        }

        if (state.subCategories.isNotEmpty()) {
            Section("Sub-category") {
                FlowRow(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bgSurfaceRaised).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.subCategories.forEach { sub ->
                        val on = sub.id == state.subCategoryId
                        Row(
                            Modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(if (on) c.bgSurfaceHigh else c.bgSurfaceRaised)
                                .clickable(role = Role.RadioButton) { vm.selectSubCategory(sub.id) }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(BtIcons.of(sub.icon), null, tint = if (on) c.textPrimary else c.textMuted, modifier = Modifier.size(18.dp))
                            Text(sub.name, style = BtTheme.type.label, color = if (on) c.textPrimary else c.textMuted)
                        }
                    }
                }
            }
            Divider()
        }

        Section("Merchant · optional") {
            InputField(state.merchant, vm::setMerchant, "Shop or service", Icons.Rounded.Storefront)
            if (state.recentMerchants.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Recent:", style = BtTheme.type.caption, color = c.textMuted)
                    state.recentMerchants.take(4).forEach { m ->
                        Text(m, style = BtTheme.type.caption, color = c.accentText, modifier = Modifier.clickable { vm.setMerchant(m) })
                    }
                }
            }
        }
        Divider()

        Section("For whom") {
            Row(Modifier.fillMaxWidth()) {
                state.people.forEach { p ->
                    val on = p.id == state.personId
                    Column(
                        Modifier.weight(1f).clickable(role = Role.RadioButton) { vm.selectPerson(p.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PersonAvatar(p, size = 44.dp, selected = on, modifier = if (on) Modifier else Modifier.padding(4.dp))
                        Text(p.name, style = BtTheme.type.caption, color = if (on) c.textPrimary else c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Divider()

        Section("Date", trailing = "${DateLabels.relative(state.date)}, ${DateLabels.daysAgo(state.date)}".takeIf { state.date != LocalDate.now() }) {
            val today = LocalDate.now()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0L..6L).map { today.minusDays(it) }.forEach { d ->
                    val on = d == state.date
                    Column(
                        Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (on) c.accentFill else c.bgSurfaceRaised)
                            .border(1.dp, if (on) c.accentFill else c.borderDefault, RoundedCornerShape(14.dp))
                            .clickable(role = Role.RadioButton) { vm.setDate(d) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val top = when (d) { today -> "TODAY"; today.minusDays(1) -> "YEST."; else -> DateLabels.weekdayShort(d).uppercase() }
                        Text(top, style = BtTheme.type.overline.copy(fontSize = 10.sp), color = if (on) c.accentOn else c.textMuted)
                        Text(d.dayOfMonth.toString(), style = BtTheme.type.amountMedium, color = if (on) c.accentOn else c.textPrimary)
                    }
                }
                Box(
                    Modifier.width(44.dp).height(60.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, c.borderStrong, RoundedCornerShape(14.dp)).clickable(onClick = onOpenCalendar),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.CalendarMonth, "Pick an older date", tint = c.textSecondary) }
            }
        }
        Divider()

        Section("Note") { InputField(state.note, vm::setNote, "Add a note", Icons.AutoMirrored.Rounded.Notes) }

        PrimaryButton(
            text = when (state.kind) { TransactionKind.INCOME -> "Save income"; TransactionKind.TRANSFER -> "Save transfer"; else -> "Save expense" },
            onClick = vm::save,
            enabled = state.canSave,
            icon = Icons.Rounded.Check,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun Section(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.padding(bottom = 8.dp)) {
            Overline(title)
            if (trailing != null) {
                Text("  ·  $trailing", style = BtTheme.type.caption, color = BtTheme.colors.accentText)
            }
        }
        content()
    }
}

@Composable
private fun Divider() = Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(BtTheme.colors.borderDefault))

@Composable
private fun InputField(value: String, onChange: (String) -> Unit, placeholder: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val c = BtTheme.colors
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(c.bgSurfaceRaised)
            .border(BorderStroke(1.dp, c.borderDefault), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = BtTheme.type.body, color = c.textMuted)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = BtTheme.type.body.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accentFill),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
