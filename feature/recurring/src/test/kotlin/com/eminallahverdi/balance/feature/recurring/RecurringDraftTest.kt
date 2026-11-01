package com.eminallahverdi.balance.feature.recurring

import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.RecurringFrequency
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class RecurringDraftTest {
    private val today = LocalDate.of(2026, 11, 1)
    private val subscriptions = Category(1, "Subscriptions", CategoryType.EXPENSE, icon = "subscriptions", color = 0xFFB39DFF)
    private val apps = Category(2, "Apps", CategoryType.EXPENSE, parentId = 1, icon = "apps", color = 0xFFB39DFF)
    private val ready = newDraft(today, accountId = 7).copy(name = " Spotify ", amountText = "16.31", categoryId = 1)

    @Test fun `a new payment starts a month away`() {
        assertThat(newDraft(today, accountId = 7).nextDate).isEqualTo(LocalDate.of(2026, 12, 1))
    }

    @Test fun `needs a name, an amount, a category and an account`() {
        assertThat(ready.canSave).isTrue()
        assertThat(ready.copy(name = "  ").canSave).isFalse()
        assertThat(ready.copy(amountText = "").canSave).isFalse()
        assertThat(ready.copy(amountText = "0").canSave).isFalse()
        assertThat(ready.copy(amountText = "-4").canSave).isFalse()
        assertThat(ready.copy(amountText = "1.2.3").canSave).isFalse()
        assertThat(ready.copy(categoryId = null).canSave).isFalse()
        assertThat(ready.copy(accountId = null).canSave).isFalse()
    }

    @Test fun `saves the trimmed name and the amount in minor units`() {
        val payment = ready.toPayment()!!
        assertThat(payment.name).isEqualTo("Spotify")
        assertThat(payment.amount).isEqualTo(Money(16_31))
        assertThat(payment.categoryId).isEqualTo(1)
    }

    @Test fun `a comma works as the decimal point`() {
        assertThat(ready.copy(amountText = "4,40").amount).isEqualTo(Money(4_40))
    }

    @Test fun `a sub-category is what gets saved`() {
        assertThat(ready.copy(subCategoryId = 2).toPayment()!!.categoryId).isEqualTo(2)
    }

    @Test fun `editing splits a sub-category back out and keeps the rest`() {
        val payment = RecurringPayment(
            id = 5,
            name = "Spotify",
            amount = Money(1_184_00),
            accountId = 7,
            categoryId = 2,
            personId = 3,
            frequency = RecurringFrequency.YEARLY,
            nextDate = LocalDate.of(2027, 2, 28),
            anchorDay = 31,
        )
        val draft = payment.toDraft(listOf(subscriptions, apps))
        assertThat(draft.categoryId).isEqualTo(1)
        assertThat(draft.subCategoryId).isEqualTo(2)
        assertThat(draft.amountText).isEqualTo("1184.00")
        assertThat(draft.toPayment()).isEqualTo(payment)
    }
}
