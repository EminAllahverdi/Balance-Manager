package com.eminallahverdi.balance.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eminallahverdi.balance.core.data.StarterTemplate
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtTextField
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.ChoiceSheet
import com.eminallahverdi.balance.core.designsystem.component.ColourPicker
import com.eminallahverdi.balance.core.designsystem.component.EditorField
import com.eminallahverdi.balance.core.designsystem.component.EditorTitle
import com.eminallahverdi.balance.core.designsystem.component.OptionRow
import com.eminallahverdi.balance.core.designsystem.component.PersonAvatar
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.component.SegmentedToggle
import com.eminallahverdi.balance.core.designsystem.icon.BtIcons
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.designsystem.theme.PersonPalette
import com.eminallahverdi.balance.core.model.Person

private const val GRID_COLUMNS = 4
private const val FALLBACK_TILE = 0xFF8A94A3
private const val SHARED_PERSON = "family"
private val TILE_SIZE = 60.dp
private val TILE_CORNER = 18.dp

@Composable
internal fun PeopleStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    val c = BtTheme.colors
    Column(Modifier.fillMaxSize()) {
        StepHeader(state.progress, vm::back)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.onboarding_who_is_it_for), style = BtTheme.type.titleLarge, color = c.textPrimary)
                Text(stringResource(R.string.onboarding_who_explained), style = BtTheme.type.body, color = c.textSecondary)
            }
            EditorField(stringResource(R.string.onboarding_your_name)) {
                BtTextField(state.ownerName, stringResource(R.string.onboarding_your_name), vm::setOwnerName, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            }
            EditorField(stringResource(R.string.onboarding_others)) {
                BtCard {
                    Column {
                        state.others.forEach { person ->
                            PersonRow(person, onClick = { vm.editPerson(person) })
                            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = vm::addPerson).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = c.accentText)
                            Text(stringResource(R.string.onboarding_add_a_person), style = BtTheme.type.bodyStrong, color = c.accentText)
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(16.dp)) {
            PrimaryButton(stringResource(R.string.onboarding_continue_), vm::next, enabled = state.ownerName.isNotBlank())
        }
    }
    state.editor?.let { PersonSheet(it, vm) }
}

@Composable
private fun PersonRow(person: PersonDraft, onClick: () -> Unit) {
    val c = BtTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PersonAvatar(Person(0, person.name, person.color))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(person.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (person.key == SHARED_PERSON) Text(stringResource(R.string.onboarding_shared_things), style = BtTheme.type.caption, color = c.textSecondary)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonSheet(editor: PersonEditor, vm: OnboardingViewModel) {
    val person = editor.draft
    ModalBottomSheet(
        onDismissRequest = vm::closeEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BtTheme.colors.bgSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EditorTitle(if (editor.isNew) stringResource(R.string.onboarding_new_person) else person.name, onRemove = if (editor.isNew) null else vm::removeEditing) {
                PersonAvatar(Person(0, person.name, person.color))
            }
            BtTextField(person.name, stringResource(R.string.onboarding_name), { vm.updateEditor(person.copy(name = it)) }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            EditorField(stringResource(R.string.onboarding_colour)) {
                ColourPicker(PersonPalette, person.color) { vm.updateEditor(person.copy(color = it)) }
            }
            PrimaryButton(stringResource(R.string.onboarding_save), vm::saveEditor, enabled = person.name.isNotBlank())
        }
    }
}

@Composable
internal fun CategoriesStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    val c = BtTheme.colors
    var pickingTemplate by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        StepHeader(state.progress, vm::back)
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.onboarding_your_categories), style = BtTheme.type.titleLarge, color = c.textPrimary)
                Text(stringResource(R.string.onboarding_categories_explained), style = BtTheme.type.body, color = c.textSecondary)
            }
            BtCard {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { pickingTemplate = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.onboarding_template), style = BtTheme.type.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Text(stringResource(state.template.label), style = BtTheme.type.caption, color = c.textSecondary)
                    Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
                }
            }
            SegmentedToggle(listOf(stringResource(R.string.onboarding_expense), stringResource(R.string.onboarding_income)), if (state.showingIncome) 1 else 0, { vm.showIncome(it == 1) }, Modifier.fillMaxWidth(), fill = true)
            LazyVerticalGrid(
                GridCells.Fixed(GRID_COLUMNS),
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.shownCategories, key = { it.key }) { category ->
                    CategoryChoice(category, keptIn = category.key !in state.dropped, onClick = { vm.toggle(category) })
                }
            }
        }
        Box(Modifier.padding(16.dp)) { PrimaryButton(stringResource(R.string.onboarding_continue_), vm::next) }
    }
    if (pickingTemplate) {
        ChoiceSheet(stringResource(R.string.onboarding_category_template), onClose = { pickingTemplate = false }) { choose ->
            CategoryTemplate.entries.forEach { template ->
                OptionRow(stringResource(template.label), template == state.template, onClick = { choose { vm.chooseTemplate(template) } }, caption = stringResource(template.caption))
            }
        }
    }
}

@Composable
private fun CategoryChoice(category: StarterTemplate.TemplateCategory, keptIn: Boolean, onClick: () -> Unit) {
    val c = BtTheme.colors
    Column(
        Modifier.clickable(role = Role.Checkbox, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val icon = category.icon ?: "more_horiz"
        if (keptIn) {
            CategoryTile(icon, category.color ?: FALLBACK_TILE, size = TILE_SIZE, iconSize = 28.dp, corner = TILE_CORNER)
        } else {
            LeftOutTile(icon)
        }
        Text(
            category.name,
            style = BtTheme.type.caption.copy(lineHeight = 15.sp, textDecoration = if (keptIn) null else TextDecoration.LineThrough),
            color = if (keptIn) c.textSecondary else c.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2,
        )
    }
}

@Composable
private fun LeftOutTile(icon: String) {
    val c = BtTheme.colors
    val shape = RoundedCornerShape(TILE_CORNER)
    Box(
        Modifier
            .size(TILE_SIZE)
            .clip(shape)
            .background(c.bgSurface)
            .drawBehind {
                drawRoundRect(
                    color = c.borderStrong,
                    cornerRadius = CornerRadius(TILE_CORNER.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) { Icon(BtIcons.of(icon), null, tint = c.textMuted, modifier = Modifier.size(28.dp)) }
}
