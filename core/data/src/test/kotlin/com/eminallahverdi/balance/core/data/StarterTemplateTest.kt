package com.eminallahverdi.balance.core.data

import com.eminallahverdi.balance.core.model.CategoryType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StarterTemplateTest {
    private fun StarterTemplate.spending() = categories.filter { it.type == CategoryType.EXPENSE }
    private fun StarterTemplate.defaultOf(name: String) = categories.first { it.name == name }.defaultPerson

    @Test fun `the templates hold as many categories as the screen says`() {
        assertThat(StarterTemplate.household().spending()).hasSize(16)
        assertThat(StarterTemplate.simple().spending()).hasSize(8)
    }

    @Test fun `the simple template has no sub-categories and can still record income`() {
        val simple = StarterTemplate.simple()
        assertThat(simple.categories.flatMap { it.children }).isEmpty()
        assertThat(simple.categories.count { it.type == CategoryType.INCOME }).isAtLeast(1)
    }

    @Test fun `the owner's name is used and they come first`() {
        val template = StarterTemplate.household("Alex").withPeople(emptyList())
        assertThat(template.people.map { it.name }).containsExactly("Alex")
    }

    @Test fun `removing Family gives its categories to the owner`() {
        val template = StarterTemplate.household().withPeople(emptyList())
        assertThat(template.defaultOf("Groceries")).isEqualTo("me")
    }

    @Test fun `keeping Family keeps its categories, and new people join after`() {
        val family = StarterTemplate.TemplatePerson("family", "Family", 0, 1)
        val sam = StarterTemplate.TemplatePerson("person1", "Sam", 0, 2)
        val template = StarterTemplate.household().withPeople(listOf(family, sam))
        assertThat(template.defaultOf("Groceries")).isEqualTo("family")
        assertThat(template.people.map { it.key }).containsExactly("me", "family", "person1").inOrder()
    }

    @Test fun `a category with nobody by default stays that way`() {
        val template = StarterTemplate.household().withPeople(emptyList())
        assertThat(template.defaultOf("Kids & Education")).isNull()
    }

    @Test fun `what's left out is gone, with its sub-categories`() {
        val template = StarterTemplate.household().without(setOf("Travel", "Gift"))
        assertThat(template.categories.map { it.name }).containsNoneOf("Travel", "Gift")
        assertThat(template.categoryNames).doesNotContain("Hotel")
        assertThat(template.categoryNames).contains("Taxi")
    }
}
