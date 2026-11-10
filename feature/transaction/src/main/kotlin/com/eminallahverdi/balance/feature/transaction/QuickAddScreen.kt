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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.res.stringResource
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
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
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
import com.eminallahverdi.balance.core.model.DebtType
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.core.model.namesIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

private const val GRID_COLUMNS = 4
private const val SHORT_SCREEN_DP = 800

/** How much of the screen a page with the tabs takes: room for the full grid, or the keypad page of a transfer. */
private const val TABBED_SHEET_SHARE = 0.84f

/** Names offered while one is typed. */
private const val NAME_MATCHES = 4

/** The quick-add sheet. [onClose] is called after saving or when the user dismisses it. [onVoice] swaps it for listening. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddRoute(onClose: () -> Unit, onVoice: (() -> Unit)? = null, viewModel: QuickAddViewModel = hiltViewModel()) {
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
        // Every page with the tabs on it is one height, so switching between them never moves the tabs under the thumb.
        val tabbed = state.showsKinds
        val tabbedHeight = (LocalConfiguration.current.screenHeightDp * TABBED_SHEET_SHARE).dp
        Column(Modifier.fillMaxWidth().then(if (tabbed) Modifier.height(tabbedHeight) else Modifier.animateContentSize()).navigationBarsPadding().imePadding()) {
            if (state.editing != null) EditHeader(onDelete = { confirmDelete = true })
            state.heard?.let { HeardLine(it) }
            when (state.step) {
                QuickAddStep.CATEGORY -> CategoryStep(state, viewModel, onVoice.takeIf { state.editing == null && state.pending == null && state.payment == null }, Modifier.weight(1f, fill = tabbed))
                QuickAddStep.ENTRY -> EntryStep(state, viewModel, onOpenCalendar = { showCalendar = true }, Modifier.weight(1f, fill = tabbed), fill = tabbed)
                QuickAddStep.DEBT -> DebtStep(state, viewModel, onVoice, Modifier.weight(1f, fill = tabbed))
            }
        }
    }

    // Outside the sheet: a dialog created inside it takes on the sheet's window and sticks to the bottom edge.
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.quickadd_delete_transaction_q)) },
            text = { Text(stringResource(R.string.quickadd_delete_transaction_text)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.quickadd_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.quickadd_cancel)) } },
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
                }) { Text(stringResource(R.string.quickadd_use_date)) }
            },
            dismissButton = { TextButton(onClick = { showCalendar = false }) { Text(stringResource(R.string.quickadd_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun EditHeader(onDelete: () -> Unit) {
    val c = BtTheme.colors
    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.quickadd_edit_transaction), style = BtTheme.type.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
        Box(
            Modifier.heightIn(min = 44.dp).clip(BtShapes.full).clickable(role = Role.Button, onClick = onDelete).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.quickadd_delete), style = BtTheme.type.label, color = c.moneyExpense) }
    }
}

/** The sentence a spoken entry came from, so a wrong guess is easy to see and put right. */
@Composable
private fun HeardLine(text: String) {
    val c = BtTheme.colors
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().clip(BtShapes.md).background(c.bgSurfaceRaised).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Mic, null, tint = c.accentText, modifier = Modifier.size(16.dp))
        Text("“$text”", style = BtTheme.type.caption, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun KindToggle(state: QuickAddUiState, vm: QuickAddViewModel) {
    // A debt is only ever new here: something being edited, or arriving filled in, stays the kind it is.
    val new = state.editing == null && state.pending == null && state.payment == null
    val kinds = listOfNotNull(TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.TRANSFER, TransactionKind.DEBT.takeIf { new })
    SegmentedToggle(
        options = kinds.map { kind -> stringResource(kind.tab) },
        selected = kinds.indexOf(state.kind),
        onSelect = { vm.setKind(kinds[it]) },
        fill = true,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth(),
    )
}

// ---------------------------------------------------------------- the grid

@Composable
private fun CategoryStep(state: QuickAddUiState, vm: QuickAddViewModel, onVoice: (() -> Unit)?, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { KindToggle(state, vm) }
            onVoice?.let { BtIconButton(Icons.Rounded.Mic, stringResource(R.string.quickadd_add_by_voice), it, Modifier.padding(end = 16.dp)) }
        }
        // The bank's ready-made entries come first: the payment just made is usually one of them.
        if (state.debt == null) WaitingFromBank(state, vm)
        val frequent = state.frequent
        if (frequent.isNotEmpty()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Overline(stringResource(R.string.quickadd_frequent), Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    frequent.forEach { item -> FrequentChip(item, vm, Modifier.weight(1f)) }
                    repeat(FREQUENT_MERCHANTS - frequent.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Overline(stringResource(R.string.quickadd_all_categories), Modifier.weight(1f))
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
private fun EntryStep(state: QuickAddUiState, vm: QuickAddViewModel, onOpenCalendar: () -> Unit, modifier: Modifier, fill: Boolean = false) {
    val transfer = state.kind == TransactionKind.TRANSFER
    Column(modifier) {
        // [fill]: the sheet is a fixed height, and the keypad keeps to its bottom.
        Column(Modifier.weight(1f, fill = fill).verticalScroll(rememberScrollState())) {
            val debt = state.debt.takeIf { state.isDebt }
            when {
                transfer -> KindToggle(state, vm)
                debt != null && !debt.type.hasCategory -> DebtHeader(state, vm)
                else -> EntryHeader(state, vm, showAccount = debt?.type != DebtType.PAID_FOR_ME)
            }
            if (debt != null && debt.type.hasCategory) DebtKindLine(state, vm)
            AmountLine(state)
            if (debt != null) DebtCaption(state)
            if (transfer) {
                TransferAccounts(state, vm)
            } else if (debt != null) {
                if (debt.type != DebtType.PAID_BACK) DebtWho(state, vm)
                when (debt.type) {
                    DebtType.LENT, DebtType.BORROWED -> DebtParts(state, vm)
                    DebtType.PAID_FOR_OTHERS -> DebtShare(state, vm)
                    else -> Unit
                }
                if (debt.type.hasCategory) {
                    if (state.subCategories.isNotEmpty()) SubCategories(state, vm)
                    Merchants(state, vm)
                    ForWhom(state, vm)
                }
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
private fun EntryHeader(state: QuickAddUiState, vm: QuickAddViewModel, showAccount: Boolean = true) {
    val c = BtTheme.colors
    val income = state.kind == TransactionKind.INCOME
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 44.dp).clip(BtShapes.md).clickable(role = Role.Button, onClickLabel = stringResource(R.string.quickadd_change_category), onClick = vm::showCategories),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.category?.let { CategoryTile(it.icon, it.color) }
            Text(state.category?.name ?: stringResource(R.string.quickadd_pick_a_category), style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Rounded.ExpandMore, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
        if (showAccount) AccountPicker(label = if (income) stringResource(R.string.quickadd_to) else stringResource(R.string.quickadd_from), accounts = state.accounts, selected = state.account, onSelect = vm::selectAccount)
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

/** The tab a kind of entry has in quick add. */
private val TransactionKind.tab: Int
    get() = when (this) {
        TransactionKind.EXPENSE -> R.string.quickadd_expense
        TransactionKind.INCOME -> R.string.quickadd_income
        TransactionKind.TRANSFER -> R.string.quickadd_transfer
        else -> R.string.quickadd_debt
    }

@Composable
internal fun AccountPicker(label: String, accounts: List<Account>, selected: Account?, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.clip(BtShapes.sm).clickable { open = true }.padding(4.dp)) {
        Overline(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AccountBalanceWallet, null, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(selected?.name ?: stringResource(R.string.quickadd_account), style = BtTheme.type.bodyStrong, color = BtTheme.colors.textPrimary)
            Icon(Icons.Rounded.ExpandMore, null, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
    if (open) {
        ChoiceSheet(stringResource(R.string.quickadd_label_account, label), onClose = { open = false }) { choose ->
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
        Box(Modifier.weight(1f)) { AccountPicker(stringResource(R.string.quickadd_from), state.accounts, state.account, vm::selectAccount) }
        Icon(Icons.Rounded.ArrowForward, null, tint = BtTheme.colors.textSecondary)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            AccountPicker(stringResource(R.string.quickadd_to), state.accounts.filter { it.id != state.accountId }, state.accounts.firstOrNull { it.id == state.toAccountId }, vm::selectToAccount)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubCategories(state: QuickAddUiState, vm: QuickAddViewModel) {
    Section(stringResource(R.string.quickadd_sub_category)) {
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
    Section(stringResource(R.string.quickadd_merchant)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.merchantChips.forEach { name ->
                BtChip(name, name.equals(state.merchant.trim(), ignoreCase = true), { vm.toggleMerchant(name) }, Modifier.widthIn(max = 160.dp), outlined = true)
            }
            IconChip(Icons.Rounded.Edit, stringResource(R.string.quickadd_type_a_merchant), selected = state.typing == QuickAddText.MERCHANT) { vm.startTyping(QuickAddText.MERCHANT) }
        }
    }
}

@Composable
private fun ForWhom(state: QuickAddUiState, vm: QuickAddViewModel) {
    val c = BtTheme.colors
    Section(stringResource(R.string.quickadd_for_whom)) {
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
    Section(stringResource(R.string.quickadd_date_and_note)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BtChip(stringResource(R.string.quickadd_today), state.date == today, { vm.setDate(today) }, outlined = true)
            if (older) {
                BtChip(
                    DateLabels.relative(state.date),
                    selected = true,
                    onClick = onOpenCalendar,
                    leading = { Icon(Icons.Rounded.CalendarMonth, null, tint = c.textSecondary, modifier = Modifier.size(16.dp)) },
                    outlined = true,
                )
            } else {
                BtChip(stringResource(R.string.quickadd_yesterday), state.date == yesterday, { vm.setDate(yesterday) }, outlined = true)
                IconChip(Icons.Rounded.CalendarMonth, stringResource(R.string.quickadd_pick_older_date), selected = false, onClick = onOpenCalendar)
            }
            BtChip(
                text = state.note.ifBlank { stringResource(R.string.quickadd_note) },
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
internal fun IconChip(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
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
    val isName = typing == QuickAddText.NAME
    val focus = remember { FocusRequester() }
    // What's there starts selected, so typing replaces it and a tap keeps it to correct.
    var field by remember(typing) {
        val current = when (typing) {
            QuickAddText.MERCHANT -> state.merchant
            QuickAddText.NOTE -> state.note
            QuickAddText.NAME -> state.debt?.who.orEmpty()
        }
        mutableStateOf(TextFieldValue(current, TextRange(0, current.length)))
    }
    fun change(value: TextFieldValue) {
        field = value
        when (typing) {
            QuickAddText.MERCHANT -> vm.setMerchant(value.text)
            QuickAddText.NOTE -> vm.setNote(value.text)
            QuickAddText.NAME -> vm.setWho(value.text)
        }
    }
    // Names used before that start like what's typed, so the same person isn't spelt two ways.
    val nameMatches = if (isName) {
        val typed = namesIn(field.text).lastOrNull().orEmpty()
        state.knownNames.filter { typed.isNotBlank() && it.startsWith(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }.take(NAME_MATCHES)
    } else {
        emptyList()
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
        if (nameMatches.isNotEmpty()) {
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                nameMatches.forEach { name ->
                    BtChip(name, selected = false, onClick = {
                        // The typed start becomes the name it matched; a bill keeps the names before it.
                        val before = namesIn(field.text).dropLast(1)
                        change(TextFieldValue(joinNames(before + name)))
                        vm.stopTyping()
                    })
                }
            }
        }
        if (isMerchant && (state.merchantMatches.isNotEmpty() || state.isNewMerchant)) {
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.merchantMatches.forEach { name -> BtChip(name, selected = false, onClick = { vm.pickMerchant(name) }) }
                if (state.isNewMerchant) {
                    BtChip(
                        stringResource(R.string.quickadd_add_quoted, state.merchant.trim()),
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
                Icon(when { isMerchant -> Icons.Rounded.Storefront; isName -> Icons.Rounded.Person; else -> Icons.AutoMirrored.Rounded.Notes }, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (field.text.isEmpty()) Text(when { isMerchant -> stringResource(R.string.quickadd_shop_or_service); isName -> if (state.debt?.type == DebtType.PAID_FOR_OTHERS) stringResource(R.string.quickadd_names_like) else stringResource(R.string.quickadd_name); else -> stringResource(R.string.quickadd_add_a_note) }, style = BtTheme.type.body, color = c.textMuted, maxLines = 1)
                    BasicTextField(
                        value = field,
                        onValueChange = ::change,
                        singleLine = true,
                        textStyle = BtTheme.type.body.copy(color = c.textPrimary),
                        cursorBrush = SolidColor(c.accentFill),
                        keyboardOptions = KeyboardOptions(
                            capitalization = if (isMerchant || isName) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
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
                    ) { Icon(Icons.Rounded.Close, stringResource(R.string.quickadd_clear), tint = c.textMuted, modifier = Modifier.size(18.dp)) }
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
                Text(stringResource(R.string.quickadd_save), style = BtTheme.type.bodyStrong, color = if (state.canSave) c.accentOn else c.textMuted)
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
                            if (k == '<') Icon(Icons.AutoMirrored.Rounded.Backspace, stringResource(R.string.quickadd_delete), tint = c.textPrimary)
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
                Text(stringResource(R.string.quickadd_save), style = BtTheme.type.bodyStrong, color = if (state.canSave) c.accentOn else c.textMuted)
            }
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Overline(title, Modifier.padding(bottom = 8.dp))
        content()
    }
}
