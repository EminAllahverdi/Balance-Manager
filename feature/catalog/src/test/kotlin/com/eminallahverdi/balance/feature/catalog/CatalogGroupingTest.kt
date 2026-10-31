package com.eminallahverdi.balance.feature.catalog

import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CatalogGroupingTest {
    private fun category(id: Long, parentId: Long? = null, type: CategoryType = CategoryType.EXPENSE, archived: Boolean = false) =
        Category(id, "c$id", type, parentId, icon = "more_horiz", color = 0, isArchived = archived)

    @Test fun `groups active categories of the type with their active sub-categories`() {
        val all = listOf(category(1), category(2, parentId = 1), category(3, parentId = 1, archived = true), category(4, type = CategoryType.INCOME), category(5, archived = true))
        val groups = groupCategories(all, CategoryType.EXPENSE)
        assertThat(groups.map { it.category.id }).containsExactly(1L)
        assertThat(groups.single().subCategories.map { it.id }).containsExactly(2L)
    }

    @Test fun `archived list leaves out sub-categories of an archived parent`() {
        val all = listOf(category(1, archived = true), category(2, parentId = 1, archived = true), category(3), category(4, parentId = 3, archived = true))
        assertThat(archivedCategories(all, CategoryType.EXPENSE).map { it.id }).containsExactly(1L, 4L).inOrder()
    }

    @Test fun `moved puts the item where the target was`() {
        val list = listOf(1L, 2L, 3L)
        assertThat(list.moved(1, 3) { it }).containsExactly(2L, 3L, 1L).inOrder()
        assertThat(list.moved(3, 1) { it }).containsExactly(3L, 1L, 2L).inOrder()
        assertThat(list.moved(1, 9) { it }).isEqualTo(list)
    }
}
