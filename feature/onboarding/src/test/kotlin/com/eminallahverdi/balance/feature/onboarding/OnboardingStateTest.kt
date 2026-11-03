package com.eminallahverdi.balance.feature.onboarding

import com.eminallahverdi.balance.core.data.StarterTemplate
import com.eminallahverdi.balance.core.model.CategoryType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OnboardingStateTest {
    private val people = OnboardingUiState(step = OnboardingStep.PEOPLE)

    @Test fun `the count only includes the steps this person will see`() {
        assertThat(people.copy(asksNotifications = true).progress).isEqualTo("1 of 4")
        assertThat(people.copy(asksNotifications = false).progress).isEqualTo("1 of 3")
        assertThat(people.copy(asksNotifications = true, importing = true).progress).isEqualTo("1 of 5")
    }

    @Test fun `importing comes last, after reminders, so everything is chosen before it's written`() {
        val state = people.copy(asksNotifications = true, importing = true)
        assertThat(state.steps)
            .containsExactly(OnboardingStep.PEOPLE, OnboardingStep.CATEGORIES, OnboardingStep.BACKUP, OnboardingStep.REMINDERS, OnboardingStep.IMPORT)
            .inOrder()
    }

    @Test fun `everyone is asked about backups, after categories`() {
        assertThat(OnboardingUiState(step = OnboardingStep.CATEGORIES).next).isEqualTo(OnboardingStep.BACKUP)
        assertThat(OnboardingUiState(step = OnboardingStep.BACKUP).previous).isEqualTo(OnboardingStep.CATEGORIES)
    }

    @Test fun `the last step has nothing after it, which is what finishes`() {
        val reminders = OnboardingUiState(step = OnboardingStep.REMINDERS, asksNotifications = true)
        assertThat(reminders.next).isNull()
        assertThat(OnboardingUiState(step = OnboardingStep.BACKUP).next).isNull()
        assertThat(OnboardingUiState(step = OnboardingStep.BACKUP, asksNotifications = true).next).isEqualTo(OnboardingStep.REMINDERS)
    }

    @Test fun `back from the first step is Welcome`() {
        assertThat(people.previous).isEqualTo(OnboardingStep.WELCOME)
        assertThat(OnboardingUiState(step = OnboardingStep.CATEGORIES).previous).isEqualTo(OnboardingStep.PEOPLE)
    }

    @Test fun `the template's captions match what it holds`() {
        assertThat(CategoryTemplate.HOUSEHOLD.caption).startsWith("${CategoryTemplate.HOUSEHOLD.build("").categories.count { it.type == CategoryType.EXPENSE }} categories")
        assertThat(CategoryTemplate.SIMPLE.caption).startsWith("${CategoryTemplate.SIMPLE.build("").categories.count { it.type == CategoryType.EXPENSE }} categories")
    }

    @Test fun `tapping leaves a category out, and tapping again brings it back`() {
        val groceries = people.shownCategories.first()
        val left = people.toggled(groceries)
        assertThat(left.dropped).containsExactly(groceries.name)
        assertThat(left.toggled(groceries).dropped).isEmpty()
    }

    @Test fun `the last category of each kind can't be left out`() {
        val spending = people.shownCategories
        val onlyOne = spending.drop(1).fold(people) { state, category -> state.toggled(category) }
        assertThat(onlyOne.toggled(spending.first()).dropped).doesNotContain(spending.first().name)
        val income = people.copy(showingIncome = true).shownCategories
        assertThat(onlyOne.toggled(income.first()).dropped).contains(income.first().name)
    }

    @Test fun `what was chosen is what gets written`() {
        val sam = PersonDraft("person1", " Sam ", 0xFFFF7AB6)
        val template = people.copy(ownerName = " Alex ", others = listOf(sam), dropped = setOf("Travel")).finalTemplate()
        assertThat(template.people.map { it.name }).containsExactly("Alex", "Sam").inOrder()
        assertThat(template.categories.map { it.name }).doesNotContain("Travel")
        assertThat(template.categories.first { it.name == "Groceries" }.defaultPerson).isEqualTo("me")
    }

    @Test fun `a new person gets a colour nobody has yet`() {
        val family = PersonDraft("family", "Family", 0xFF8A94A3)
        val colour = OnboardingUiState(others = listOf(family)).nextColour()
        assertThat(colour).isNotEqualTo(family.color)
        assertThat(colour).isNotEqualTo(0xFF5B9DFF)
    }

    @Test fun `the template names its people for the owner`() {
        assertThat(StarterTemplate.simple("Alex").people.first().name).isEqualTo("Alex")
    }
}
