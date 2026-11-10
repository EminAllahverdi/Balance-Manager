package com.eminallahverdi.balance.feature.catalog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.BtTextField
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.ColourPicker
import com.eminallahverdi.balance.core.designsystem.component.EditorField
import com.eminallahverdi.balance.core.designsystem.component.EditorTitle
import com.eminallahverdi.balance.core.designsystem.component.IconPicker
import com.eminallahverdi.balance.core.designsystem.component.PersonAvatar
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.designsystem.theme.CategoryPalette
import com.eminallahverdi.balance.core.designsystem.theme.PersonPalette
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
            PrimaryButton(stringResource(R.string.catalog_save), vm::saveEditor, enabled = name.isNotBlank())
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.catalog_remove_named, name)) },
            text = { Text(stringResource(R.string.catalog_remove_text)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; vm.removeEditing() }) { Text(stringResource(R.string.catalog_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.catalog_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFields(editor: CatalogEditor.CategoryDraft, state: CatalogUiState, vm: CatalogViewModel, onRemove: (() -> Unit)?) {
    val category = editor.category
    val sub = category.isSubCategory
    val title = when {
        editor.isNew -> if (sub) R.string.catalog_new_sub_category else R.string.catalog_new_category
        else -> if (sub) R.string.catalog_edit_sub_category else R.string.catalog_edit_category
    }
    EditorTitle(stringResource(title), onRemove) {
        CategoryTile(category.icon, category.color)
    }
    BtTextField(category.name, stringResource(R.string.catalog_name), { vm.update(category.copy(name = it)) })
    EditorField(stringResource(R.string.catalog_for_whom)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BtChip(if (sub) stringResource(R.string.catalog_same_as_category) else stringResource(R.string.catalog_no_default), category.defaultPersonId == null, { vm.update(category.copy(defaultPersonId = null)) })
            state.people.forEach { person -> PersonChip(person, category, vm) }
        }
    }
    EditorField(stringResource(R.string.catalog_icon)) {
        IconPicker(category.color, category.icon, { vm.update(category.copy(icon = it)) })
    }
    EditorField(stringResource(R.string.catalog_colour)) {
        ColourPicker(CategoryPalette, category.color, icon = category.icon) { vm.update(category.copy(color = it)) }
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
    EditorTitle(if (editor.isNew) stringResource(R.string.catalog_new_person) else stringResource(R.string.catalog_edit_person), onRemove) {
        PersonAvatar(person)
    }
    BtTextField(person.name, stringResource(R.string.catalog_name), { vm.update(person.copy(name = it)) })
    EditorField(stringResource(R.string.catalog_photo)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BtChip(stringResource(R.string.catalog_choose_photo), false, { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            if (person.avatarUri != null) BtChip(stringResource(R.string.catalog_remove_photo), false, vm::removePhoto)
        }
    }
    EditorField(stringResource(R.string.catalog_colour)) {
        ColourPicker(PersonPalette, person.color) { vm.update(person.copy(color = it)) }
    }
}
