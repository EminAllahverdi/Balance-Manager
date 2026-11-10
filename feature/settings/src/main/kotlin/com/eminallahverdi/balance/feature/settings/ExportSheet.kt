package com.eminallahverdi.balance.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.BtDatePickerDialog
import com.eminallahverdi.balance.core.designsystem.component.EditorField
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.ExportSpan
import java.time.LocalDate
import kotlinx.coroutines.launch

private val ExportSpan.label: String
    @Composable get() = when (this) {
        ExportSpan.THIS_MONTH -> stringResource(R.string.settings_this_month)
        ExportSpan.LAST_MONTH -> stringResource(R.string.settings_last_month)
        ExportSpan.LAST_30_DAYS -> stringResource(R.string.settings_last_30_days)
        ExportSpan.LAST_90_DAYS -> stringResource(R.string.settings_last_90_days)
        ExportSpan.THIS_YEAR -> stringResource(R.string.settings_this_year)
        ExportSpan.LAST_YEAR -> stringResource(R.string.settings_last_year)
        ExportSpan.EVERYTHING -> stringResource(R.string.settings_everything)
    }

/**
 * Which entries go in an export, and where it goes: saved as a file, or straight to another app through the share sheet.
 * The quick picks fill in the dates, which stay on show, so a pick never hides what it means.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ExportSheet(draft: ExportDraft, vm: SettingsViewModel) {
    val c = BtTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf<DateEnd?>(null) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { file -> file?.let(vm::exportCsv) }
    val count = draft.count
    val canExport = count != null && count > 0
    val shareTitle = stringResource(R.string.settings_share_entries)

    ModalBottomSheet(onDismissRequest = vm::closeExport, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bgSurface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_export_entries), style = BtTheme.type.titleMedium, color = c.textPrimary)
                Text(stringResource(R.string.settings_csv_hint), style = BtTheme.type.caption, color = c.textSecondary)
            }
            EditorField(stringResource(R.string.settings_quick_picks)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportSpan.entries.forEach { span -> BtChip(span.label, span == draft.span, { vm.pickSpan(span) }, outlined = true) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorField(stringResource(R.string.settings_from), Modifier.weight(1f)) { DateButton(draft.from) { picking = DateEnd.FROM } }
                EditorField(stringResource(R.string.settings_to), Modifier.weight(1f)) { DateButton(draft.to) { picking = DateEnd.TO } }
            }
            Text(
                when (count) {
                    null -> " "
                    0 -> stringResource(R.string.settings_no_entries_between)
                    else -> pluralStringResource(R.plurals.settings_entries, count, count)
                },
                style = BtTheme.type.bodyStrong,
                color = if (count == 0) c.textMuted else c.textPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PairedButton(stringResource(R.string.settings_save), Icons.Rounded.Download, enabled = canExport, modifier = Modifier.weight(1f)) { saveFile.launch(draft.fileName) }
                PairedButton(stringResource(R.string.settings_share), Icons.Rounded.Share, enabled = canExport, modifier = Modifier.weight(1f)) {
                    scope.launch {
                        val file = vm.shareFile() ?: return@launch
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/csv")
                            .putExtra(Intent.EXTRA_STREAM, file)
                            .putExtra(Intent.EXTRA_SUBJECT, draft.fileName)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, shareTitle))
                        vm.closeExport()
                    }
                }
            }
        }
    }

    // Outside the sheet: a dialog made inside it takes on the sheet's window and sticks to the bottom edge.
    when (picking) {
        DateEnd.FROM -> BtDatePickerDialog(draft.from, onPick = vm::setFrom, onClose = { picking = null }, latest = LocalDate.now())
        DateEnd.TO -> BtDatePickerDialog(draft.to, onPick = vm::setTo, onClose = { picking = null })
        null -> Unit
    }
}

private enum class DateEnd { FROM, TO }

@Composable
private fun DateButton(date: LocalDate, onClick: () -> Unit) {
    BtChip(
        text = DateLabels.dayMonthYear(date),
        selected = false,
        onClick = onClick,
        leading = { Icon(Icons.Rounded.CalendarMonth, null, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(18.dp)) },
    )
}

/** Two actions side by side where neither leads: equal buttons with a lime icon, as Transfer and Adjust balance are. */
@Composable
private fun PairedButton(text: String, icon: ImageVector, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = BtTheme.colors
    Row(
        modifier
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(BtShapes.md)
            .background(c.bgSurfaceRaised)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.accentText, modifier = Modifier.size(20.dp))
        Text(text, style = BtTheme.type.bodyStrong, color = c.textPrimary)
    }
}
