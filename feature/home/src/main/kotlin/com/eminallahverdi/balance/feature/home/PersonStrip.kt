package com.eminallahverdi.balance.feature.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.FittedAmount
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.Person
import com.eminallahverdi.balance.core.model.PersonSpending

/** Person cards at the top of Home: the ones with spending, at most this many. */
internal const val PEOPLE_CARDS = 4

private const val LIFTED_SCALE = 1.04f
private val LIFTED_SHADOW = 12.dp
private val CARD_GAP = 8.dp

/** A row or card being dragged: raised over the rest, a little larger, with the accent round it. */
internal fun Modifier.lifted(on: Boolean): Modifier = composed {
    val c = BtTheme.colors
    val shadow = with(LocalDensity.current) { LIFTED_SHADOW.toPx() }
    if (!on) {
        this
    } else {
        zIndex(1f)
            .graphicsLayer {
                scaleX = LIFTED_SCALE
                scaleY = LIFTED_SCALE
                shadowElevation = shadow
                shape = BtShapes.md
            }
            .border(1.dp, c.accentFill, BtShapes.md)
    }
}

/**
 * The people with spending this month, each opening their entries. Holding a card lifts it, and dragging it sideways
 * moves it; letting go hands [onDrop] the new order of the cards shown.
 */
@Composable
internal fun PersonStrip(cards: List<PersonSpending>, money: (Money) -> String, onOpen: (Long) -> Unit, onDrop: (List<Long>) -> Unit) {
    val c = BtTheme.colors
    val haptics = LocalHapticFeedback.current
    val gap = with(LocalDensity.current) { CARD_GAP.toPx() }
    var order by remember(cards) { mutableStateOf(cards) }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var slot by remember { mutableFloatStateOf(0f) }
    val drop by rememberUpdatedState(onDrop)

    Row(horizontalArrangement = Arrangement.spacedBy(CARD_GAP)) {
        order.forEach { p ->
            key(p.person.id) {
                val id = p.person.id
                val held = dragging == id
                BtCard(
                    bordered = true,
                    modifier = Modifier
                        .weight(1f)
                        .onSizeChanged { slot = it.width + gap }
                        .graphicsLayer { translationX = if (held) offset else 0f }
                        .lifted(held)
                        .pointerInput(id) {
                            fun settle() {
                                if (dragging == null) return
                                dragging = null
                                offset = 0f
                                drop(order.map { it.person.id })
                            }
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragging = id
                                    offset = 0f
                                },
                                onDragEnd = ::settle,
                                onDragCancel = ::settle,
                            ) { change, amount ->
                                change.consume()
                                offset += amount.x
                                // Past half a card, it takes its neighbour's place and stays under the finger.
                                val at = order.indexOfFirst { it.person.id == id }
                                val to = when {
                                    offset > slot / 2 && at < order.lastIndex -> at + 1
                                    offset < -slot / 2 && at > 0 -> at - 1
                                    else -> at
                                }
                                if (to != at) {
                                    order = order.toMutableList().apply { add(to, removeAt(at)) }
                                    offset -= (to - at) * slot
                                }
                            }
                        }
                        .clip(BtShapes.md)
                        .clickable(role = Role.Button) { onOpen(id) },
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PersonDot(p.person.color)
                            Text(p.person.name, style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1)
                        }
                        FittedAmount(money(p.total), BtTheme.type.amountSmall)
                    }
                }
            }
        }
    }
}

/**
 * Everyone in their order, with [shownInOrder] moved among themselves: each keeps one of the places they had, filled in
 * the new order, and everyone else stays where they were.
 */
internal fun reorderWithin(all: List<Person>, shownInOrder: List<Long>): List<Person> {
    val shown = shownInOrder.toSet()
    val next = shownInOrder.mapNotNull { id -> all.firstOrNull { it.id == id } }.iterator()
    return all.map { if (it.id in shown && next.hasNext()) next.next() else it }
}
