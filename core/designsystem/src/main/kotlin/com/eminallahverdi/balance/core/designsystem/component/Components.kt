package com.eminallahverdi.balance.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eminallahverdi.balance.core.designsystem.icon.BtIcons
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Person

/** Stored colours are ARGB longs (0xFFRRGGBB). */
fun Long.toColor() = Color(this.toInt())

/** Category icon on a 16 % tint of its hue. */
@Composable
fun CategoryTile(icon: String, color: Long, modifier: Modifier = Modifier, size: Dp = 40.dp, iconSize: Dp = 22.dp, corner: Dp = 12.dp) {
    val hue = color.toColor()
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(corner)).background(hue.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BtIcons.of(icon), contentDescription = null, tint = hue, modifier = Modifier.size(iconSize))
    }
}

/** Person avatar: their photo when set, otherwise the initial on their colour. */
@Composable
fun PersonAvatar(person: Person, modifier: Modifier = Modifier, size: Dp = 40.dp, selected: Boolean = false) {
    val ring = if (selected) Modifier.border(2.dp, BtTheme.colors.accentFill, CircleShape).padding(4.dp) else Modifier
    Box(modifier = modifier.then(ring).size(size).clip(CircleShape).background(person.color.toColor()), contentAlignment = Alignment.Center) {
        if (person.avatarUri != null) {
            AsyncImage(model = person.avatarUri, contentDescription = person.name, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(person.initial, style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.38f).sp), color = Color(0xFF0B0D10))
        }
    }
}

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

@Composable
fun BtChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = BtTheme.colors
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(BtShapes.full)
            .background(if (selected) c.accentFill else c.bgSurfaceRaised)
            .border(1.dp, if (selected) c.accentFill else c.borderDefault, BtShapes.full)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(text, style = BtTheme.type.label, color = if (selected) c.accentOn else c.textPrimary)
    }
}

/** Pill segmented control (Category | Person, Expense | Income | Transfer). */
@Composable
fun SegmentedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, fill: Boolean = false) {
    val c = BtTheme.colors
    Row(modifier.clip(BtShapes.full).background(c.bgSurfaceRaised).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                (if (fill) Modifier.weight(1f) else Modifier)
                    .clip(BtShapes.full)
                    .background(if (on) c.bgSurfaceHigh else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = BtTheme.type.label, color = if (on) c.textPrimary else c.textMuted)
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

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = BtTheme.colors.textMuted) {
    Text(text.uppercase(), style = BtTheme.type.overline, color = color, modifier = modifier)
}

@Composable
fun BtIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(BtTheme.colors.bgSurfaceRaised).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(22.dp)) }
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

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Overline(text)
        trailing()
    }
}
