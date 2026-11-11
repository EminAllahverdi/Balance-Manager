package com.eminallahverdi.balance.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eminallahverdi.balance.core.designsystem.icon.BtIcons
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.designsystem.theme.TILE_TINT
import com.eminallahverdi.balance.core.designsystem.theme.tileGlyph
import com.eminallahverdi.balance.core.model.Person

/** Stored colours are ARGB longs (0xFFRRGGBB). */
fun Long.toColor() = Color(this.toInt())

/** Category icon on a 16 % tint of its hue. */
@Composable
fun CategoryTile(icon: String, color: Long, modifier: Modifier = Modifier, size: Dp = 40.dp, iconSize: Dp = 22.dp, corner: Dp = 12.dp) {
    val hue = color.toColor()
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(corner)).background(hue.copy(alpha = TILE_TINT)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BtIcons.of(icon), contentDescription = null, tint = BtTheme.colors.tileGlyph(hue), modifier = Modifier.size(iconSize))
    }
}

/**
 * Person avatar: the initial on their colour, or their photo inside a ring of it. A photo would hide the colour, and the
 * colour is what ties them to their dots and bars everywhere else, so it stays in view. [selected] adds a lime ring outside.
 */
@Composable
fun PersonAvatar(person: Person, modifier: Modifier = Modifier, size: Dp = 40.dp, selected: Boolean = false) {
    val ring = if (selected) Modifier.border(2.dp, BtTheme.colors.accentFill, CircleShape).padding(4.dp) else Modifier
    val hue = person.color.toColor()
    if (person.avatarUri != null) {
        val band = (size * PHOTO_RING).coerceAtLeast(2.dp)
        Box(modifier = modifier.then(ring).size(size).border(band, hue, CircleShape).padding(band + 1.5.dp).clip(CircleShape).background(hue)) {
            AsyncImage(model = person.avatarUri, contentDescription = person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    } else {
        Box(modifier = modifier.then(ring).size(size).clip(CircleShape).background(hue), contentAlignment = Alignment.Center) {
            Text(person.initial, style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.38f).sp), color = Color(0xFF0B0D10))
        }
    }
}

/** How thick a photo's ring of the person's colour is, against the avatar's size. */
private const val PHOTO_RING = 0.08f

@Composable
fun PersonDot(color: Long, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color.toColor()))
}

/** Horizontal bar split into coloured segments with 2 dp gaps (who a category was for, or what a person spent on). */
@Composable
fun SplitBar(parts: List<Pair<Long, Long>>, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (parts.isEmpty()) {
            Box(Modifier.weight(1f).height(height).clip(RoundedCornerShape(3.dp)).background(BtTheme.colors.bgSurfaceHigh))
        }
        parts.filter { it.second > 0 }.forEach { (color, value) ->
            Box(Modifier.weight(value.toFloat() / total).height(height).clip(RoundedCornerShape(3.dp)).background(color.toColor()))
        }
    }
}

/** [outlined] shows selected as a lime outline, for pages where a lime fill would compete with the one action, like Save. */
@Composable
fun BtChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    outlined: Boolean = false,
) {
    val c = BtTheme.colors
    val filled = selected && !outlined
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(BtShapes.full)
            .background(if (filled) c.accentFill else if (selected) c.bgSurfaceHigh else c.bgSurfaceRaised)
            .border(1.dp, if (selected) c.accentFill else c.borderDefault, BtShapes.full)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        leading?.invoke()
        Text(
            text,
            style = BtTheme.type.label,
            color = if (filled) c.accentOn else c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

/** Pill segmented control (Category | Person, Expense | Income | Transfer). */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    compact: Boolean = false,
) {
    val c = BtTheme.colors
    Row(modifier.clip(BtShapes.full).background(c.bgSurfaceRaised).padding(if (compact) 2.dp else 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                (if (fill) Modifier.weight(1f) else Modifier)
                    .clip(BtShapes.full)
                    .background(if (on) c.bgSurfaceHigh else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    // Shared out evenly, a segment gives its label more of its room: longer words, like Azerbaijani ones, stay on one line.
                    .padding(horizontal = if (fill) 4.dp else if (compact) 9.dp else 12.dp, vertical = if (compact) 5.dp else 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = BtTheme.type.label, color = if (on) c.textPrimary else c.textMuted, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun BtCard(modifier: Modifier = Modifier, bordered: Boolean = false, shape: androidx.compose.ui.graphics.Shape = BtShapes.md, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = BtTheme.colors.bgSurface,
        border = if (bordered) BorderStroke(1.dp, BtTheme.colors.borderDefault) else null,
        content = content,
    )
}

/** Small capitals over a group, uppercased in the language showing, so Azerbaijani keeps its dotted İ. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = BtTheme.colors.textMuted) {
    Text(text.uppercase(LocalConfiguration.current.locales[0]), style = BtTheme.type.overline, color = color, modifier = modifier)
}

@Composable
fun BtIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(BtTheme.colors.bgSurfaceRaised).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(22.dp)) }
}

/** The one switch style, so every on/off reads the same. */
@Composable
fun BtSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = BtTheme.colors.accentFill,
            checkedThumbColor = BtTheme.colors.accentOn,
            uncheckedTrackColor = BtTheme.colors.bgSurfaceHigh,
            uncheckedThumbColor = BtTheme.colors.textMuted,
        ),
    )
}

/** The lime add button that floats over a long list, in reach of the thumb. Align it to the bottom end of the screen. */
@Composable
fun FloatingAddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.accentFill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = c.accentOn, modifier = Modifier.size(22.dp))
        Box(Modifier.width(6.dp))
        Text(text, style = BtTheme.type.bodyStrong, color = c.accentOn)
    }
}

/** Full-width lime button: the one primary action on a screen. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = BtTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(BtShapes.full)
            .background(if (enabled) c.accentFill else c.bgSurfaceHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) c.accentOn else c.textMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = BtTheme.type.bodyStrong.copy(fontSize = 16.sp), color = if (enabled) c.accentOn else c.textMuted)
    }
}

/** An amount in a narrow space: shrinks to stay on one line, because money must never be cut off. */
@Composable
fun FittedAmount(value: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = BtTheme.colors.textPrimary) {
    var scale by remember(value) { mutableFloatStateOf(1f) }
    var fitted by remember(value) { mutableStateOf(false) }
    Text(
        text = value,
        style = style.copy(fontSize = style.fontSize * scale),
        color = color,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { if (it.hasVisualOverflow && scale > 0.6f) scale -= 0.05f else fitted = true },
        // Hidden until it fits, so the oversized first pass never shows.
        modifier = modifier.drawWithContent { if (fitted) drawContent() },
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Overline(text)
        trailing()
    }
}

/**
 * A row of tabs with a lime line under the one showing, over a hairline. It scrolls sideways once the tabs don't fit, so
 * more can join it: the Reports tab's reports.
 */
@Composable
fun ScrollingTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(c.borderDefault))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val line = c.accentFill
                Text(
                    label,
                    style = BtTheme.type.bodyStrong,
                    color = if (on) c.textPrimary else c.textMuted,
                    maxLines = 1,
                    modifier = Modifier
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) })
                        .drawBehind {
                            if (on) {
                                val thick = TAB_LINE.toPx()
                                drawRoundRect(line, topLeft = Offset(0f, size.height - thick), size = Size(size.width, thick), cornerRadius = CornerRadius(thick / 2))
                            }
                        }
                        .padding(start = 2.dp, end = 2.dp, top = 10.dp, bottom = 12.dp),
                )
            }
        }
    }
}

private val TAB_LINE = 3.dp
