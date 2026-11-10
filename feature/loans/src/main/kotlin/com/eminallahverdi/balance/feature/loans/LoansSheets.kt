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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
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
private val NameKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
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
    ChoiceSheet(stringResource(R.string.loans_add), onClose = vm::closeSheet) { _ ->
        OptionRow(stringResource(R.string.loans_bank_loan), selected = false, onClick = vm::newLoan, caption = stringResource(R.string.loans_bank_loan_caption))
        OptionRow(stringResource(R.string.loans_i_paid_for_others), selected = false, onClick = vm::newBill, caption = stringResource(R.string.loans_i_paid_for_others_caption))
        OptionRow(stringResource(R.string.loans_they_paid_for_me), selected = false, onClick = { vm.newDebt(DebtKind.PAID_FOR_ME) }, caption = stringResource(R.string.loans_they_paid_for_me_caption))
        OptionRow(stringResource(R.string.loans_i_borrowed_money), selected = false, onClick = { vm.newDebt(DebtKind.BORROWED) }, caption = stringResource(R.string.loans_i_borrowed_money_caption))
        OptionRow(stringResource(R.string.loans_i_lent_money), selected = false, onClick = { vm.newDebt(DebtKind.LENT) }, caption = stringResource(R.string.loans_i_lent_money_caption))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LoanEditorSheet(draft: LoanDraft, state: LoansUiState, vm: LoansViewModel) {
    val subCategories = state.subCategoriesOf(draft.categoryId)
    var confirmRemove by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    SheetFrame(vm::closeSheet) {
        EditorTitle(if (draft.isNew) stringResource(R.string.loans_new_loan) else stringResource(R.string.loans_edit_loan), onRemove = if (draft.isNew) null else ({ confirmRemove = true })) {
            CategoryTile("account_balance", 0xFFF5B544)
        }
        BtTextField(draft.name, stringResource(R.string.loans_name_like_car_loan), { vm.updateLoan { copy(name = it) } })
        // Labels, not placeholders: filled in, these are bare numbers.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorField(stringResource(R.string.loans_amount_borrowed), Modifier.weight(1f)) {
                BtTextField(draft.principalText, stringResource(R.string.loans_optional), { vm.updateLoan { copy(principalText = it) } }, keyboardOptions = AmountKeyboard)
            }
            EditorField(stringResource(R.string.loans_interest_a_year), Modifier.weight(1f)) {
                BtTextField(draft.rateText, stringResource(R.string.loans_optional), { vm.updateLoan { copy(rateText = it) } }, keyboardOptions = AmountKeyboard)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorField(stringResource(R.string.loans_months_in_all), Modifier.weight(1f)) {
                BtTextField(draft.termText, "0", { vm.updateLoan { copy(termText = it) } }, keyboardOptions = NumberKeyboard)
            }
            EditorField(stringResource(R.string.loans_already_paid), Modifier.weight(1f)) {
                BtTextField(draft.paymentsMadeText, "0", { vm.updateLoan { copy(paymentsMadeText = it) } }, keyboardOptions = NumberKeyboard)
            }
        }
        EditorField(stringResource(R.string.loans_monthly_payment)) {
            BtTextField(draft.amountText, "${MoneyFormat.SYMBOL}0.00", { vm.updateLoan { copy(amountText = it) } }, keyboardOptions = AmountKeyboard)
        }
        draft.suggestedAmount?.let { suggested ->
            BtChip(stringResource(R.string.loans_use_suggested, MoneyFormat.format(suggested)), selected = false, onClick = { vm.updateLoan { copy(amountText = suggested.toFieldText()) } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorField(stringResource(R.string.loans_last_payment_if_different), Modifier.weight(1f)) {
                BtTextField(draft.lastAmountText, "${MoneyFormat.SYMBOL}0.00", { vm.updateLoan { copy(lastAmountText = it) } }, keyboardOptions = AmountKeyboard)
            }
            EditorField(stringResource(R.string.loans_commission), Modifier.weight(1f)) {
                BtTextField(draft.commissionText, stringResource(R.string.loans_optional), { vm.updateLoan { copy(commissionText = it) } }, keyboardOptions = AmountKeyboard)
            }
        }
        EditorField(stringResource(R.string.loans_category)) { CategoryChipRow(state.topCategories, draft.categoryId, vm::selectLoanCategory) }
        if (subCategories.isNotEmpty()) {
            EditorField(stringResource(R.string.loans_sub_category)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subCategories.forEach { sub -> BtChip(sub.name, sub.id == draft.subCategoryId, { vm.selectLoanSubCategory(sub) }) }
                }
            }
        }
        EditorField(stringResource(R.string.loans_payments_from)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountChips(state, draft.accountId) { id -> vm.updateLoan { copy(accountId = id) } }
                Text(stringResource(R.string.loans_loan_moves_no_money), style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
            }
        }
        EditorField(stringResource(R.string.loans_next_payment)) { DateChip(draft.nextDate, state.today) { pickDate = true } }
        draft.preview?.let { LoanSums(it) }
        PrimaryButton(stringResource(R.string.loans_save), vm::saveLoan, enabled = draft.canSave)
    }

    // Outside the sheet: a dialog created inside it takes on the sheet's window and sticks to the bottom edge.
    if (pickDate) {
        BtDatePickerDialog(draft.nextDate, onPick = { date -> vm.updateLoan { copy(nextDate = date, anchorDay = date.dayOfMonth) } }, onClose = { pickDate = false }, earliest = state.today)
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.loans_remove_named, draft.name)) },
            text = { Text(stringResource(R.string.loans_remove_loan_text)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeLoan() }) { Text(stringResource(R.string.loans_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.loans_cancel)) } },
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
        Text(stringResource(R.string.loans_left_to_pay, MoneyFormat.format(loan.stillToPay)), style = BtTheme.type.bodyStrong, color = c.textPrimary)
        loan.interestLeft?.let { Text(stringResource(R.string.loans_about_interest_it, MoneyFormat.format(it)), style = BtTheme.type.caption, color = c.textSecondary) }
        loan.totalCost?.let { Text(stringResource(R.string.loans_interest_commission_total, MoneyFormat.format(it)), style = BtTheme.type.caption, color = c.textSecondary) }
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
        Text(stringResource(R.string.loans_new_debt), style = BtTheme.type.titleMedium, color = c.textPrimary)
        SegmentedToggle(KINDS.map { stringResource(it.label) }, KINDS.indexOf(draft.kind), { i -> vm.updateDebt { copy(kind = KINDS[i]) } }, Modifier.fillMaxWidth(), fill = true)
        Text(
            when {
                draft.kind == DebtKind.PAID_FOR_ME -> stringResource(R.string.loans_paid_for_me_explained)
                draft.isRunning && draft.kind == DebtKind.BORROWED -> stringResource(R.string.loans_running_borrowed_explained)
                draft.isRunning -> stringResource(R.string.loans_running_lent_explained)
                draft.kind == DebtKind.BORROWED -> stringResource(R.string.loans_borrowed_explained)
                else -> stringResource(R.string.loans_lent_explained)
            },
            style = BtTheme.type.caption,
            color = c.textSecondary,
        )
        BtTextField(draft.counterparty, stringResource(R.string.loans_who), { vm.updateDebt { copy(counterparty = it) } })
        if (names.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEach { name -> BtChip(name, false, { vm.updateDebt { copy(counterparty = name) } }) }
            }
        }
        val amountField = @Composable {
            BtTextField(draft.amountText, "${MoneyFormat.SYMBOL}0.00", { vm.updateDebt { copy(amountText = it) } }, keyboardOptions = AmountKeyboard)
        }
        // A running loan has two sums, so this one says it's the whole of it.
        if (draft.isRunning) EditorField(if (draft.kind == DebtKind.BORROWED) stringResource(R.string.loans_borrowed_in_all) else stringResource(R.string.loans_lent_in_all)) { amountField() } else amountField()
        if (draft.kind == DebtKind.PAID_FOR_ME) {
            EditorField(stringResource(R.string.loans_category)) { CategoryChipRow(state.topCategories, draft.categoryId, vm::selectDebtCategory) }
            if (subCategories.isNotEmpty()) {
                EditorField(stringResource(R.string.loans_sub_category)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        subCategories.forEach { sub -> BtChip(sub.name, sub.id == draft.subCategoryId, { vm.selectDebtSubCategory(sub) }) }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.loans_already_running), style = BtTheme.type.bodyStrong, color = c.textPrimary)
                    Text(stringResource(R.string.loans_already_running_caption), style = BtTheme.type.caption, color = c.textSecondary)
                }
                BtSwitch(draft.running) { on -> vm.updateDebt { copy(running = on) } }
            }
            if (draft.isRunning) {
                EditorField(if (draft.kind == DebtKind.BORROWED) stringResource(R.string.loans_already_paid_back) else stringResource(R.string.loans_already_paid_back_to_you)) {
                    BtTextField(draft.paidBeforeText, "${MoneyFormat.SYMBOL}0.00", { vm.updateDebt { copy(paidBeforeText = it) } }, keyboardOptions = AmountKeyboard)
                }
            } else {
                EditorField(if (draft.kind == DebtKind.BORROWED) stringResource(R.string.loans_into) else stringResource(R.string.loans_from)) { AccountChips(state, draft.accountId) { id -> vm.updateDebt { copy(accountId = id) } } }
            }
            EditorField(stringResource(R.string.loans_payments_left)) {
                BtTextField(draft.paymentsLeftText, stringResource(R.string.loans_optional_like_5), { vm.updateDebt { copy(paymentsLeftText = it) } }, keyboardOptions = NumberKeyboard)
            }
            draft.left?.let { left ->
                val plan = draft.monthly?.let { " · " + stringResource(R.string.loans_a_month, MoneyFormat.format(it)) }.orEmpty()
                Text(stringResource(R.string.loans_left_suffix, MoneyFormat.format(left)) + plan, style = BtTheme.type.caption, color = c.textSecondary)
            }
        }
        BtTextField(draft.note, stringResource(R.string.loans_note_like_friday_lunch), { vm.updateDebt { copy(note = it) } })
        EditorField(stringResource(R.string.loans_date)) { DateChip(draft.date, state.today) { pickDate = true } }
        PrimaryButton(stringResource(R.string.loans_save), vm::saveDebt, enabled = draft.canSave)
    }

    if (pickDate) {
        BtDatePickerDialog(draft.date, onPick = { date -> vm.updateDebt { copy(date = date) } }, onClose = { pickDate = false }, latest = state.today)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BillEditorSheet(draft: BillDraft, state: LoansUiState, vm: LoansViewModel) {
    val c = BtTheme.colors
    val subCategories = state.subCategoriesOf(draft.categoryId)
    val named = draft.others.map { it.lowercase() }.toSet()
    val names = state.knownNames.filter { it.lowercase() !in named }
    var pickDate by remember { mutableStateOf(false) }

    SheetFrame(vm::closeSheet) {
        EditorTitle(stringResource(R.string.loans_i_paid_for_others)) { CategoryTile("handshake", 0xFF8A94A3) }
        Text(
            stringResource(R.string.loans_bill_explained),
            style = BtTheme.type.caption,
            color = c.textSecondary,
        )
        EditorField(stringResource(R.string.loans_amount_paid)) {
            BtTextField(draft.totalText, "${MoneyFormat.SYMBOL}0.00", { vm.updateBill { copy(totalText = it) } }, keyboardOptions = AmountKeyboard)
        }
        EditorField(stringResource(R.string.loans_who_owes_you)) {
            BtTextField(draft.who, stringResource(R.string.loans_names_hint), { vm.updateBill { copy(who = it) } }, keyboardOptions = NameKeyboard)
        }
        if (names.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEach { name -> BtChip(name, false, { vm.addToBill(name) }) }
            }
        }
        EditorField(stringResource(R.string.loans_category)) { CategoryChipRow(state.topCategories, draft.categoryId, vm::selectBillCategory) }
        if (subCategories.isNotEmpty()) {
            EditorField(stringResource(R.string.loans_sub_category)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subCategories.forEach { sub -> BtChip(sub.name, sub.id == draft.subCategoryId, { vm.selectBillSubCategory(sub) }) }
                }
            }
        }
        EditorField(stringResource(R.string.loans_merchant)) { BtTextField(draft.merchant, stringResource(R.string.loans_where_like_bravo), { vm.updateBill { copy(merchant = it) } }) }
        EditorField(stringResource(R.string.loans_your_share)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BtTextField(draft.mineText, "${MoneyFormat.SYMBOL}0.00", { vm.updateBill { copy(mineText = it) } }, keyboardOptions = AmountKeyboard)
                draft.evenSplit?.let { even ->
                    val people = draft.others.size + 1
                    BtChip(stringResource(R.string.loans_split_evenly, people, MoneyFormat.format(even)), draft.mine == even, { vm.updateBill { copy(mineText = even.toFieldText()) } })
                }
                Text(shareCaption(draft), style = BtTheme.type.caption, color = c.textSecondary)
            }
        }
        EditorField(stringResource(R.string.loans_for_whom)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.activePeople.forEach { person ->
                    BtChip(person.name, person.id == draft.personId, { vm.updateBill { copy(personId = person.id) } }, leading = { PersonDot(person.color) })
                }
            }
        }
        EditorField(stringResource(R.string.loans_from)) { AccountChips(state, draft.accountId) { id -> vm.updateBill { copy(accountId = id) } } }
        EditorField(stringResource(R.string.loans_date)) { DateChip(draft.date, state.today) { pickDate = true } }
        BtTextField(draft.note, stringResource(R.string.loans_note_like_friday_lunch), { vm.updateBill { copy(note = it) } })
        PrimaryButton(draft.total?.let { stringResource(R.string.loans_save_amount, MoneyFormat.format(it)) } ?: stringResource(R.string.loans_save), vm::saveBill, enabled = draft.canSave)
    }

    if (pickDate) {
        BtDatePickerDialog(draft.date, onPick = { date -> vm.updateBill { copy(date = date) } }, onClose = { pickDate = false }, latest = state.today)
    }
}

/** What saving will do with the money, in plain words, so the split is never a surprise. */
@Composable
private fun shareCaption(draft: BillDraft): String {
    val mine = draft.mine
    val owed = draft.owed
    return when {
        mine == null -> stringResource(R.string.loans_not_an_amount)
        draft.total != null && owed == null -> stringResource(R.string.loans_share_not_whole)
        owed != null && !mine.isZero -> stringResource(R.string.loans_share_spending_now, MoneyFormat.format(mine), MoneyFormat.format(owed))
        else -> stringResource(R.string.loans_share_leave_empty)
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
        EditorTitle(stringResource(if (iOwe) R.string.loans_pay_back_named else R.string.loans_named_paid_back, first.debt.counterparty), onRemove = if (canRemove) ({ confirmRemove = true }) else null) {
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
                    where != null && iOwe -> stringResource(R.string.loans_as_expense_in, amount, where)
                    iOwe -> stringResource(R.string.loans_as_debt_payment, amount)
                    else -> stringResource(R.string.loans_back_into_account, amount)
                }
                Text(line, style = BtTheme.type.caption, color = c.textSecondary)
            }
        }
        EditorField(if (iOwe) stringResource(R.string.loans_from) else stringResource(R.string.loans_into)) { AccountChips(state, draft.accountId) { id -> vm.updatePay { copy(accountId = id) } } }
        EditorField(stringResource(R.string.loans_date)) { DateChip(draft.date, state.today) { pickDate = true } }
        val action = if (iOwe) stringResource(R.string.loans_pay) else stringResource(R.string.loans_add)
        PrimaryButton(if (total != null) stringResource(R.string.loans_action_amount, action, MoneyFormat.format(total)) else action, vm::confirmPay, enabled = draft.canPay)
        if (draft.canMarkShare && total != null) {
            // What nobody is sending back: the user's own part of the bill.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BtChip(stringResource(R.string.loans_is_my_share, MoneyFormat.format(total)), selected = false, onClick = vm::markShare)
                Text(
                    stringResource(R.string.loans_share_spending_explained, state.categoryPath(first.debt.categoryId).orEmpty()),
                    style = BtTheme.type.caption,
                    color = c.textSecondary,
                )
            }
        }
    }

    if (pickDate) {
        BtDatePickerDialog(draft.date, onPick = { date -> vm.updatePay { copy(date = date) } }, onClose = { pickDate = false }, latest = state.today)
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.loans_remove_debt_q)) },
            text = { Text(stringResource(R.string.loans_remove_debt_text)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeDebt(first.debt) }) { Text(stringResource(R.string.loans_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.loans_cancel)) } },
        )
    }
}

/** A new name for someone outside the household, on every debt with them, so a typo doesn't mean starting again. */
@Composable
internal fun RenameSheet(rename: LoansSheet.Rename, state: LoansUiState, vm: LoansViewModel) {
    val c = BtTheme.colors
    val count = state.debts.count { it.debt.counterparty.trim().equals(rename.from.trim(), ignoreCase = true) }
    // Someone else already called this: their debts come together, which is how a second spelling is folded in.
    val joining = state.knownNames.firstOrNull { it.equals(rename.text.trim(), ignoreCase = true) && !it.equals(rename.from.trim(), ignoreCase = true) }
    SheetFrame(vm::closeSheet) {
        EditorTitle(stringResource(R.string.loans_change_the_name)) { CategoryTile("handshake", 0xFF8A94A3) }
        BtTextField(rename.text, stringResource(R.string.loans_name), vm::updateRename, keyboardOptions = NameKeyboard)
        Text(
            buildString {
                append(pluralStringResource(R.plurals.loans_rename_changes, count, count, rename.from))
                if (joining != null) append(" " + stringResource(R.string.loans_rename_join, joining))
            },
            style = BtTheme.type.caption,
            color = c.textSecondary,
        )
        PrimaryButton(stringResource(R.string.loans_save), vm::saveRename, enabled = rename.canSave)
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
