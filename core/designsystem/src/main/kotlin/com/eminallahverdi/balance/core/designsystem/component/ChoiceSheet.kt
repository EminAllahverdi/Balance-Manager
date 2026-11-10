package com.eminallahverdi.balance.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.designsystem.R
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import kotlinx.coroutines.launch

/**
 * A bottom sheet for picking one thing, used instead of a menu. [options] wraps what a pick does
 * in `choose { }`, which applies it and slides the sheet away before [onClose].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceSheet(title: String, onClose: () -> Unit, options: @Composable (choose: (() -> Unit) -> Unit) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val choose = { apply: () -> Unit ->
        apply()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() }
        Unit
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, containerColor = BtTheme.colors.bgSurface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = BtTheme.type.titleMedium, color = BtTheme.colors.textPrimary, modifier = Modifier.padding(bottom = 4.dp))
            options(choose)
        }
    }
}

/** One choice in a [ChoiceSheet]. A check marks the selected row, and [caption] explains a choice that needs it. */
@Composable
fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit, caption: String? = null, leading: (@Composable () -> Unit)? = null) {
    val c = BtTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(BtShapes.md)
            .background(if (selected) c.bgSurfaceHigh else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) Text(caption, style = BtTheme.type.caption, color = c.textSecondary)
        }
        if (selected) Icon(Icons.Rounded.Check, stringResource(R.string.ds_selected), tint = c.accentText, modifier = Modifier.size(20.dp))
    }
}
