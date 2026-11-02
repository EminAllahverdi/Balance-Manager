package com.eminallahverdi.balance.feature.transaction

import com.eminallahverdi.balance.core.data.Suggestion
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.AccountType
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.Merchant
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.PendingEntry
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.TransactionKind
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class QuickAddFlowTest {
    private val groceries = Category(1, "Groceries", CategoryType.EXPENSE, icon = "shopping_cart", color = 0)
    private val subscriptions = Category(2, "Subscriptions", CategoryType.EXPENSE, icon = "subscriptions", color = 0)
    private val apps = Category(3, "Apps", CategoryType.EXPENSE, parentId = 2, icon = "apps", color = 0)
    private val salary = Category(4, "Salary", CategoryType.INCOME, icon = "payments", color = 0)
    private val categories = listOf(groceries, subscriptions, apps, salary)

    private val card = Account(1, "Card", AccountType.DEBIT_CARD, "payments", 0)
    private val cash = Account(2, "Cash", AccountType.CASH, "payments", 0)
    private val me = Person(1, "Me", 0)
    private val wife = Person(2, "Wife", 0)

    private val zeta = Merchant(1, "Zeta Market", lastCategoryId = 1, lastAccountId = 2, lastPersonId = 2, useCount = 40)
    private val cloudy = Merchant(2, "Cloudy", lastCategoryId = 3, lastAccountId = 1, lastPersonId = 1, useCount = 30)
    private val rarely = Merchant(3, "Rarely", lastCategoryId = 1, useCount = 2)
    private val zetaKiosk = Merchant(4, "Kiosk Zeta", lastCategoryId = 1, useCount = 90)

    private val state = QuickAddUiState(allCategories = categories, accounts = listOf(card, cash), accountId = 1, people = listOf(me, wife))
    private val suggestion = Suggestion(subCategoryId = 3, merchantName = "Last shop", personId = 1, accountId = 1, recentMerchants = listOf("Last shop", "Cloudy"))

    @Test fun `a new expense or income starts on the grid`() {
        assertThat(startStep(TransactionKind.EXPENSE, categoryId = null)).isEqualTo(QuickAddStep.CATEGORY)
        assertThat(startStep(TransactionKind.INCOME, categoryId = null)).isEqualTo(QuickAddStep.CATEGORY)
    }

    @Test fun `a transfer has no grid`() {
        assertThat(startStep(TransactionKind.TRANSFER, categoryId = null)).isEqualTo(QuickAddStep.ENTRY)
    }

    @Test fun `an entry that arrives with a category skips the grid`() {
        assertThat(startStep(TransactionKind.EXPENSE, categoryId = 1)).isEqualTo(QuickAddStep.ENTRY)
    }

    @Test fun `back closes the keyboard, then returns to the grid, then lets the sheet close`() {
        val typing = state.copy(step = QuickAddStep.ENTRY, pickedFromGrid = true, typing = QuickAddText.NOTE)
        val entry = typing.afterBack()!!
        assertThat(entry.typing).isNull()
        assertThat(entry.step).isEqualTo(QuickAddStep.ENTRY)
        val grid = entry.afterBack()!!
        assertThat(grid.step).isEqualTo(QuickAddStep.CATEGORY)
        assertThat(grid.afterBack()).isNull()
    }

    @Test fun `an entry that didn't come from the grid closes on back`() {
        assertThat(state.copy(step = QuickAddStep.ENTRY).afterBack()).isNull()
    }

    @Test fun `frequent merchants are the most used, of this kind of category`() {
        val income = Merchant(9, "Employer", lastCategoryId = 4, useCount = 99)
        val frequent = frequentMerchants(listOf(rarely, cloudy, income, zeta), categories, CategoryType.EXPENSE)
        assertThat(frequent.map { it.merchant.name }).containsExactly("Zeta Market", "Cloudy", "Rarely").inOrder()
    }

    @Test fun `a merchant whose category or parent was archived isn't frequent`() {
        val archived = listOf(groceries.copy(isArchived = true), subscriptions.copy(isArchived = true), apps)
        assertThat(frequentMerchants(listOf(zeta, cloudy), archived, CategoryType.EXPENSE)).isEmpty()
    }

    @Test fun `there are never more than three`() {
        assertThat(frequentMerchants(listOf(zeta, cloudy, rarely, zetaKiosk), categories, CategoryType.EXPENSE)).hasSize(3)
    }

    @Test fun `a transfer has no frequent merchants`() {
        assertThat(state.copy(kind = TransactionKind.TRANSFER, merchants = listOf(zeta)).frequent).isEmpty()
    }

    @Test fun `a frequent merchant wins over the category's last entry`() {
        val picked = state.copy(categoryId = 2).withSuggestion(suggestion, frequent = cloudy)
        assertThat(picked.merchant).isEqualTo("Cloudy")
        assertThat(picked.subCategoryId).isEqualTo(3L)
        assertThat(picked.accountId).isEqualTo(1L)

        val other = state.copy(categoryId = 1).withSuggestion(suggestion, frequent = zeta)
        assertThat(other.subCategoryId).isNull()
        assertThat(other.personId).isEqualTo(2L)
        assertThat(other.accountId).isEqualTo(2L)
    }

    @Test fun `with no frequent merchant the category's last entry fills it in`() {
        val picked = state.copy(categoryId = 2).withSuggestion(suggestion)
        assertThat(picked.merchant).isEqualTo("Last shop")
        assertThat(picked.subCategoryId).isEqualTo(3L)
    }

    @Test fun `a bank notification keeps its merchant and account`() {
        val pending = PendingEntry(1, "x", "Bank", Money(5_00), TransactionKind.EXPENSE, "SHOP", Instant.EPOCH)
        val picked = state.copy(categoryId = 2, merchant = "Shop", accountId = 2, pending = pending).withSuggestion(suggestion)
        assertThat(picked.merchant).isEqualTo("Shop")
        assertThat(picked.accountId).isEqualTo(2L)
    }

    @Test fun `matches come from every category, those starting with the text first`() {
        assertThat(merchantMatches(listOf(zetaKiosk, zeta, cloudy), "zet")).containsExactly("Zeta Market", "Kiosk Zeta").inOrder()
    }

    @Test fun `an exact match isn't offered back, and nothing typed matches nothing`() {
        assertThat(merchantMatches(listOf(cloudy), " cloudy ")).isEmpty()
        assertThat(merchantMatches(listOf(cloudy), "  ")).isEmpty()
    }

    @Test fun `only a name you don't have yet counts as new`() {
        val known = state.copy(merchants = listOf(cloudy))
        assertThat(known.copy(merchant = "Disney").isNewMerchant).isTrue()
        assertThat(known.copy(merchant = " cloudy").isNewMerchant).isFalse()
        assertThat(known.copy(merchant = " ").isNewMerchant).isFalse()
    }

    @Test fun `a typed merchant sits in front of the usual ones`() {
        val usual = listOf("One", "Two", "Three", "Four", "Five")
        assertThat(state.copy(recentMerchants = usual, merchant = "New").merchantChips).containsExactly("New", "One", "Two", "Three").inOrder()
    }

    @Test fun `a usual merchant stays in its place when picked`() {
        val usual = listOf("One", "Two", "Three")
        assertThat(state.copy(recentMerchants = usual, merchant = "two").merchantChips).containsExactly("One", "Two", "Three").inOrder()
    }
}
