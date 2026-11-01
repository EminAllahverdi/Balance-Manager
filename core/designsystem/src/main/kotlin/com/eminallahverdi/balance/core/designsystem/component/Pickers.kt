package com.eminallahverdi.balance.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.model.Category
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Chips in one line for picking a category inside a sheet, where a sheet on a sheet is too much. */
@Composable
fun CategoryChipRow(categories: List<Category>, selectedId: Long?, onSelect: (Category) -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = categories.indexOfFirst { it.id == selectedId }.coerceAtLeast(0))
    LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories, key = { it.id }) { category ->
            BtChip(
                text = category.name,
                selected = category.id == selectedId,
                onClick = { onSelect(category) },
                leading = { CategoryTile(category.icon, category.color, size = 24.dp, iconSize = 16.dp, corner = 8.dp) },
            )
        }
    }
}

/**
 * The calendar, limited to what a date can sensibly be here: [earliest] or [latest] leave out the days past them.
 * Call it outside any sheet: a dialog created inside one takes on the sheet's window and sticks to the bottom edge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BtDatePickerDialog(selected: LocalDate, onPick: (LocalDate) -> Unit, onClose: () -> Unit, earliest: LocalDate? = null, latest: LocalDate? = null) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                (earliest == null || utcTimeMillis >= earliest.toUtcMillis()) && (latest == null || utcTimeMillis <= latest.toUtcMillis())
        },
    )
    DatePickerDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onClose()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
