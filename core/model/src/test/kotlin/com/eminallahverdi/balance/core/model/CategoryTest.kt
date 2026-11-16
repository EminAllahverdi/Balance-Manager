package com.eminallahverdi.balance.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryTest {
    private val old = Category(1, "Cafe", CategoryType.EXPENSE, icon = "restaurant", color = 0xFFFF9F6E)
    private val new = old.copy(icon = "local_cafe", color = 0xFF5DCAA5)

    @Test fun `sub-category with inherited style follows its parent`() {
        val sub = Category(2, "Delivery", CategoryType.EXPENSE, parentId = 1, icon = old.icon, color = old.color)
        val followed = sub.followParentStyle(old, new)
        assertThat(followed.icon).isEqualTo(new.icon)
        assertThat(followed.color).isEqualTo(new.color)
    }

    @Test fun `sub-category keeps what it set itself`() {
        val sub = Category(2, "Coffee shop", CategoryType.EXPENSE, parentId = 1, icon = "spa", color = old.color)
        val followed = sub.followParentStyle(old, new)
        assertThat(followed.icon).isEqualTo("spa")
        assertThat(followed.color).isEqualTo(new.color)
    }

    @Test fun `names compare the same with a dotted capital or a plain i`() {
        assertThat("İlkin".caseKey()).isEqualTo("ilkin".caseKey())
        assertThat("ŞƏKİ".caseKey()).isEqualTo("şəki")
        assertThat("SAM".caseKey()).isEqualTo("sam")
    }

    @Test fun `a sub-category with its parent's colour shows one of its own, the same wherever it's drawn`() {
        val car = Category(1, "Car", CategoryType.EXPENSE, icon = "directions_car", color = 0xFF111111)
        val fuel = Category(2, "Fuel", CategoryType.EXPENSE, parentId = 1, icon = "directions_car", color = 0xFF111111, sortOrder = 0)
        val wash = Category(3, "Wash", CategoryType.EXPENSE, parentId = 1, icon = "directions_car", color = 0xFF111111, sortOrder = 1)
        val own = Category(4, "Own", CategoryType.EXPENSE, parentId = 1, icon = "directions_car", color = 0xFF444444, sortOrder = 2)
        val siblings = listOf(own, wash, fuel)
        val palette = listOf(0xFF111111, 0xFFAAAAAA, 0xFFBBBBBB)
        assertThat(fuel.shownColour(car, siblings, palette)).isEqualTo(0xFFAAAAAA)
        assertThat(wash.shownColour(car, siblings, palette)).isEqualTo(0xFFBBBBBB)
        assertThat(own.shownColour(car, siblings, palette)).isEqualTo(0xFF444444)
        assertThat(car.shownColour(car, siblings, palette)).isEqualTo(0xFF111111)
    }
}
