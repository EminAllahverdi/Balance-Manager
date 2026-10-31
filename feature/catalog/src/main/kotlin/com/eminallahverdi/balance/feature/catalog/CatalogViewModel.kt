package com.eminallahverdi.balance.feature.catalog

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.data.AvatarStore
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.RemoveResult
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.Person
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CatalogTab { EXPENSE, INCOME, PEOPLE }

data class CategoryGroup(val category: Category, val subCategories: List<Category>)

sealed interface CatalogEditor {
    data class CategoryDraft(val category: Category, val isNew: Boolean) : CatalogEditor

    /** [savedAvatar] is the photo in the database, so a picked one can be dropped if the edit is abandoned. */
    data class PersonDraft(val person: Person, val isNew: Boolean, val savedAvatar: String?) : CatalogEditor
}

data class CatalogUiState(
    val tab: CatalogTab = CatalogTab.EXPENSE,
    val allCategories: List<Category> = emptyList(),
    val allPeople: List<Person> = emptyList(),
    val expandedId: Long? = null,
    val editor: CatalogEditor? = null,
) {
    private val type: CategoryType get() = if (tab == CatalogTab.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    val groups: List<CategoryGroup> get() = groupCategories(allCategories, type)
    val archivedCategories: List<Category> get() = archivedCategories(allCategories, type)
    val people: List<Person> get() = allPeople.filterNot { it.isArchived }
    val archivedPeople: List<Person> get() = allPeople.filter { it.isArchived }
    /** Every expense needs someone it was for, so the last person stays. */
    val canRemovePerson: Boolean get() = people.size > 1
}

internal fun groupCategories(all: List<Category>, type: CategoryType): List<CategoryGroup> =
    all.filter { it.type == type && it.parentId == null && !it.isArchived }
        .map { parent -> CategoryGroup(parent, all.filter { it.parentId == parent.id && !it.isArchived }) }

/** What can be restored: a sub-category of an archived parent comes back with the parent. */
internal fun archivedCategories(all: List<Category>, type: CategoryType): List<Category> {
    val archivedIds = all.filter { it.isArchived }.map { it.id }.toSet()
    return all.filter { it.type == type && it.isArchived && it.parentId !in archivedIds }
}

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val avatars: AvatarStore,
) : ViewModel() {

    private val tab = MutableStateFlow(CatalogTab.EXPENSE)
    private val expanded = MutableStateFlow<Long?>(null)
    private val editor = MutableStateFlow<CatalogEditor?>(null)

    val state: StateFlow<CatalogUiState> = combine(catalog.categories, catalog.people, tab, expanded, editor) { categories, people, t, e, d ->
        CatalogUiState(t, categories, people, e, d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState())

    fun setTab(t: CatalogTab) = tab.update { t }
    fun toggleExpanded(id: Long) = expanded.update { if (it == id) null else id }

    fun reorderCategories(ordered: List<Category>) { viewModelScope.launch { catalog.reorderCategories(ordered) } }
    fun reorderPeople(ordered: List<Person>) { viewModelScope.launch { catalog.reorderPeople(ordered) } }
    fun restore(category: Category) { viewModelScope.launch { catalog.saveCategory(category.copy(isArchived = false)) } }
    fun restore(person: Person) { viewModelScope.launch { catalog.savePerson(person.copy(isArchived = false)) } }

    fun newCategory() {
        val s = state.value
        val type = if (s.tab == CatalogTab.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        val draft = Category(
            id = 0,
            name = "",
            type = type,
            icon = "more_horiz",
            color = Palette.first(),
            defaultPersonId = s.people.firstOrNull()?.id,
            sortOrder = s.groups.size,
        )
        editor.update { CatalogEditor.CategoryDraft(draft, isNew = true) }
    }

    fun newSubCategory(parent: CategoryGroup) {
        val p = parent.category
        val draft = Category(id = 0, name = "", type = p.type, parentId = p.id, icon = p.icon, color = p.color, sortOrder = parent.subCategories.size)
        editor.update { CatalogEditor.CategoryDraft(draft, isNew = true) }
    }

    fun newPerson() {
        val s = state.value
        val draft = Person(id = 0, name = "", color = Palette[s.people.size % Palette.size], sortOrder = s.people.size)
        editor.update { CatalogEditor.PersonDraft(draft, isNew = true, savedAvatar = null) }
    }

    fun edit(category: Category) = editor.update { CatalogEditor.CategoryDraft(category, isNew = false) }
    fun edit(person: Person) = editor.update { CatalogEditor.PersonDraft(person, isNew = false, savedAvatar = person.avatarUri) }

    fun update(category: Category) = editor.update { (it as? CatalogEditor.CategoryDraft)?.copy(category = category) ?: it }
    fun update(person: Person) = editor.update { (it as? CatalogEditor.PersonDraft)?.copy(person = person) ?: it }

    fun setPhoto(source: Uri) {
        viewModelScope.launch {
            val stored = avatars.copy(source) ?: return@launch
            replacePhoto(stored)
        }
    }

    fun removePhoto() {
        viewModelScope.launch { replacePhoto(null) }
    }

    private suspend fun replacePhoto(uri: String?) {
        val draft = editor.value as? CatalogEditor.PersonDraft ?: return
        dropUnsavedPhoto(draft)
        update(draft.person.copy(avatarUri = uri))
    }

    private suspend fun dropUnsavedPhoto(draft: CatalogEditor.PersonDraft) {
        draft.person.avatarUri?.takeIf { it != draft.savedAvatar }?.let { avatars.delete(it) }
    }

    fun closeEditor() {
        val draft = editor.value
        editor.update { null }
        if (draft is CatalogEditor.PersonDraft) viewModelScope.launch { dropUnsavedPhoto(draft) }
    }

    fun saveEditor() {
        val draft = editor.value ?: return
        editor.update { null }
        viewModelScope.launch {
            when (draft) {
                is CatalogEditor.CategoryDraft -> catalog.saveCategory(draft.category.copy(name = draft.category.name.trim()))
                is CatalogEditor.PersonDraft -> {
                    catalog.savePerson(draft.person.copy(name = draft.person.name.trim()))
                    draft.savedAvatar?.takeIf { it != draft.person.avatarUri }?.let { avatars.delete(it) }
                }
            }
        }
    }

    /** Removes what's being edited. Anything already used is archived instead, so history keeps it. */
    fun removeEditing() {
        val draft = editor.value ?: return
        editor.update { null }
        viewModelScope.launch {
            when (draft) {
                is CatalogEditor.CategoryDraft -> catalog.removeCategory(draft.category)
                is CatalogEditor.PersonDraft -> {
                    dropUnsavedPhoto(draft)
                    val stored = draft.person.copy(avatarUri = draft.savedAvatar)
                    if (catalog.removePerson(stored) == RemoveResult.DELETED) draft.savedAvatar?.let { avatars.delete(it) }
                }
            }
        }
    }
}

/** Colours offered for categories and people: the design's category hues and person colours. */
internal val Palette: List<Long> = listOf(
    0xFF5B9DFF, 0xFFFF7AB6, 0xFFFFB547, 0xFFA78BFA, 0xFF8A94A3,
    0xFF5DCAA5, 0xFFFF7A66, 0xFF8AB4F8, 0xFFB39DFF, 0xFFF5B544,
    0xFFFFD166, 0xFFFF9F6E, 0xFF7FD1F7, 0xFF9AD0A8, 0xFFF49AC2,
    0xFFC79BF2, 0xFF6FC7D9, 0xFFF7A072, 0xFFB8C0CC,
)
