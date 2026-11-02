package com.eminallahverdi.balance.feature.transaction

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.ChoiceSheet
import com.eminallahverdi.balance.core.designsystem.component.OptionRow
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PersonAvatar
import com.eminallahverdi.balance.core.designsystem.component.SegmentedToggle
import com.eminallahverdi.balance.core.designsystem.component.toColor
import com.eminallahverdi.balance.core.designsystem.icon.BtIcons
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.designsystem.theme.tileGlyph
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.TransactionKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

private const val GRID_COLUMNS = 4
private const val SHORT_SCREEN_DP = 800

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
        // Back steps out of the keyboard and the entry first, which the sheet can't do on its own.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler {
            if (!viewModel.back()) scope.launch { sheet.hide() }.invokeOnCompletion { onClose() }
        }
        Column(Modifier.fillMaxWidth().animateContentSize().navigationBarsPadding().imePadding()) {
            if (state.editing != null) EditHeader(onDelete = { confirmDelete = true })
            when (state.step) {
                QuickAddStep.CATEGORY -> CategoryStep(state, viewModel, Modifier.weight(1f, fill = false))
                QuickAddStep.ENTRY -> EntryStep(state, viewModel, onOpenCalendar = { showCalendar = true }, Modifier.weight(1f, fill = false))
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

@Composable
private fun KindToggle(state: QuickAddUiState, vm: QuickAddViewModel) {
    val kinds = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.TRANSFER)
    SegmentedToggle(
        options = listOf("Expense", "Income", "Transfer"),
        selected = kinds.indexOf(state.kind),
        onSelect = { vm.setKind(kinds[it]) },
        fill = true,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth(),
    )
}

// ---------------------------------------------------------------- the grid

@Composable
private fun CategoryStep(state: QuickAddUiState, vm: QuickAddViewModel, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        KindToggle(state, vm)
        val frequent = state.frequent
        if (frequent.isNotEmpty()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Overline("Frequent", Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    frequent.forEach { item -> FrequentChip(item, vm, Modifier.weight(1f)) }
                    repeat(FREQUENT_MERCHANTS - frequent.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Overline("All categories", Modifier.weight(1f))
            // A bank notification being edited already has its amount.
            state.amount?.let { Text(MoneyFormat.format(it), style = BtTheme.type.amountSmall, color = BtTheme.colors.textSecondary) }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.categories.chunked(GRID_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { category -> CategoryCell(category, selected = category.id == state.categoryId, vm, Modifier.weight(1f)) }
                    repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FrequentChip(item: FrequentMerchant, vm: QuickAddViewModel, modifier: Modifier) {
    val c = BtTheme.colors
    BtChip(
        text = item.merchant.name,
        selected = false,
        onClick = { vm.selectFrequent(item) },
        modifier = modifier,
        leading = { Icon(BtIcons.of(item.category.icon), null, tint = c.tileGlyph(item.category.color.toColor()), modifier = Modifier.size(18.dp)) },
    )
}

@Composable
private fun CategoryCell(category: Category, selected: Boolean, vm: QuickAddViewModel, modifier: Modifier) {
    val c = BtTheme.colors
    Column(
        modifier.clip(BtShapes.md).clickable(role = Role.RadioButton) { vm.selectCategory(category) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(if (selected) Modifier.border(2.dp, c.accentFill, RoundedCornerShape(21.dp)).padding(3.dp) else Modifier.padding(3.dp)) {
            CategoryTile(category.icon, category.color, size = 60.dp, iconSize = 28.dp, corner = 18.dp)
        }
        Text(
            category.name,
            style = BtTheme.type.caption.copy(lineHeight = 15.sp),
            color = if (selected) c.textPrimary else c.textSecondary,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- the entry

/** Everything on one page, with Save always in reach. The page scrolls when the keyboard leaves it too little room. */
@Composable
private fun EntryStep(state: QuickAddUiState, vm: QuickAddViewModel, onOpenCalendar: () -> Unit, modifier: Modifier) {
    val transfer = state.kind == TransactionKind.TRANSFER
    Column(modifier) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (transfer) KindToggle(state, vm) else EntryHeader(state, vm)
            AmountLine(state)
            if (transfer) {
                TransferAccounts(state, vm)
            } else {
                if (state.subCategories.isNotEmpty()) SubCategories(state, vm)
                Merchants(state, vm)
                ForWhom(state, vm)
            }
            DateAndNote(state, vm, onOpenCalendar)
        }
        when (val typing = state.typing) {
            null -> Keypad(state, vm)
            else -> TextBar(typing, state, vm)
        }
    }
}

@Composable
private fun EntryHeader(state: QuickAddUiState, vm: QuickAddViewModel) {
    val c = BtTheme.colors
    val income = state.kind == TransactionKind.INCOME
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 44.dp).clip(BtShapes.md).clickable(role = Role.Button, onClickLabel = "Change category", onClick = vm::showCategories),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.category?.let { CategoryTile(it.icon, it.color) }
            Text(state.category?.name ?: "Pick a category", style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Rounded.ExpandMore, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
        AccountPicker(label = if (income) "To" else "From", accounts = state.accounts, selected = state.account, onSelect = vm::selectAccount)
    }
}

@Composable
private fun AmountLine(state: QuickAddUiState) {
    val c = BtTheme.colors
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = MoneyFormat.SYMBOL + state.amountText.ifEmpty { "0" },
            style = BtTheme.type.amountDisplay,
            color = if (state.amountText.isEmpty()) c.textMuted else c.textPrimary,
            maxLines = 1,
        )
        if (state.typing == null) Box(Modifier.width(2.dp).height(40.dp).background(c.accentFill))
    }
}

@Composable
private fun AccountPicker(label: String, accounts: List<Account>, selected: Account?, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.clip(BtShapes.sm).clickable { open = true }.padding(4.dp)) {
        Overline(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AccountBalanceWallet, null, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(selected?.name ?: "Account", style = BtTheme.type.bodyStrong, color = BtTheme.colors.textPrimary)
            Icon(Icons.Rounded.ExpandMore, null, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
    if (open) {
        ChoiceSheet("$label account", onClose = { open = false }) { choose ->
            accounts.forEach { a ->
                OptionRow(
                    text = a.name,
                    selected = a.id == selected?.id,
                    onClick = { choose { onSelect(a.id) } },
                    leading = { CategoryTile(a.icon, a.color, size = 32.dp, iconSize = 18.dp, corner = 10.dp) },
                )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubCategories(state: QuickAddUiState, vm: QuickAddViewModel) {
    Section("Sub-category") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.subCategories.forEach { sub ->
                BtChip(sub.name, sub.id == state.subCategoryId, { vm.selectSubCategory(sub.id) }, outlined = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Merchants(state: QuickAddUiState, vm: QuickAddViewModel) {
    Section("Merchant") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.merchantChips.forEach { name ->
                BtChip(name, name.equals(state.merchant.trim(), ignoreCase = true), { vm.toggleMerchant(name) }, Modifier.widthIn(max = 160.dp), outlined = true)
            }
            IconChip(Icons.Rounded.Edit, "Type a merchant", selected = state.typing == QuickAddText.MERCHANT) { vm.startTyping(QuickAddText.MERCHANT) }
        }
    }
}

@Composable
private fun ForWhom(state: QuickAddUiState, vm: QuickAddViewModel) {
    val c = BtTheme.colors
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
}

/** One line whatever's in it: an older date takes Yesterday's place, and a long note shortens rather than wrapping. */
@Composable
private fun DateAndNote(state: QuickAddUiState, vm: QuickAddViewModel, onOpenCalendar: () -> Unit) {
    val c = BtTheme.colors
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val older = state.date != today && state.date != yesterday
    Section("Date and note") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BtChip("Today", state.date == today, { vm.setDate(today) }, outlined = true)
            if (older) {
                BtChip(
                    DateLabels.relative(state.date),
                    selected = true,
                    onClick = onOpenCalendar,
                    leading = { Icon(Icons.Rounded.CalendarMonth, null, tint = c.textSecondary, modifier = Modifier.size(16.dp)) },
                    outlined = true,
                )
            } else {
                BtChip("Yesterday", state.date == yesterday, { vm.setDate(yesterday) }, outlined = true)
                IconChip(Icons.Rounded.CalendarMonth, "Pick an older date", selected = false, onClick = onOpenCalendar)
            }
            BtChip(
                text = state.note.ifBlank { "Note" },
                selected = state.note.isNotBlank(),
                onClick = { vm.startTyping(QuickAddText.NOTE) },
                modifier = Modifier.weight(1f, fill = false),
                leading = { Icon(Icons.AutoMirrored.Rounded.Notes, null, tint = c.textSecondary, modifier = Modifier.size(16.dp)) },
                outlined = true,
            )
        }
    }
}

/** A chip that's only an icon, like the pencil beside the merchants. */
@Composable
private fun IconChip(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    val c = BtTheme.colors
    Box(
        Modifier
            .height(36.dp)
            .width(40.dp)
            .clip(BtShapes.full)
            .background(if (selected) c.bgSurfaceHigh else c.bgSurfaceRaised)
            .border(1.dp, if (selected) c.accentFill else c.borderDefault, BtShapes.full)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (selected) c.textPrimary else c.textSecondary, modifier = Modifier.size(18.dp)) }
}

/**
 * Sits above the phone's keyboard, which takes the number pad's place. Save is here too, so the last thing typed
 * doesn't need Done and then Save. The keyboard's own Done, or Back, brings the number pad back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextBar(typing: QuickAddText, state: QuickAddUiState, vm: QuickAddViewModel) {
    val c = BtTheme.colors
    val isMerchant = typing == QuickAddText.MERCHANT
    val focus = remember { FocusRequester() }
    // What's there starts selected, so typing replaces it and a tap keeps it to correct.
    var field by remember(typing) {
        val current = if (isMerchant) state.merchant else state.note
        mutableStateOf(TextFieldValue(current, TextRange(0, current.length)))
    }
    fun change(value: TextFieldValue) {
        field = value
        if (isMerchant) vm.setMerchant(value.text) else vm.setNote(value.text)
    }
    LaunchedEffect(typing) { focus.requestFocus() }

    // Back hides the keyboard before the app hears about it, so its going away is what ends the typing.
    val keyboardOpen = WindowInsets.isImeVisible
    var keyboardHasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardOpen) {
        if (keyboardOpen) keyboardHasOpened = true else if (keyboardHasOpened) vm.stopTyping()
    }

    Column(Modifier.fillMaxWidth().background(c.bgSurface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderStrong))
        if (isMerchant && (state.merchantMatches.isNotEmpty() || state.isNewMerchant)) {
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.merchantMatches.forEach { name -> BtChip(name, selected = false, onClick = { vm.pickMerchant(name) }) }
                if (state.isNewMerchant) {
                    BtChip(
                        "Add “${state.merchant.trim()}”",
                        selected = false,
                        onClick = vm::stopTyping,
                        leading = { Icon(Icons.Rounded.Add, null, tint = c.textSecondary, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(c.bgSurfaceRaised)
                    .border(1.dp, c.accentFill, RoundedCornerShape(14.dp)).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(if (isMerchant) Icons.Rounded.Storefront else Icons.AutoMirrored.Rounded.Notes, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (field.text.isEmpty()) Text(if (isMerchant) "Shop or service" else "Add a note", style = BtTheme.type.body, color = c.textMuted, maxLines = 1)
                    BasicTextField(
                        value = field,
                        onValueChange = ::change,
                        singleLine = true,
                        textStyle = BtTheme.type.body.copy(color = c.textPrimary),
                        cursorBrush = SolidColor(c.accentFill),
                        keyboardOptions = KeyboardOptions(
                            capitalization = if (isMerchant) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { vm.stopTyping() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (field.text.isNotEmpty()) {
                    Box(
                        Modifier.size(width = 40.dp, height = 44.dp).clip(CircleShape).clickable(role = Role.Button) { change(TextFieldValue()) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Close, "Clear", tint = c.textMuted, modifier = Modifier.size(18.dp)) }
                } else {
                    Spacer(Modifier.width(4.dp))
                }
            }
            Row(
                Modifier.height(44.dp).clip(BtShapes.full).background(if (state.canSave) c.accentFill else c.bgSurfaceHigh)
                    .clickable(enabled = state.canSave, role = Role.Button, onClick = vm::save).padding(start = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Rounded.Check, null, tint = if (state.canSave) c.accentOn else c.textMuted, modifier = Modifier.size(20.dp))
                Text("Save", style = BtTheme.type.bodyStrong, color = if (state.canSave) c.accentOn else c.textMuted)
            }
        }
    }
}

@Composable
private fun Keypad(state: QuickAddUiState, vm: QuickAddViewModel) {
    val c = BtTheme.colors
    val rows = listOf("123", "456", "789", ".0<")
    // Shorter keys on a shorter phone, so two lines of sub-categories still fit without scrolling.
    val keyHeight = if (LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_DP) 46.dp else 54.dp
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.weight(1f).height(keyHeight).clip(BtShapes.key).background(c.bgSurfaceRaised)
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
                Modifier.fillMaxWidth().height(keyHeight).clip(BtShapes.key).background(c.bgSurfaceHigh).clickable(role = Role.Button, onClick = vm::clear),
                contentAlignment = Alignment.Center,
            ) { Text("C", style = BtTheme.type.amountLarge.copy(fontSize = 22.sp), color = c.textPrimary) }
            Column(
                Modifier.fillMaxWidth().height(keyHeight * 3 + 16.dp).clip(BtShapes.key)
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

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Overline(title, Modifier.padding(bottom = 8.dp))
        content()
    }
}
