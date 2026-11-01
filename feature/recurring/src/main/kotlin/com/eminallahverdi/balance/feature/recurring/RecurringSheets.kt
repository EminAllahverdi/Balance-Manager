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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.eminallahverdi.balance.core.designsystem.component.BtTextField
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.EditorField
import com.eminallahverdi.balance.core.designsystem.component.EditorTitle
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.component.SegmentedToggle
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.RecurringFrequency
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
            EditorTitle(if (draft.isNew) "New payment" else "Edit payment", onRemove = if (draft.isNew) null else ({ confirmRemove = true })) {
                CategoryTile(category?.icon ?: "more_horiz", category?.color ?: 0xFF8A94A3)
            }
            BtTextField(draft.name, "Name, like Spotify", vm::setName)
            BtTextField(
                value = draft.amountText,
                placeholder = "${MoneyFormat.SYMBOL}0.00",
                onChange = vm::setAmount,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            EditorField("Category") { CategoryRow(state, draft, vm) }
            if (subCategories.isNotEmpty()) {
                EditorField("Sub-category") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        subCategories.forEach { sub -> BtChip(sub.name, sub.id == draft.subCategoryId, { vm.selectSubCategory(sub) }) }
                    }
                }
            }
            EditorField("For whom") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.activePeople.forEach { person ->
                        BtChip(person.name, person.id == draft.personId, { vm.setPerson(person.id) }, leading = { PersonDot(person.color) })
                    }
                }
            }
            EditorField("From") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.activeAccounts.forEach { account ->
                        BtChip(account.name, account.id == draft.accountId, { vm.setAccount(account.id) }, leading = { PersonDot(account.color) })
                    }
                }
            }
            EditorField("Repeats") {
                val frequencies = RecurringFrequency.entries
                SegmentedToggle(frequencies.map { it.label }, frequencies.indexOf(draft.frequency), { vm.setFrequency(frequencies[it]) }, Modifier.fillMaxWidth(), fill = true)
            }
            EditorField("Next payment") {
                BtChip(
                    text = DateLabels.relative(draft.nextDate, state.today),
                    selected = false,
                    onClick = { pickDate = true },
                    leading = { Icon(Icons.Rounded.CalendarMonth, null, tint = c.textSecondary, modifier = Modifier.size(18.dp)) },
                )
            }
            PrimaryButton("Save", onSave, enabled = draft.canSave)
        }
    }

    // Outside the sheet: a dialog created inside it takes on the sheet's window and sticks to the bottom edge.
    if (pickDate) NextDateDialog(draft.nextDate, state.today, onPick = vm::setDate, onClose = { pickDate = false })
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${draft.name}?") },
            text = { Text("The reminders stop. Entries you logged for it stay in your history.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeEditing() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

/** Chips in one line, because a sheet on a sheet is too much here and there can be twenty categories. */
@Composable
private fun CategoryRow(state: RecurringUiState, draft: RecurringDraft, vm: RecurringViewModel) {
    val categories = state.topCategories
    val list = rememberLazyListState(initialFirstVisibleItemIndex = categories.indexOfFirst { it.id == draft.categoryId }.coerceAtLeast(0))
    LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories, key = { it.id }) { category ->
            BtChip(
                text = category.name,
                selected = category.id == draft.categoryId,
                onClick = { vm.selectCategory(category) },
                leading = { CategoryTile(category.icon, category.color, size = 24.dp, iconSize = 16.dp, corner = 8.dp) },
            )
        }
    }
}

/** Today onward: a reminder is for what's coming, and a past date would only show as overdue. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NextDateDialog(selected: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onClose: () -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = selected.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            private val firstDay = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= firstDay
        },
    )
    DatePickerDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onClose()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    ) { DatePicker(state = pickerState) }
}
