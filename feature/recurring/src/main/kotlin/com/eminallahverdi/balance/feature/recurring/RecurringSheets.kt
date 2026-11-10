package com.eminallahverdi.balance.feature.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.BtDatePickerDialog
import com.eminallahverdi.balance.core.designsystem.component.BtTextField
import com.eminallahverdi.balance.core.designsystem.component.CategoryChipRow
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.EditorField
import com.eminallahverdi.balance.core.designsystem.component.EditorTitle
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.component.SegmentedToggle
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.RecurringFrequency

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RecurringEditorSheet(draft: RecurringDraft, state: RecurringUiState, vm: RecurringViewModel, onSave: () -> Unit) {
    val c = BtTheme.colors
    val category = state.category(draft.subCategoryId ?: draft.categoryId)
    val subCategories = state.subCategoriesOf(draft.categoryId)
    var confirmRemove by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = vm::closeEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bgSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EditorTitle(if (draft.isNew) stringResource(R.string.recurring_new_payment) else stringResource(R.string.recurring_edit_payment), onRemove = if (draft.isNew) null else ({ confirmRemove = true })) {
                CategoryTile(category?.icon ?: "more_horiz", category?.color ?: 0xFF8A94A3)
            }
            BtTextField(draft.name, stringResource(R.string.recurring_name_like_spotify), vm::setName)
            BtTextField(
                value = draft.amountText,
                placeholder = "${MoneyFormat.SYMBOL}0.00",
                onChange = vm::setAmount,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            EditorField(stringResource(R.string.recurring_category)) { CategoryChipRow(state.topCategories, draft.categoryId, vm::selectCategory) }
            if (subCategories.isNotEmpty()) {
                EditorField(stringResource(R.string.recurring_sub_category)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        subCategories.forEach { sub -> BtChip(sub.name, sub.id == draft.subCategoryId, { vm.selectSubCategory(sub) }) }
                    }
                }
            }
            EditorField(stringResource(R.string.recurring_for_whom)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.activePeople.forEach { person ->
                        BtChip(person.name, person.id == draft.personId, { vm.setPerson(person.id) }, leading = { PersonDot(person.color) })
                    }
                }
            }
            EditorField(stringResource(R.string.recurring_from)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.activeAccounts.forEach { account ->
                        BtChip(account.name, account.id == draft.accountId, { vm.setAccount(account.id) }, leading = { PersonDot(account.color) })
                    }
                }
            }
            EditorField(stringResource(R.string.recurring_repeats)) {
                val frequencies = RecurringFrequency.entries
                SegmentedToggle(frequencies.map { it.label }, frequencies.indexOf(draft.frequency), { vm.setFrequency(frequencies[it]) }, Modifier.fillMaxWidth(), fill = true)
            }
            EditorField(stringResource(R.string.recurring_next_payment)) {
                BtChip(
                    text = DateLabels.relative(draft.nextDate, state.today),
                    selected = false,
                    onClick = { pickDate = true },
                    leading = { Icon(Icons.Rounded.CalendarMonth, null, tint = c.textSecondary, modifier = Modifier.size(18.dp)) },
                )
            }
            PrimaryButton(stringResource(R.string.recurring_save), onSave, enabled = draft.canSave)
        }
    }

    // Outside the sheet: a dialog created inside it takes on the sheet's window and sticks to the bottom edge.
    if (pickDate) BtDatePickerDialog(draft.nextDate, onPick = vm::setDate, onClose = { pickDate = false }, earliest = state.today)
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.recurring_remove_named, draft.name)) },
            text = { Text(stringResource(R.string.recurring_remove_payment_text)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeEditing() }) { Text(stringResource(R.string.recurring_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.recurring_cancel)) } },
        )
    }
}
