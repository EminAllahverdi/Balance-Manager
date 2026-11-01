package com.eminallahverdi.balance.feature.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.BtDatePickerDialog
import com.eminallahverdi.balance.core.designsystem.component.BtTextField
import com.eminallahverdi.balance.core.designsystem.component.CategoryChipRow
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.ChoiceSheet
import com.eminallahverdi.balance.core.designsystem.component.EditorField
import com.eminallahverdi.balance.core.designsystem.component.EditorTitle
import com.eminallahverdi.balance.core.designsystem.component.OptionRow
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.component.SegmentedToggle
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.DebtDirection
import java.time.LocalDate

private val AmountKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
private val NumberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
private val KINDS = DebtKind.entries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BtTheme.colors.bgSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun NewEntrySheet(vm: LoansViewModel) {
    // Each option opens its own sheet in this one's place, so there's no closing first: that would close the new one too.
    ChoiceSheet("Add", onClose = vm::closeSheet) { _ ->
        OptionRow("Bank loan", selected = false, onClick = vm::newLoan, caption = "A set number of monthly payments, with a reminder for each")
        OptionRow("They paid for me", selected = false, onClick = { vm.newDebt(DebtKind.PAID_FOR_ME) }, caption = "Your share of lunch or a gift. It becomes an expense when you pay them back")
        OptionRow("I borrowed money", selected = false, onClick = { vm.newDebt(DebtKind.BORROWED) }, caption = "It goes into your account, but isn't income")
        OptionRow("I lent money", selected = false, onClick = { vm.newDebt(DebtKind.LENT) }, caption = "It leaves your account, but isn't spending")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LoanEditorSheet(draft: LoanDraft, state: LoansUiState, vm: LoansViewModel) {
    val subCategories = state.subCategoriesOf(draft.categoryId)
    var confirmRemove by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    SheetFrame(vm::closeSheet) {
        EditorTitle(if (draft.isNew) "New loan" else "Edit loan", onRemove = if (draft.isNew) null else ({ confirmRemove = true })) {
            CategoryTile("account_balance", 0xFFF5B544)
        }
        BtTextField(draft.name, "Name, like Car loan", { vm.updateLoan { copy(name = it) } })
        // Labels, not placeholders: filled in, these are bare numbers.
        EditorField("Monthly payment") {
            BtTextField(draft.amountText, "${MoneyFormat.SYMBOL}0.00", { vm.updateLoan { copy(amountText = it) } }, keyboardOptions = AmountKeyboard)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorField("Payments left", Modifier.weight(1f)) {
                BtTextField(draft.paymentsLeftText, "0", { vm.updateLoan { copy(paymentsLeftText = it) } }, keyboardOptions = NumberKeyboard)
            }
            EditorField("Already paid", Modifier.weight(1f)) {
                BtTextField(draft.paymentsMadeText, "0", { vm.updateLoan { copy(paymentsMadeText = it) } }, keyboardOptions = NumberKeyboard)
            }
        }
        EditorField("Last payment, if different") {
            BtTextField(draft.lastAmountText, "${MoneyFormat.SYMBOL}0.00", { vm.updateLoan { copy(lastAmountText = it) } }, keyboardOptions = AmountKeyboard)
        }
        EditorField("Category") { CategoryChipRow(state.topCategories, draft.categoryId, vm::selectLoanCategory) }
        if (subCategories.isNotEmpty()) {
            EditorField("Sub-category") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subCategories.forEach { sub -> BtChip(sub.name, sub.id == draft.subCategoryId, { vm.selectLoanSubCategory(sub) }) }
                }
            }
        }
        EditorField("From") { AccountChips(state, draft.accountId) { id -> vm.updateLoan { copy(accountId = id) } } }
        EditorField("Next payment") { DateChip(draft.nextDate, state.today) { pickDate = true } }
        PrimaryButton("Save", vm::saveLoan, enabled = draft.canSave)
    }

    // Outside the sheet: a dialog created inside it takes on the sheet's window and sticks to the bottom edge.
    if (pickDate) {
        BtDatePickerDialog(draft.nextDate, onPick = { date -> vm.updateLoan { copy(nextDate = date, anchorDay = date.dayOfMonth) } }, onClose = { pickDate = false }, earliest = state.today)
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${draft.name}?") },
            text = { Text("The reminders stop. Payments you logged stay in your history.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeLoan() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DebtEditorSheet(draft: DebtDraft, state: LoansUiState, vm: LoansViewModel) {
    val c = BtTheme.colors
    val subCategories = state.subCategoriesOf(draft.categoryId)
    val names = state.knownNames.filter { it.contains(draft.counterparty.trim(), ignoreCase = true) && !it.equals(draft.counterparty.trim(), ignoreCase = true) }
    var pickDate by remember { mutableStateOf(false) }

    SheetFrame(vm::closeSheet) {
        Text("New debt", style = BtTheme.type.titleMedium, color = c.textPrimary)
        SegmentedToggle(KINDS.map { it.label }, KINDS.indexOf(draft.kind), { i -> vm.updateDebt { copy(kind = KINDS[i]) } }, Modifier.fillMaxWidth(), fill = true)
        Text(
            when (draft.kind) {
                DebtKind.PAID_FOR_ME -> "Your balance stays the same for now. It becomes an expense in this category when you pay them back."
                DebtKind.BORROWED -> "Adds to your account. It isn't income, and paying it back isn't spending."
                DebtKind.LENT -> "Takes from your account. It isn't spending, and getting it back isn't income."
            },
            style = BtTheme.type.caption,
            color = c.textSecondary,
        )
        BtTextField(draft.counterparty, "Who", { vm.updateDebt { copy(counterparty = it) } })
        if (names.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEach { name -> BtChip(name, false, { vm.updateDebt { copy(counterparty = name) } }) }
            }
        }
        BtTextField(draft.amountText, "${MoneyFormat.SYMBOL}0.00", { vm.updateDebt { copy(amountText = it) } }, keyboardOptions = AmountKeyboard)
        if (draft.kind == DebtKind.PAID_FOR_ME) {
            EditorField("Category") { CategoryChipRow(state.topCategories, draft.categoryId, vm::selectDebtCategory) }
            if (subCategories.isNotEmpty()) {
                EditorField("Sub-category") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        subCategories.forEach { sub -> BtChip(sub.name, sub.id == draft.subCategoryId, { vm.selectDebtSubCategory(sub) }) }
                    }
                }
            }
        } else {
            EditorField(if (draft.kind == DebtKind.BORROWED) "Into" else "From") { AccountChips(state, draft.accountId) { id -> vm.updateDebt { copy(accountId = id) } } }
        }
        BtTextField(draft.note, "Note, like Friday lunch", { vm.updateDebt { copy(note = it) } })
        EditorField("Date") { DateChip(draft.date, state.today) { pickDate = true } }
        PrimaryButton("Save", vm::saveDebt, enabled = draft.canSave)
    }

    if (pickDate) {
        BtDatePickerDialog(draft.date, onPick = { date -> vm.updateDebt { copy(date = date) } }, onClose = { pickDate = false }, latest = state.today)
    }
}

@Composable
internal fun PaySheet(draft: PayDraft, state: LoansUiState, vm: LoansViewModel) {
    val c = BtTheme.colors
    val first = draft.debts.first()
    val iOwe = first.debt.direction == DebtDirection.I_OWE
    val total = draft.total
    val canRemove = draft.isSingle && first.paid.isZero
    var confirmRemove by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    SheetFrame(vm::closeSheet) {
        EditorTitle(if (iOwe) "Pay back ${first.debt.counterparty}" else "${first.debt.counterparty} paid back", onRemove = if (canRemove) ({ confirmRemove = true }) else null) {
            CategoryTile("handshake", 0xFF8A94A3)
        }
        if (draft.isSingle) {
            BtTextField(draft.amountText, "${MoneyFormat.SYMBOL}0.00", { vm.updatePay { copy(amountText = it) } }, keyboardOptions = AmountKeyboard)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            draft.debts.forEach { balance ->
                val amount = MoneyFormat.format(draft.amountFor(balance))
                val where = state.categoryPath(balance.debt.categoryId)
                val line = when {
                    where != null -> "$amount as an expense in $where"
                    iOwe -> "$amount as a debt payment, not spending"
                    else -> "$amount back into your account, not income"
                }
                Text(line, style = BtTheme.type.caption, color = c.textSecondary)
            }
        }
        EditorField(if (iOwe) "From" else "Into") { AccountChips(state, draft.accountId) { id -> vm.updatePay { copy(accountId = id) } } }
        EditorField("Date") { DateChip(draft.date, state.today) { pickDate = true } }
        val action = if (iOwe) "Pay" else "Add"
        PrimaryButton(if (total != null) "$action ${MoneyFormat.format(total)}" else action, vm::confirmPay, enabled = draft.canPay)
    }

    if (pickDate) {
        BtDatePickerDialog(draft.date, onPick = { date -> vm.updatePay { copy(date = date) } }, onClose = { pickDate = false }, latest = state.today)
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this debt?") },
            text = { Text("For one added by mistake. Any money it moved goes back too.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeDebt(first.debt) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChips(state: LoansUiState, selected: Long?, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.activeAccounts.forEach { account ->
            BtChip(account.name, account.id == selected, { onSelect(account.id) }, leading = { PersonDot(account.color) })
        }
    }
}

@Composable
private fun DateChip(date: LocalDate, today: LocalDate, onClick: () -> Unit) {
    BtChip(
        text = DateLabels.relative(date, today),
        selected = false,
        onClick = onClick,
        leading = { Icon(Icons.Rounded.CalendarMonth, null, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(18.dp)) },
    )
}
