package com.eminallahverdi.balance.feature.catalog

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private const val ROWS_PER_SECOND = 4f

/** Drag-to-reorder for a LazyColumn. Reorderable items use a Long key; anything else stays put. */
@Stable
internal class ReorderState(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: State<(from: Long, to: Long) -> Unit>,
    private val onDrop: State<() -> Unit>,
) {
    var draggingId by mutableStateOf<Long?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    /** Where the last move put the item. Until a layout shows it there, the positions are out of date. */
    private var expectedIndex: Int? = null

    /** How far the finger has travelled, so a row only scrolls the list the way it's being dragged. */
    private var dragTotal = 0f

    fun start(id: Long) {
        draggingId = id
        offset = 0f
        expectedIndex = null
        dragTotal = 0f
        scope.launch { autoScroll() }
    }

    fun drag(delta: Float) {
        offset += delta
        dragTotal += delta
        moveOverNeighbour()
    }

    /** Runs for the whole drag, so holding a row near the edge keeps the list moving. */
    private suspend fun autoScroll() {
        var last = withFrameNanos { it }
        while (draggingId != null) {
            val now = withFrameNanos { it }
            val distance = edgeSpeed() * (now - last) / 1_000_000_000f
            last = now
            if (distance == 0f) continue
            // A move cancels a running scroll; that's fine, the next frame carries on.
            try {
                val scrolled = list.scrollBy(distance)
                offset += scrolled
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
            }
            moveOverNeighbour()
        }
    }

    /** Pixels per second: when the row is dragged to within most of its height of an edge. */
    private fun edgeSpeed(): Float {
        val info = list.layoutInfo
        val dragged = info.visibleItemsInfo.firstOrNull { it.key == draggingId } ?: return 0f
        val top = dragged.offset + offset
        val margin = dragged.size * 3 / 4
        return when {
            dragTotal < 0 && top < info.viewportStartOffset + margin -> -dragged.size * ROWS_PER_SECOND
            dragTotal > 0 && top + dragged.size > info.viewportEndOffset - margin -> dragged.size * ROWS_PER_SECOND
            else -> 0f
        }
    }

    private fun moveOverNeighbour() {
        val id = draggingId ?: return
        val info = list.layoutInfo
        val dragged = info.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        if (expectedIndex != null && dragged.index != expectedIndex) return
        expectedIndex = null
        val center = dragged.offset + offset + dragged.size / 2f
        val target = info.visibleItemsInfo.firstOrNull { it.key != id && it.key is Long && center.toInt() in it.offset until it.offset + it.size }
        if (target != null) {
            // The item is about to jump to the target's slot; keep it under the finger.
            val newOffset = if (target.offset > dragged.offset) target.offset + target.size - dragged.size else target.offset
            offset -= newOffset - dragged.offset
            expectedIndex = target.index
            // Without this the list anchors on the first visible key, and scrolls along with a moved first item.
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
            onMove.value(id, target.key as Long)
        }
    }

    fun end() {
        if (draggingId == null) return
        draggingId = null
        offset = 0f
        onDrop.value()
    }
}

@Composable
internal fun rememberReorderState(list: LazyListState, onMove: (Long, Long) -> Unit, onDrop: () -> Unit): ReorderState {
    val scope = rememberCoroutineScope()
    val move = rememberUpdatedState(onMove)
    val drop = rememberUpdatedState(onDrop)
    return remember(list) { ReorderState(list, scope, move, drop) }
}

/** Put on the drag handle. */
internal fun Modifier.reorderHandle(state: ReorderState, id: Long): Modifier = pointerInput(state, id) {
    detectDragGestures(
        onDragStart = { state.start(id) },
        onDragEnd = state::end,
        onDragCancel = state::end,
    ) { change, amount ->
        change.consume()
        state.drag(amount.y)
    }
}

/** Put on the whole row, so it follows the finger above its neighbours. */
internal fun Modifier.reorderItem(state: ReorderState, id: Long): Modifier =
    zIndex(if (state.draggingId == id) 1f else 0f)
        .graphicsLayer { translationY = if (state.draggingId == id) state.offset else 0f }

/** Moves the item with [from]'s key to where [to]'s item is. */
internal fun <T> List<T>.moved(from: Long, to: Long, id: (T) -> Long): List<T> {
    val fromIndex = indexOfFirst { id(it) == from }
    val toIndex = indexOfFirst { id(it) == to }
    if (fromIndex < 0 || toIndex < 0) return this
    return toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}
