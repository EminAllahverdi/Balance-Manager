package com.eminallahverdi.balance.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.designsystem.R
import com.eminallahverdi.balance.core.designsystem.icon.BtIcons
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme

/** Top of an editor sheet: a live preview, the title, and Remove when there's something to remove. */
@Composable
fun EditorTitle(title: String, onRemove: (() -> Unit)? = null, preview: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        preview()
        Text(title, style = BtTheme.type.titleMedium, color = BtTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        if (onRemove != null) {
            Box(
                Modifier.heightIn(min = 44.dp).clip(BtShapes.full).clickable(role = Role.Button, onClick = onRemove).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.ds_remove), style = BtTheme.type.label, color = BtTheme.colors.moneyExpense) }
        }
    }
}

@Composable
fun EditorField(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline(title)
        content()
    }
}

@Composable
fun BtTextField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
) {
    val c = BtTheme.colors
    Box(modifier.fillMaxWidth().heightIn(min = 52.dp).clip(BtShapes.md).background(c.bgSurfaceRaised).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(placeholder, style = BtTheme.type.body, color = c.textMuted)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = BtTheme.type.body.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.accentFill),
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(color: Long, selected: String, onSelect: (String) -> Unit, keys: Collection<String> = BtIcons.catalog.keys) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { key ->
            val on = key == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .then(if (on) Modifier.border(2.dp, BtTheme.colors.accentFill, RoundedCornerShape(15.dp)) else Modifier)
                    .clickable(role = Role.RadioButton) { onSelect(key) }
                    .padding(3.dp),
            ) { CategoryTile(key, color, size = 44.dp) }
        }
    }
}

