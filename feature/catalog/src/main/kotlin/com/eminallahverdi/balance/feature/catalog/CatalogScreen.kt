package com.eminallahverdi.balance.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PersonAvatar
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.component.SegmentedToggle
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.Person

/** Categories, sub-categories and people. Everything the user can make their own. */
@Composable
fun CatalogRoute(onBack: () -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(BtTheme.colors.bgCanvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Text("Categories", style = BtTheme.type.titleLarge, color = BtTheme.colors.textPrimary)
            }
            SegmentedToggle(
                options = listOf("Expense", "Income", "People"),
                selected = state.tab.ordinal,
                onSelect = { viewModel.setTab(CatalogTab.entries[it]) },
                fill = true,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            )
            if (state.tab == CatalogTab.PEOPLE) PeopleList(state, viewModel) else CategoryList(state, viewModel)
        }
        AddButton(
            text = if (state.tab == CatalogTab.PEOPLE) "New person" else "New category",
            onClick = { if (state.tab == CatalogTab.PEOPLE) viewModel.newPerson() else viewModel.newCategory() },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
        )
    }
    state.editor?.let { CatalogEditorSheet(it, state, viewModel) }
}

/** Clears the add button at the end of the list. */
private val ListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp)

@Composable
private fun CategoryList(state: CatalogUiState, vm: CatalogViewModel) {
    val source = state.groups
    var order by remember(source) { mutableStateOf(source) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        list = listState,
        onMove = { from, to -> order = order.moved(from, to) { it.category.id } },
        onDrop = { vm.reorderCategories(order.map { it.category }) },
    )
    val people = state.allPeople.associateBy(Person::id)

    LazyColumn(Modifier.fillMaxSize(), listState, ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(order, key = { it.category.id }) { group ->
            val id = group.category.id
            CategoryCard(
                group = group,
                expanded = state.expandedId == id,
                people = people,
                vm = vm,
                handle = Modifier.reorderHandle(reorder, id),
                modifier = Modifier.reorderItem(reorder, id),
            )
        }
        archivedSection(state.archivedCategories, key = { "c${it.id}" }, name = { it.name }, onRestore = vm::restore)
    }
}

@Composable
private fun CategoryCard(group: CategoryGroup, expanded: Boolean, people: Map<Long, Person>, vm: CatalogViewModel, handle: Modifier, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    val category = group.category
    BtCard(modifier, bordered = expanded) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { vm.toggleExpanded(category.id) }.padding(start = 2.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DragHandle(handle)
                CategoryTile(category.icon, category.color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(category.name, style = BtTheme.type.bodyStrong, color = c.textPrimary)
                    if (group.subCategories.isNotEmpty()) {
                        Text(subCategoryCount(group.subCategories.size), style = BtTheme.type.caption, color = c.textSecondary)
                    }
                }
                people[category.defaultPersonId]?.let { PersonLabel(it) }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
            }
            if (expanded) {
                group.subCategories.forEach { sub -> SubCategoryRow(sub, people[sub.defaultPersonId ?: category.defaultPersonId], vm) }
                ActionRow(Icons.Rounded.Add, "Add sub-category") { vm.newSubCategory(group) }
                ActionRow(Icons.Rounded.Edit, "Edit category") { vm.edit(category) }
            }
        }
    }
}

private fun subCategoryCount(n: Int) = if (n == 1) "1 sub-category" else "$n sub-categories"

@Composable
private fun SubCategoryRow(sub: Category, person: Person?, vm: CatalogViewModel) {
    val c = BtTheme.colors
    Column {
        Divider()
        Row(
            Modifier.fillMaxWidth().clickable { vm.edit(sub) }.padding(start = 52.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(sub.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f))
            person?.let { PersonLabel(it) }
            Icon(Icons.Rounded.MoreVert, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    val c = BtTheme.colors
    Column {
        Divider()
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(start = 52.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = c.accentText, modifier = Modifier.size(20.dp))
            Text(text, style = BtTheme.type.label, color = c.accentText)
        }
    }
}

@Composable
private fun PeopleList(state: CatalogUiState, vm: CatalogViewModel) {
    val c = BtTheme.colors
    val source = state.people
    var order by remember(source) { mutableStateOf(source) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        list = listState,
        onMove = { from, to -> order = order.moved(from, to, Person::id) },
        onDrop = { vm.reorderPeople(order) },
    )

    LazyColumn(Modifier.fillMaxSize(), listState, ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(order, key = { it.id }) { person ->
            BtCard(Modifier.reorderItem(reorder, person.id)) {
                Row(
                    Modifier.fillMaxWidth().clickable { vm.edit(person) }.padding(start = 2.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DragHandle(Modifier.reorderHandle(reorder, person.id))
                    PersonAvatar(person)
                    Text(person.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.MoreVert, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
                }
            }
        }
        archivedSection(state.archivedPeople, key = { "p${it.id}" }, name = { it.name }, onRestore = vm::restore)
    }
}

private fun <T> LazyListScope.archivedSection(archived: List<T>, key: (T) -> String, name: (T) -> String, onRestore: (T) -> Unit) {
    if (archived.isEmpty()) return
    item(key = "archived") { Overline("Archived", Modifier.padding(top = 16.dp, start = 4.dp)) }
    items(archived, key = key) { entry ->
        BtCard {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name(entry), style = BtTheme.type.body, color = BtTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { onRestore(entry) }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Restore", style = BtTheme.type.label, color = BtTheme.colors.accentText) }
            }
        }
    }
}

@Composable
private fun DragHandle(modifier: Modifier) {
    Box(modifier.size(width = 36.dp, height = 44.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.DragIndicator, "Drag to reorder", tint = BtTheme.colors.textMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PersonLabel(person: Person) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        PersonDot(person.color, 7.dp)
        Text(person.name, style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(BtTheme.colors.borderDefault))
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.accentFill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = c.accentOn, modifier = Modifier.size(22.dp))
        Box(Modifier.width(6.dp))
        Text(text, style = BtTheme.type.bodyStrong, color = c.accentOn)
    }
}
