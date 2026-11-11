package com.eminallahverdi.balance.core.designsystem.component

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.designsystem.R
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Category
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

private const val HOURS_IN_DAY = 24
private const val MINUTES_IN_HOUR = 60

/** Rows above and below the middle one. */
private const val WHEEL_SIDE_ROWS = 2

/** How many times round a wheel's numbers repeat. It starts halfway, so no one reaches either end. */
private const val WHEEL_TURNS = 1_000

/** How much each row away from the middle fades. */
private const val WHEEL_FADE_PER_ROW = 0.35f
private val WheelRowHeight = 56.dp

private const val WHEEL_TICK_MILLIS = 15L

/** Out of 255. */
private const val WHEEL_TICK_STRENGTH = 150

/** Matches how far back Home can swipe. */
private const val YEARS_BACK = 49
private const val MONTHS_PER_ROW = 3

/** How many months a month-by-month page can swipe back through: Home's and Groceries' pagers, and [MonthPicker]'s years. */
const val MONTH_PAGES = 600

/**
 * A month's name with a small caret, on the page itself, so it moves with a swipe and a swipe that starts on it works.
 * Tapping it opens a sheet of months ([MonthPicker]). The year shows only when it isn't this one.
 */
@Composable
fun MonthTitle(month: YearMonth, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    val label = DateLabels.monthTitle(month)
    Row(
        modifier.clip(BtShapes.sm).clickable(role = Role.Button, onClickLabel = stringResource(R.string.ds_pick_a_month), onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = BtTheme.type.titleLarge, color = c.textPrimary)
        Icon(Icons.Rounded.ExpandMore, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
    }
}

/**
 * A month as a small button with a caret, in the corner of a report's first card, where a page title would be one title too
 * many. Tapping it opens the same sheet of months as [MonthTitle].
 */
@Composable
fun MonthButton(month: YearMonth, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(BtShapes.full)
            .background(c.bgSurfaceRaised)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.ds_pick_a_month), onClick = onClick)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(DateLabels.monthTitle(month), style = BtTheme.type.label, color = c.textPrimary, maxLines = 1)
        Icon(Icons.Rounded.ExpandMore, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
    }
}

/** Any month up to this one: a year at a time, so it stays on one screen. */
@Composable
fun MonthPicker(selected: YearMonth?, onSelect: (YearMonth) -> Unit) {
    val now = YearMonth.now()
    var year by rememberSaveable { mutableIntStateOf((selected ?: now).year) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        YearStep(Icons.Rounded.ChevronLeft, stringResource(R.string.ds_previous_year), enabled = year > now.year - YEARS_BACK) { year-- }
        Text(year.toString(), style = BtTheme.type.titleMedium, color = BtTheme.colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        YearStep(Icons.Rounded.ChevronRight, stringResource(R.string.ds_next_year), enabled = year < now.year) { year++ }
    }
    (1..12).map { YearMonth.of(year, it) }.chunked(MONTHS_PER_ROW).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { month ->
                MonthCell(month, selected == month, enabled = month <= now, onClick = { onSelect(month) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun YearStep(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).alpha(if (enabled) 1f else 0.3f).clip(BtShapes.full).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = BtTheme.colors.textSecondary) }
}

@Composable
private fun MonthCell(month: YearMonth, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    Box(
        modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(BtShapes.key)
            .background(if (selected) c.accentFill else c.bgSurfaceRaised)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(DateLabels.monthShort(month.month), style = BtTheme.type.label, color = if (selected) c.accentOn else c.textPrimary)
    }
}

/** Chips in one line for picking a category inside a sheet, where a sheet on a sheet is too much. */
@Composable
fun CategoryChipRow(categories: List<Category>, selectedId: Long?, onSelect: (Category) -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = categories.indexOfFirst { it.id == selectedId }.coerceAtLeast(0))
    LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories, key = { it.id }) { category ->
            BtChip(
                text = category.name,
                selected = category.id == selectedId,
                onClick = { onSelect(category) },
                leading = { CategoryTile(category.icon, category.color, size = 24.dp, iconSize = 16.dp, corner = 8.dp) },
            )
        }
    }
}

/**
 * The calendar, limited to what a date can sensibly be here: [earliest] or [latest] leave out the days past them.
 * Call it outside any sheet: a dialog created inside one takes on the sheet's window and sticks to the bottom edge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BtDatePickerDialog(selected: LocalDate, onPick: (LocalDate) -> Unit, onClose: () -> Unit, earliest: LocalDate? = null, latest: LocalDate? = null) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                (earliest == null || utcTimeMillis >= earliest.toUtcMillis()) && (latest == null || utcTimeMillis <= latest.toUtcMillis())
        },
    )
    DatePickerDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onClose()
            }) { Text(stringResource(R.string.ds_ok)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.ds_cancel)) } },
    ) { DatePicker(state = state) }
}

/**
 * A time of day, as minutes after midnight, on two wheels like a phone's alarm clock. Always 24-hour, like every time in the app.
 *
 * A panel drawn in the screen, not a ModalBottomSheet: a sheet that can be dragged fights the wheels, taking some drags and
 * flings for itself, and the one here can't turn that off. It closes with Save, the X, Back or a tap on the dim area. Call it
 * at the root of a screen, so it covers everything.
 */
@Composable
fun BtTimePickerSheet(visible: Boolean, title: String, minuteOfDay: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    BackHandler(enabled = visible, onBack = onClose)
    val scrim = BtTheme.colors.scrim
    val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Up behind the status bar too, which this screen's own bounds stop short of.
                    .drawBehind { drawRect(scrim, Offset(0f, -statusBar), Size(size.width, size.height + statusBar)) }
                    .clickable(interactionSource = null, indication = null, onClick = onClose),
            )
        }
        AnimatedVisibility(visible, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            TimePanel(title, minuteOfDay, onPick, onClose)
        }
    }
}

@Composable
private fun TimePanel(title: String, minuteOfDay: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val c = BtTheme.colors
    var hour by remember { mutableIntStateOf(minuteOfDay / MINUTES_IN_HOUR) }
    var minute by remember { mutableIntStateOf(minuteOfDay % MINUTES_IN_HOUR) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(BtShapes.sheet)
            .background(c.bgSurface)
            .clickable(interactionSource = null, indication = null) {}
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = BtTheme.type.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            BtIconButton(Icons.Rounded.Close, stringResource(R.string.ds_close), onClose)
        }
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(WheelRowHeight).clip(BtShapes.md).background(c.bgSurfaceRaised))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TimeWheel(HOURS_IN_DAY, hour, { hour = it }, Alignment.CenterEnd, Modifier.weight(1f))
                Text(":", style = BtTheme.type.amountDisplay.copy(fontSize = 36.sp, lineHeight = 40.sp), color = c.textPrimary, modifier = Modifier.padding(horizontal = 16.dp))
                TimeWheel(MINUTES_IN_HOUR, minute, { minute = it }, Alignment.CenterStart, Modifier.weight(1f))
            }
        }
        PrimaryButton(stringResource(R.string.ds_save), onClick = {
            onPick(hour * MINUTES_IN_HOUR + minute)
            onClose()
        })
    }
}

/**
 * One endless wheel of two-digit numbers. It settles on the middle row, and tapping another row turns to it. It takes up its
 * half of the panel, so a drag anywhere on that side turns it, with the number at [alignment], next to the colon.
 */
@Composable
private fun TimeWheel(count: Int, initial: Int, onSelect: (Int) -> Unit, alignment: Alignment, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = WHEEL_TURNS / 2 * count + initial - WHEEL_SIDE_ROWS)
    LaunchedEffect(list) {
        snapshotFlow { list.middleIndex() }.filterNotNull().distinctUntilChanged().drop(1).collect {
            context.wheelTick()
            onSelect(it % count)
        }
    }
    LazyColumn(
        modifier.height(WheelRowHeight * (WHEEL_SIDE_ROWS * 2 + 1)),
        state = list,
        flingBehavior = rememberSnapFlingBehavior(list),
    ) {
        items(count * WHEEL_TURNS) { index ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight)
                    .graphicsLayer { alpha = list.wheelAlpha(index) }
                    .clickable(interactionSource = null, indication = null) { scope.launch { list.animateScrollToItem(index - WHEEL_SIDE_ROWS) } },
                contentAlignment = alignment,
            ) {
                Text(
                    (index % count).toString().padStart(2, '0'),
                    style = BtTheme.type.amountDisplay.copy(fontSize = 36.sp, lineHeight = 40.sp),
                    color = BtTheme.colors.textPrimary,
                )
            }
        }
    }
}

/**
 * A tick as a number passes, like a phone's alarm wheels. The system's tick effects are too faint to feel on the owner's
 * Samsung, so this sets the motor's strength itself. As touch feedback, it still follows the phone's vibration settings.
 */
private fun Context.wheelTick() {
    val vibrator = getSystemService(Vibrator::class.java) ?: return
    val tick = VibrationEffect.createOneShot(WHEEL_TICK_MILLIS, WHEEL_TICK_STRENGTH)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        vibrator.vibrate(tick, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
    } else {
        vibrator.vibrate(tick)
    }
}

/** The row closest to the middle of the wheel, while it's turning too. */
private fun LazyListState.middleIndex(): Int? {
    val wheel = layoutInfo
    val middle = (wheel.viewportStartOffset + wheel.viewportEndOffset) / 2
    return wheel.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) }?.index
}

private fun LazyListState.wheelAlpha(index: Int): Float {
    val wheel = layoutInfo
    val row = wheel.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
    val middle = (wheel.viewportStartOffset + wheel.viewportEndOffset) / 2f
    val rowsAway = abs(row.offset + row.size / 2f - middle) / row.size
    return (1f - rowsAway * WHEEL_FADE_PER_ROW).coerceAtLeast(0f)
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
