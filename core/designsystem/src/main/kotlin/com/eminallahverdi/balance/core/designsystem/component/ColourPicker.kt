package com.eminallahverdi.balance.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.designsystem.theme.DarkBtColors
import com.eminallahverdi.balance.core.designsystem.theme.LightBtColors
import com.eminallahverdi.balance.core.designsystem.theme.LocalBtColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Two rows: the palette fills all but the last place, which opens a picker for any other colour. */
private const val COLUMNS = 7
private const val PLACES = COLUMNS * 2

/*
 * A colour of one's own stays where it reads on both themes: never so grey it's lost on the surface, never so dark it
 * disappears on Dark, never so pale it washes out on Light. tileGlyph darkens the icon on Light where it has to.
 */
private const val MIN_SATURATION = 0.15f
private const val MAX_SATURATION = 0.95f
private const val MIN_LIGHTNESS = 0.45f
private const val MAX_LIGHTNESS = 0.82f
private val HUES = (0..360 step 30).map { Color.hsl(it.toFloat(), 0.85f, 0.65f) }

/**
 * The palette's colours in two rows, the last place for any other colour. A colour that isn't in the palette shows
 * there, ticked. [icon] previews a category or account in the picker; without one it previews a person's dot.
 */
@Composable
fun ColourPicker(palette: List<Long>, selected: Long, icon: String? = null, onSelect: (Long) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val shown = palette.take(PLACES - 1)
    val custom = selected.takeIf { it !in shown }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (shown + null).chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { colour ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).clickable(role = Role.RadioButton) { if (colour != null) onSelect(colour) else picking = true }, contentAlignment = Alignment.Center) {
                        if (colour != null) Swatch(colour, colour == selected) else OwnSwatch(custom)
                    }
                }
                repeat(COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
    if (picking) OwnColourSheet(selected, icon, onPick = { onSelect(it); picking = false }, onClose = { picking = false })
}

@Composable
private fun Swatch(colour: Long, on: Boolean) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(colour.toColor()), contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Rounded.Check, "Selected", tint = Color(0xFF0B0D10), modifier = Modifier.size(20.dp))
    }
}

/** A ring of every hue, around the colour of one's own when there is one, or a plus to pick one. */
@Composable
private fun OwnSwatch(custom: Long?) {
    Box(
        Modifier.size(32.dp).border(3.dp, Brush.sweepGradient(HUES), CircleShape).padding(5.dp).clip(CircleShape)
            .background(custom?.toColor() ?: BtTheme.colors.bgSurfaceRaised)
            .semantics { contentDescription = if (custom != null) "Your own colour, selected" else "Pick any colour" },
        contentAlignment = Alignment.Center,
    ) {
        if (custom != null) {
            Icon(Icons.Rounded.Check, null, tint = Color(0xFF0B0D10), modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Rounded.Add, null, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

/** Hue, saturation and lightness, each 0 to 1 except hue in degrees. */
internal data class Hsl(val hue: Float, val saturation: Float, val lightness: Float) {
    /** Kept to what reads on both themes. */
    fun usable(): Hsl = copy(saturation = saturation.coerceIn(MIN_SATURATION, MAX_SATURATION), lightness = lightness.coerceIn(MIN_LIGHTNESS, MAX_LIGHTNESS))

    fun toColour(): Long = Color.hsl(hue.mod(360f), saturation, lightness).toArgb().toLong() and 0xFFFFFFFFL

    companion object {
        fun of(colour: Long): Hsl {
            val c = colour.toColor()
            val max = maxOf(c.red, c.green, c.blue)
            val min = minOf(c.red, c.green, c.blue)
            val chroma = max - min
            val lightness = (max + min) / 2
            val saturation = if (chroma == 0f) 0f else (chroma / (1 - abs(2 * lightness - 1))).coerceAtMost(1f)
            val hue = when {
                chroma == 0f -> 0f
                max == c.red -> ((c.green - c.blue) / chroma).mod(6f) * 60
                max == c.green -> ((c.blue - c.red) / chroma + 2) * 60
                else -> ((c.red - c.green) / chroma + 4) * 60
            }
            return Hsl(hue, saturation, lightness)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnColourSheet(start: Long, icon: String?, onPick: (Long) -> Unit, onClose: () -> Unit) {
    val first = remember(start) { Hsl.of(start).usable() }
    var hue by remember { mutableFloatStateOf(first.hue) }
    var saturation by remember { mutableFloatStateOf(first.saturation) }
    var lightness by remember { mutableFloatStateOf(first.lightness) }
    val colour = Hsl(hue, saturation, lightness).toColour()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BtTheme.colors.bgSurface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Your own colour", style = BtTheme.type.titleMedium, color = BtTheme.colors.textPrimary)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                HueRing(hue, saturation, lightness, onHue = { hue = it }, modifier = Modifier.size(232.dp))
                ColourPreview(colour, icon, size = 72.dp)
            }
            EditorField("Strength") {
                GradientSlider(
                    value = (saturation - MIN_SATURATION) / (MAX_SATURATION - MIN_SATURATION),
                    onValue = { saturation = MIN_SATURATION + it * (MAX_SATURATION - MIN_SATURATION) },
                    brush = Brush.horizontalGradient(listOf(Color.hsl(hue, MIN_SATURATION, lightness), Color.hsl(hue, MAX_SATURATION, lightness))),
                    label = "Strength",
                )
            }
            EditorField("Lightness") {
                GradientSlider(
                    value = (lightness - MIN_LIGHTNESS) / (MAX_LIGHTNESS - MIN_LIGHTNESS),
                    onValue = { lightness = MIN_LIGHTNESS + it * (MAX_LIGHTNESS - MIN_LIGHTNESS) },
                    brush = Brush.horizontalGradient(listOf(Color.hsl(hue, saturation, MIN_LIGHTNESS), Color.hsl(hue, saturation, MAX_LIGHTNESS))),
                    label = "Lightness",
                )
            }
            // How it reads on each theme, whichever one is on now.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("On Dark" to DarkBtColors, "On Light" to LightBtColors).forEach { (label, colours) ->
                    CompositionLocalProvider(LocalBtColors provides colours) {
                        Row(
                            Modifier.weight(1f).clip(BtShapes.md).background(colours.bgSurface)
                                .border(1.dp, colours.borderDefault, BtShapes.md).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ColourPreview(colour, icon, size = 40.dp)
                            Text(label, style = BtTheme.type.caption, color = colours.textSecondary)
                        }
                    }
                }
            }
            PrimaryButton("Use this colour", { onPick(colour) })
        }
    }
}

/** A category's tile, or a person's dot, in [colour]. */
@Composable
private fun ColourPreview(colour: Long, icon: String?, size: Dp) {
    if (icon != null) {
        CategoryTile(icon, colour, size = size, iconSize = size * 0.55f, corner = size * 0.3f)
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(colour.toColor()))
    }
}

/** Every hue around a ring. Touching or dragging anywhere on it picks the hue at that angle. */
@Composable
private fun HueRing(hue: Float, saturation: Float, lightness: Float, onHue: (Float) -> Unit, modifier: Modifier) {
    val thickness = with(LocalDensity.current) { 28.dp.toPx() }
    val ring = remember(saturation, lightness) { (0..360 step 10).map { Color.hsl(it.toFloat(), saturation, lightness) } }
    val surface = BtTheme.colors.bgSurface
    Canvas(
        modifier
            .semantics { contentDescription = "Hue, ${hue.roundToInt()} degrees" }
            .pointerInput(Unit) {
                fun pick(at: Offset) {
                    val angle = Math.toDegrees(atan2((at.y - size.height / 2f).toDouble(), (at.x - size.width / 2f).toDouble())).toFloat()
                    onHue((angle + 360f) % 360f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pick(down.position)
                    down.consume()
                    drag(down.id) { change ->
                        pick(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        val radius = size.minDimension / 2 - thickness / 2
        drawCircle(Brush.sweepGradient(ring, center), radius, center, style = Stroke(thickness))
        val radians = hue * PI.toFloat() / 180f
        val knob = Offset(center.x + radius * cos(radians), center.y + radius * sin(radians))
        drawCircle(surface, thickness / 2 + 3.dp.toPx(), knob)
        drawCircle(Color.hsl(hue, saturation, lightness), thickness / 2 - 1.dp.toPx(), knob)
    }
}

/** A slider whose track shows what it changes. [value] runs from 0 to 1. */
@Composable
private fun GradientSlider(value: Float, onValue: (Float) -> Unit, brush: Brush, label: String) {
    val c = BtTheme.colors
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(36.dp)
            .semantics { contentDescription = "$label, ${(value * 100).roundToInt()} percent" }
            .pointerInput(Unit) {
                fun pick(x: Float) = onValue((x / size.width).coerceIn(0f, 1f))
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pick(down.position.x)
                    down.consume()
                    drag(down.id) { change ->
                        pick(change.position.x)
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumb = 28.dp
        Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(brush))
        val travel = with(LocalDensity.current) { (maxWidth - thumb).toPx() }
        Box(
            Modifier.offset { IntOffset((value.coerceIn(0f, 1f) * travel).roundToInt(), 0) }
                .size(thumb).clip(CircleShape).background(c.textPrimary).padding(3.dp).clip(CircleShape).background(c.bgSurface),
        ) { Box(Modifier.fillMaxSize()) }
    }
}
