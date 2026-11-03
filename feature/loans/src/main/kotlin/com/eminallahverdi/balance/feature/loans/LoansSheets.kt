package com.eminallahverdi.balance.feature.loans

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.BtDatePickerDialog
import com.eminallahverdi.balance.core.designsystem.component.BtSwitch
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
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.DebtDirection
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.core.model.interestLeft
import com.eminallahverdi.balance.core.model.totalCost
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
        OptionRow("I borrowed money", selected = false, onClick = { vm.newDebt(DebtKind.BORROWED) }, caption = "Into your account, or one already running. It isn't income")
        OptionRow("I lent money", selected = false, onClick = { vm.newDebt(DebtKind.LENT) }, caption = "Out of your account, or one already running. It isn't spending")
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorField("Amount borrowed", Modifier.weight(1f)) {
                BtTextField(draft.principalText, "Optional", { vm.updateLoan { copy(principalText = it) } }, keyboardOptions = AmountKeyboard)
            }
            EditorField("Interest, % a year", Modifier.weight(1f)) {
                BtTextField(draft.rateText, "Optional", { vm.updateLoan { copy(rateText = it) } }, keyboardOptions = AmountKeyboard)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorField("Months in all", Modifier.weight(1f)) {
                BtTextField(draft.termText, "0", { vm.updateLoan { copy(termText = it) } }, keyboardOptions = NumberKeyboard)
            }
            EditorField("Already paid", Modifier.weight(1f)) {
                BtTextField(draft.paymentsMadeText, "0", { vm.updateLoan { copy(paymentsMadeText = it) } }, keyboardOptions = NumberKeyboard)
            }
        }
        EditorField("Monthly payment") {
            BtTextField(draft.amountText, "${MoneyFormat.SYMBOL}0.00", { vm.updateLoan { copy(amountText = it) } }, keyboardOptions = AmountKeyboard)
        }
        draft.suggestedAmount?.let { suggested ->
            BtChip("Use ${MoneyFormat.format(suggested)}, from the amount, rate and months", selected = false, onClick = { vm.updateLoan { copy(amountText = suggested.toFieldText()) } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorField("Last payment, if different", Modifier.weight(1f)) {
                BtTextField(draft.lastAmountText, "${MoneyFormat.SYMBOL}0.00", { vm.updateLoan { copy(lastAmountText = it) } }, keyboardOptions = AmountKeyboard)
            }
            EditorField("Commission", Modifier.weight(1f)) {
                BtTextField(draft.commissionText, "Optional", { vm.updateLoan { copy(commissionText = it) } }, keyboardOptions = AmountKeyboard)
            }
        }
        EditorField("Category") { CategoryChipRow(state.topCategories, draft.categoryId, vm::selectLoanCategory) }
        if (subCategories.isNotEmpty()) {
            EditorField("Sub-category") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subCategories.forEach { sub -> BtChip(sub.name, sub.id == draft.subCategoryId, { vm.selectLoanSubCategory(sub) }) }
                }
            }
        }
        EditorField("Payments from") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountChips(state, draft.accountId) { id -> vm.updateLoan { copy(accountId = id) } }
                Text("Adding a loan moves no money. Each payment comes out of this account when you log it.", style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
            }
        }
        EditorField("Next payment") { DateChip(draft.nextDate, state.today) { pickDate = true } }
        draft.preview?.let { LoanSums(it) }
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

/** What's left on the loan as typed, and how much of it is interest once the rate is known. */
@Composable
private fun LoanSums(loan: RecurringPayment) {
    val c = BtTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(BtShapes.md).background(c.bgSurfaceRaised).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("${MoneyFormat.format(loan.stillToPay)} left to pay", style = BtTheme.type.bodyStrong, color = c.textPrimary)
        loan.interestLeft?.let { Text("About ${MoneyFormat.format(it)} of it is interest", style = BtTheme.type.caption, color = c.textSecondary) }
        loan.totalCost?.let { Text("Interest and commission over the whole loan: ${MoneyFormat.format(it)}", style = BtTheme.type.caption, color = c.textSecondary) }
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
            when {
                draft.kind == DebtKind.PAID_FOR_ME -> "Your balance stays the same for now. It becomes an expense in this category when you pay them back."
                draft.isRunning && draft.kind == DebtKind.BORROWED -> "Nothing moves now: the money came in before. What you pay back from here on leaves an account, and isn't spending."
                draft.isRunning -> "Nothing moves now: the money went out before. What they pay back from here on comes into an account, and isn't income."
                draft.kind == DebtKind.BORROWED -> "Adds to your account. It isn't income, and paying it back isn't spending."
                else -> "Takes from your account. It isn't spending, and getting it back isn't income."
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
        val amountField = @Composable {
            BtTextField(draft.amountText, "${MoneyFormat.SYMBOL}0.00", { vm.updateDebt { copy(amountText = it) } }, keyboardOptions = AmountKeyboard)
        }
        // A running loan has two sums, so this one says it's the whole of it.
        if (draft.isRunning) EditorField(if (draft.kind == DebtKind.BORROWED) "Borrowed in all" else "Lent in all") { amountField() } else amountField()
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Already running", style = BtTheme.type.bodyStrong, color = c.textPrimary)
                    Text("It started before today, and some may be paid back", style = BtTheme.type.caption, color = c.textSecondary)
                }
                BtSwitch(draft.running) { on -> vm.updateDebt { copy(running = on) } }
            }
            if (draft.isRunning) {
                EditorField(if (draft.kind == DebtKind.BORROWED) "Already paid back" else "Already paid back to you") {
                    BtTextField(draft.paidBeforeText, "${MoneyFormat.SYMBOL}0.00", { vm.updateDebt { copy(paidBeforeText = it) } }, keyboardOptions = AmountKeyboard)
                }
            } else {
                EditorField(if (draft.kind == DebtKind.BORROWED) "Into" else "From") { AccountChips(state, draft.accountId) { id -> vm.updateDebt { copy(accountId = id) } } }
            }
            EditorField("Payments left") {
                BtTextField(draft.paymentsLeftText, "Optional, like 5", { vm.updateDebt { copy(paymentsLeftText = it) } }, keyboardOptions = NumberKeyboard)
            }
            draft.left?.let { left ->
                val plan = draft.monthly?.let { " · ${MoneyFormat.format(it)} a month" }.orEmpty()
                Text("${MoneyFormat.format(left)} left$plan", style = BtTheme.type.caption, color = c.textSecondary)
            }
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
    val canRemove = draft.isSingle && !first.hasRepayments
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
