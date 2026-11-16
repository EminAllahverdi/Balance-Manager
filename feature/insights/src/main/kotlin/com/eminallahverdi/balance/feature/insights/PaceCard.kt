package com.eminallahverdi.balance.feature.insights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.text
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.InsightOverview
import com.eminallahverdi.balance.core.model.InsightPeriod
import com.eminallahverdi.balance.core.model.Money
import java.time.Month

private val PLOT_HEIGHT = 150.dp
private val AXIS_HEIGHT = 20.dp
private val PLOT_TOP = 8.dp
/** The most lines across the chart; fewer when a round step reaches the top sooner. */
private const val MAX_GRID_LINES = 4
private const val DRAW_MILLIS = 600

/**
 * The selected period's spending added up day by day, as a line, against the period before as a dashed one, so it's
 * plain whether this month is running ahead of the last. A period still running stops at today, where both lines carry
 * a dot.
 */
@Composable
internal fun PaceCard(period: InsightPeriod, overview: InsightOverview) {
    val c = BtTheme.colors
    val pace = overview.pace
    val previousName = period.periodName(overview.previousRange).text()
    val comparison = when {
        !pace.isRunning -> comparison(pace.change, period.periodName(overview.previousRange)).text()
        pace.change.isZero -> stringResource(R.string.insights_pace_same, previousName)
        else -> stringResource(R.string.insights_pace_versus, signedChange(pace.change), previousName)
    }
    val soFar = stringResource(R.string.insights_so_far)
    BtCard(bordered = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Overline(stringResource(R.string.insights_pace, period.periodName(overview.selected.range).text()), Modifier.weight(1f))
                Key(solid = true, period.barLabel(overview.selected.range))
                Key(solid = false, period.barLabel(overview.previousRange))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(MoneyFormat.format(pace.spent), style = BtTheme.type.amountMedium.copy(fontSize = 22.sp, lineHeight = 27.sp), color = c.textPrimary)
                    if (pace.isRunning) Text(soFar, style = BtTheme.type.caption, color = c.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text(comparison, style = BtTheme.type.caption, color = changeColor(pace.change, neutral = c.textSecondary))
            }
            val spoken = listOfNotNull(MoneyFormat.format(pace.spent), soFar.takeIf { pace.isRunning }).joinToString(" ") + ". " + comparison
            PaceChart(period, overview, Modifier.fillMaxWidth().height(PLOT_HEIGHT + AXIS_HEIGHT).semantics { contentDescription = spoken })
        }
    }
}

/** A short line, solid for the selected period and dashed for the one before, with its name. */
@Composable
private fun Key(solid: Boolean, label: String) {
    val c = BtTheme.colors
    val line = c.accentText
    val dash = c.textMuted
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.width(16.dp).height(4.dp)) {
            val y = size.height / 2
            if (solid) {
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            } else {
                drawLine(dash, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            }
        }
        Text(label, style = BtTheme.type.caption, color = c.textSecondary)
    }
}

@Composable
private fun PaceChart(period: InsightPeriod, overview: InsightOverview, modifier: Modifier) {
    val c = BtTheme.colors
    val pace = overview.pace
    val measurer = rememberTextMeasurer()
    val labelStyle = BtTheme.type.caption.copy(fontSize = 10.sp, lineHeight = 12.sp, color = c.textMuted)
    val axisStyle = BtTheme.type.caption.copy(color = c.textMuted)
    val days = maxOf(pace.length, pace.previous.size)
    val highest = maxOf(pace.current.maxOfOrNull { it.minor } ?: 0L, pace.previous.maxOfOrNull { it.minor } ?: 0L, 1L)
    val step = gridStep(highest)
    val lines = ((highest + step - 1) / step).toInt()
    val top = step * lines
    val ticks = axisTicks(period, overview)
    // The lines draw in from the left whenever another period is shown.
    val reveal = remember(overview.selected.range, period) { Animatable(0f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, tween(DRAW_MILLIS)) }

    Canvas(modifier) {
        val plotHeight = PLOT_HEIGHT.toPx()
        val plotTop = PLOT_TOP.toPx()
        fun x(day: Int) = if (days <= 1) 0f else day * size.width / (days - 1)
        fun y(minor: Long) = plotHeight - 2.dp.toPx() - (plotHeight - plotTop - 2.dp.toPx()) * minor / top

        for (line in 1..lines) {
            val gy = y(step * line)
            drawLine(c.borderDefault, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(axisLabel(step * line, top), labelStyle)
            drawText(label, topLeft = Offset(size.width - label.size.width, gy - label.size.height - 2.dp.toPx()))
        }
        ticks.forEach { (day, text) ->
            val label = measurer.measure(text, axisStyle)
            val left = (x(day) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
            drawText(label, topLeft = Offset(left, plotHeight + (AXIS_HEIGHT.toPx() - label.size.height)))
        }

        fun path(points: List<Money>) = Path().apply {
            points.forEachIndexed { i, m -> if (i == 0) moveTo(x(i), y(m.minor)) else lineTo(x(i), y(m.minor)) }
        }
        val today = pace.current.lastIndex
        clipRect(right = size.width * reveal.value) {
            drawPath(
                path(pace.previous),
                color = c.textMuted,
                style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))),
            )
            if (pace.isRunning && today >= 0) {
                drawLine(c.borderStrong, Offset(x(today), plotTop), Offset(x(today), plotHeight), strokeWidth = 1.dp.toPx())
            }
            if (pace.current.size > 1) {
                drawPath(path(pace.current), color = c.accentText, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (pace.isRunning && today >= 0) {
                pace.previous.getOrNull(minOf(today, pace.previous.lastIndex))?.let { before ->
                    dot(Offset(x(today), y(before.minor)), fill = c.bgSurface, ring = c.textMuted, radius = 4.dp.toPx())
                }
                dot(Offset(x(today), y(pace.current[today].minor)), fill = c.accentText, ring = c.bgSurface, radius = 5.dp.toPx())
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.dot(at: Offset, fill: Color, ring: Color, radius: Float) {
    drawCircle(ring, radius + 2.dp.toPx(), at)
    drawCircle(fill, radius, at)
}

/** The smallest round step, 1, 2, 2.5 or 5 times a power of ten and at least ₼1, that reaches [maxMinor] in [MAX_GRID_LINES]. */
internal fun gridStep(maxMinor: Long): Long {
    val highest = maxOf(maxMinor, 1L)
    var power = 100L
    while (true) {
        for (times in listOf(10L, 20L, 25L, 50L)) {
            val step = times * power / 10
            if ((highest + step - 1) / step <= MAX_GRID_LINES) return step
        }
        power *= 10
    }
}

/** 500 · 1k · 1.5k, in whole manats: a chart's scale, where the exact figure is above it. */
internal fun axisLabel(minor: Long, top: Long): String {
    val major = minor / 100
    if (top < 100_000) return major.toString()
    return java.math.BigDecimal(major).movePointLeft(3).stripTrailingZeros().toPlainString() + "k"
}

/** Under the chart: the days of the week, 1 8 15 22 29 of a month, or the quarters of a year. */
private fun axisTicks(period: InsightPeriod, overview: InsightOverview): List<Pair<Int, String>> {
    val start = overview.selected.range.start
    return when (period) {
        InsightPeriod.WEEK -> (0..6).map { it to DateLabels.weekdayShort(start.plusDays(it.toLong())) }
        InsightPeriod.MONTH -> listOf(0, 7, 14, 21, 28).map { it to (it + 1).toString() }
        InsightPeriod.YEAR -> listOf(Month.JANUARY, Month.APRIL, Month.JULY, Month.OCTOBER).map { m -> (start.withMonth(m.value).dayOfYear - 1) to DateLabels.monthShort(m) }
    }
}
