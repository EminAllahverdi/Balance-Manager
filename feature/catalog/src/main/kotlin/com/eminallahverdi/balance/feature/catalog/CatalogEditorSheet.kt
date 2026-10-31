package com.eminallahverdi.balance.feature.catalog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PersonAvatar
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.component.toColor
import com.eminallahverdi.balance.core.designsystem.icon.BtIcons
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogEditorSheet(editor: CatalogEditor, state: CatalogUiState, vm: CatalogViewModel) {
    var confirmRemove by remember { mutableStateOf(false) }
    val (name, canRemove) = when (editor) {
        is CatalogEditor.CategoryDraft -> editor.category.name to !editor.isNew
        is CatalogEditor.PersonDraft -> editor.person.name to (!editor.isNew && state.canRemovePerson)
    }

    ModalBottomSheet(
        onDismissRequest = vm::closeEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BtTheme.colors.bgSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val onRemove = if (canRemove) ({ confirmRemove = true }) else null
            when (editor) {
                is CatalogEditor.CategoryDraft -> CategoryFields(editor, state, vm, onRemove)
                is CatalogEditor.PersonDraft -> PersonFields(editor, vm, onRemove)
            }
            PrimaryButton("Save", vm::saveEditor, enabled = name.isNotBlank())
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove $name?") },
            text = { Text("If it's been used, it's archived so your history stays the same.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; vm.removeEditing() }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFields(editor: CatalogEditor.CategoryDraft, state: CatalogUiState, vm: CatalogViewModel, onRemove: (() -> Unit)?) {
    val category = editor.category
    val sub = category.isSubCategory
    val kind = if (sub) "sub-category" else "category"
    SheetTitle(if (editor.isNew) "New $kind" else "Edit $kind", onRemove) {
        CategoryTile(category.icon, category.color)
    }
    NameField(category.name, placeholder = "Name") { vm.update(category.copy(name = it)) }
    Field("For whom") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BtChip(if (sub) "Same as category" else "No default", category.defaultPersonId == null, { vm.update(category.copy(defaultPersonId = null)) })
            state.people.forEach { person -> PersonChip(person, category, vm) }
        }
    }
    Field("Icon") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BtIcons.catalog.keys.forEach { key ->
                val on = key == category.icon
                Box(
                    Modifier
                        .clip(RoundedCornerShape(15.dp))
                        .then(if (on) Modifier.border(2.dp, BtTheme.colors.accentFill, RoundedCornerShape(15.dp)) else Modifier)
                        .clickable(role = Role.RadioButton) { vm.update(category.copy(icon = key)) }
                        .padding(3.dp),
                ) { CategoryTile(key, category.color, size = 44.dp) }
            }
        }
    }
    Field("Colour") {
        ColourPicker(category.color) { vm.update(category.copy(color = it)) }
    }
}

@Composable
private fun PersonChip(person: Person, category: Category, vm: CatalogViewModel) {
    BtChip(
        text = person.name,
        selected = category.defaultPersonId == person.id,
        onClick = { vm.update(category.copy(defaultPersonId = person.id)) },
        leading = { PersonDot(person.color) },
    )
}

@Composable
private fun PersonFields(editor: CatalogEditor.PersonDraft, vm: CatalogViewModel, onRemove: (() -> Unit)?) {
    val person = editor.person
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setPhoto) }
    SheetTitle(if (editor.isNew) "New person" else "Edit person", onRemove) {
        PersonAvatar(person)
    }
    NameField(person.name, placeholder = "Name") { vm.update(person.copy(name = it)) }
    Field("Photo") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BtChip("Choose photo", false, { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            if (person.avatarUri != null) BtChip("Remove photo", false, vm::removePhoto)
        }
    }
    Field("Colour") {
        ColourPicker(person.color) { vm.update(person.copy(color = it)) }
    }
}

@Composable
private fun SheetTitle(title: String, onRemove: (() -> Unit)?, preview: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        preview()
        Text(title, style = BtTheme.type.titleMedium, color = BtTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        if (onRemove != null) {
            Box(
                Modifier.heightIn(min = 44.dp).clip(BtShapes.full).clickable(role = Role.Button, onClick = onRemove).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Remove", style = BtTheme.type.label, color = BtTheme.colors.moneyExpense) }
        }
    }
}

@Composable
private fun Field(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline(title)
        content()
    }
}

@Composable
private fun NameField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val c = BtTheme.colors
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(BtShapes.md).background(c.bgSurfaceRaised).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(placeholder, style = BtTheme.type.body, color = c.textMuted)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = BtTheme.type.body.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.accentFill),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourPicker(selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Palette.forEach { color ->
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.RadioButton) { onSelect(color) }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(color.toColor()), contentAlignment = Alignment.Center) {
                    if (color == selected) Icon(Icons.Rounded.Check, "Selected", tint = Color(0xFF0B0D10), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
