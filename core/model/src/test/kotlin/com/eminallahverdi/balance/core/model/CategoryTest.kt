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
}
