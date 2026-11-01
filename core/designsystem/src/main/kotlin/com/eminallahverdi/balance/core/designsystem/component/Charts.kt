package com.eminallahverdi.balance.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import kotlin.math.min

private const val RING_START_DEGREES = -90f
private const val SLICE_GAP_DEGREES = 2.5f
private const val REVEAL_MILLIS = 500

/** A ring of [slices], each a colour and its size, swept in from the top. It's a plain track when they're empty. */
@Composable
fun DonutChart(slices: List<Pair<Long, Long>>, description: String, modifier: Modifier = Modifier, thickness: Dp = 14.dp, center: @Composable BoxScope.() -> Unit) {
    val track = BtTheme.colors.bgSurfaceHigh
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(REVEAL_MILLIS))
    }
    Box(
        modifier
            .semantics { contentDescription = description }
            .drawBehind {
                val stroke = thickness.toPx()
                val topLeft = Offset(stroke / 2, stroke / 2)
                val ring = Size(size.width - stroke, size.height - stroke)
                val total = slices.sumOf { it.second }
                if (total == 0L) {
                    drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = ring, style = Stroke(stroke))
                    return@drawBehind
                }
                var start = RING_START_DEGREES
                slices.forEach { (color, value) ->
                    val sweep = 360f * reveal.value * (value.toFloat() / total)
                    val gap = if (slices.size > 1) min(SLICE_GAP_DEGREES, sweep / 2) else 0f
                    drawArc(color.toColor(), start + gap / 2, sweep - gap, useCenter = false, topLeft = topLeft, size = ring, style = Stroke(stroke))
                    start += sweep
                }
            },
        contentAlignment = Alignment.Center,
        content = center,
    )
}
