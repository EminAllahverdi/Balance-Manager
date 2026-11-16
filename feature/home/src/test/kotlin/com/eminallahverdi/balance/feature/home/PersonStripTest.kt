package com.eminallahverdi.balance.feature.home

import com.eminallahverdi.balance.core.model.Person
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PersonStripTest {
    private val me = Person(1, "Me", 0)
    private val sam = Person(2, "Sam", 0)
    private val kid = Person(3, "Kid", 0)
    private val home = Person(4, "Home", 0)
    private val all = listOf(me, sam, kid, home)

    @Test fun `cards moved among themselves keep the places they had, and the rest stay put`() {
        // Only Me, Kid and Home had spending; Kid was dragged in front of Me.
        assertThat(reorderWithin(all, listOf(kid.id, me.id, home.id))).containsExactly(kid, sam, me, home).inOrder()
    }

    @Test fun `an unchanged order changes nothing`() {
        assertThat(reorderWithin(all, listOf(me.id, kid.id))).isEqualTo(all)
    }
}
