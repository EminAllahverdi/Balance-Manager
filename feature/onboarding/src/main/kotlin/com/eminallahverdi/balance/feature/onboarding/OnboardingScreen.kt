package com.eminallahverdi.balance.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.AppLanguage
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.Language
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.ChoiceSheet
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.component.text
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.CsvPreview
import com.eminallahverdi.balance.feature.settings.BackupViewModel
import com.eminallahverdi.balance.feature.settings.DriveRestoreEffects
import com.eminallahverdi.balance.feature.settings.DriveRestoreSheet

private const val ICON_PIXELS = 288

/** First run. [onDone] once everything is written, which only happens on the very last tap. */
@Composable
fun OnboardingRoute(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    BackHandler(enabled = state.step != OnboardingStep.WELCOME, onBack = viewModel::back)
    Box(Modifier.fillMaxSize().background(BtTheme.colors.bgCanvas).systemBarsPadding().imePadding()) {
        when (state.step) {
            OnboardingStep.WELCOME -> WelcomeStep(viewModel)
            OnboardingStep.PEOPLE -> PeopleStep(state, viewModel)
            OnboardingStep.CATEGORIES -> CategoriesStep(state, viewModel)
            OnboardingStep.BACKUP -> BackupStep(state, viewModel)
            OnboardingStep.REMINDERS -> RemindersStep(state, viewModel)
            OnboardingStep.IMPORT -> ImportStep(state, viewModel)
        }
    }
}

/** After a restore, whose reminders come back already on: Android's permission belongs to this install, not the backup. */
@Composable
fun NotificationsAskRoute(onAnswered: () -> Unit) {
    Box(Modifier.fillMaxSize().background(BtTheme.colors.bgCanvas).systemBarsPadding()) {
        NotificationsPage(stringResource(R.string.onboarding_reminders_are_on), stringResource(R.string.onboarding_android_needs_ok), onAnswered)
    }
}

@Composable
private fun WelcomeStep(vm: OnboardingViewModel, backup: BackupViewModel = hiltViewModel()) {
    val c = BtTheme.colors
    val context = LocalContext.current
    val restore by backup.state.collectAsStateWithLifecycle()
    val icon = remember { context.packageManager.getApplicationIcon(context.packageName).toBitmap(ICON_PIXELS, ICON_PIXELS).asImageBitmap() }
    // Nothing here has been written yet, so restoring replaces nothing and needs no warning.
    DriveRestoreEffects(restore, backup)
    DriveRestoreSheet(restore, backup, onChoose = backup::restore)
    Column(Modifier.fillMaxSize()) {
        LanguageSwitch(Modifier.align(Alignment.End).padding(top = 12.dp, end = 16.dp))
        Centred {
            Image(icon, stringResource(R.string.onboarding_cibim_icon), Modifier.padding(bottom = 8.dp).size(96.dp).clip(RoundedCornerShape(24.dp)))
            Text("Cibim", style = BtTheme.type.titleLarge, color = c.textPrimary)
            Text(stringResource(R.string.onboarding_tagline), style = BtTheme.type.body, color = c.textSecondary, textAlign = TextAlign.Center)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PrimaryButton(stringResource(R.string.onboarding_get_started), { vm.start(importing = false) })
            BtCard {
                Column {
                    WelcomeRow(Icons.Rounded.CloudDownload, stringResource(R.string.onboarding_restore_drive), restore.message?.text() ?: stringResource(R.string.onboarding_restore_caption), backup::openRestore)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                    WelcomeRow(Icons.Rounded.UploadFile, stringResource(R.string.onboarding_import_csv), stringResource(R.string.onboarding_import_csv_caption)) { vm.start(importing = true) }
                }
            }
        }
    }
}

@Composable
private fun WelcomeRow(icon: ImageVector, title: String, caption: String, onClick: () -> Unit) {
    val c = BtTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = c.textSecondary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = BtTheme.type.bodyStrong, color = c.textPrimary)
            Text(caption, style = BtTheme.type.caption, color = c.textSecondary)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
    }
}

/**
 * Google is asked here, but backups only turn on at the last tap. An account that already has backups is offered them
 * first: starting fresh beside them would push them out of Drive within a week.
 */
@Composable
private fun BackupStep(state: OnboardingUiState, vm: OnboardingViewModel, backup: BackupViewModel = hiltViewModel()) {
    val c = BtTheme.colors
    val drive by backup.state.collectAsStateWithLifecycle()
    DriveRestoreEffects(drive, backup)
    DriveRestoreSheet(drive, backup, onChoose = backup::restore)
    LaunchedEffect(drive.existingBackups) {
        if (drive.existingBackups == 0) {
            backup.connectionUsed()
            vm.chooseBackup(true)
        }
    }
    val hasBackups = (drive.existingBackups ?: 0) > 0
    Column(Modifier.fillMaxSize()) {
        StepHeader(state.progress, vm::back)
        Centred {
            Medallion(Icons.Rounded.CloudUpload)
            Text(stringResource(R.string.onboarding_back_up_drive), style = BtTheme.type.titleLarge, color = c.textPrimary)
            Text(
                stringResource(R.string.onboarding_backup_explained),
                style = BtTheme.type.body,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
            if (hasBackups) {
                Text(stringResource(R.string.onboarding_has_backups), style = BtTheme.type.caption, color = c.statusWarning, textAlign = TextAlign.Center)
            } else {
                drive.message?.let { Text(it.text(), style = BtTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Center) }
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (hasBackups) {
                PrimaryButton(stringResource(R.string.onboarding_restore_a_backup), backup::openRestore)
                QuietButton(stringResource(R.string.onboarding_start_fresh)) {
                    backup.connectionUsed()
                    vm.chooseBackup(true)
                }
            } else {
                PrimaryButton(stringResource(R.string.onboarding_turn_on_backups), backup::connect)
                QuietButton(stringResource(R.string.onboarding_not_now)) { vm.chooseBackup(false) }
            }
        }
    }
}

@Composable
private fun RemindersStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    Column(Modifier.fillMaxSize()) {
        StepHeader(state.progress, vm::back)
        NotificationsPage(
            stringResource(R.string.onboarding_reminders),
            stringResource(R.string.onboarding_reminders_explained, DateLabels.timeOfDay(state.reminderMinute)),
            vm::notificationsAnswered,
        )
    }
}

/** Android's question only appears on the versions that have one, so this only shows on those. */
@Composable
private fun NotificationsPage(title: String, text: String, onAnswered: () -> Unit) {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onAnswered() }
    Column(Modifier.fillMaxSize()) {
        Centred {
            Medallion(Icons.Rounded.Notifications)
            Text(title, style = BtTheme.type.titleLarge, color = BtTheme.colors.textPrimary)
            Text(text, style = BtTheme.type.body, color = BtTheme.colors.textSecondary, textAlign = TextAlign.Center)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PrimaryButton(stringResource(R.string.onboarding_allow_notifications), {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else onAnswered()
            })
            QuietButton(stringResource(R.string.onboarding_not_now), onAnswered)
        }
    }
}

@Composable
private fun ImportStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    val c = BtTheme.colors
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file -> file?.let(vm::chooseFile) }
    Column(Modifier.fillMaxSize()) {
        StepHeader(state.progress, vm::back)
        Centred {
            Medallion(Icons.Rounded.UploadFile)
            Text(stringResource(R.string.onboarding_import_csv), style = BtTheme.type.titleLarge, color = c.textPrimary)
            Text(
                stringResource(R.string.onboarding_csv_needs),
                style = BtTheme.type.body,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
            state.message?.let { Text(it.text(), style = BtTheme.type.caption, color = c.statusWarning, textAlign = TextAlign.Center) }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PrimaryButton(stringResource(R.string.onboarding_choose_file), { pick.launch(arrayOf("*/*")) }, enabled = !state.saving)
            QuietButton(stringResource(R.string.onboarding_skip), vm::skipImport)
        }
    }
    state.preview?.let { preview ->
        ChoiceSheet(stringResource(R.string.onboarding_import_csv), onClose = vm::closePreview) { choose ->
            ImportSummary(preview)
            PrimaryButton(stringResource(R.string.onboarding_import_action), { choose(vm::runImport) })
        }
    }
}

@Composable
private fun ImportSummary(preview: CsvPreview) {
    val c = BtTheme.colors
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val entries = pluralStringResource(R.plurals.onboarding_entries, preview.entries, preview.entries)
        val headline = if (preview.firstYear == preview.lastYear) stringResource(R.string.onboarding_entries_in_year, entries, preview.firstYear)
        else stringResource(R.string.onboarding_entries_years, entries, preview.firstYear, preview.lastYear)
        Text(headline, style = BtTheme.type.bodyStrong, color = c.textPrimary)
        Text(stringResource(R.string.onboarding_accounts_list, preview.accounts.joinToString(", ")), style = BtTheme.type.caption, color = c.textSecondary)
        if (preview.newCategories.isNotEmpty()) {
            Text(stringResource(R.string.onboarding_new_categories, preview.newCategories.joinToString(", ")), style = BtTheme.type.caption, color = c.textSecondary)
        }
        if (preview.unreadable > 0) {
            Text(pluralStringResource(R.plurals.onboarding_lines_unreadable, preview.unreadable, preview.unreadable), style = BtTheme.type.caption, color = c.statusWarning)
        }
    }
}

@Composable
internal fun StepHeader(progress: Pair<Int, Int>, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.onboarding_back), onBack)
        Box(Modifier.weight(1f))
        Text(stringResource(R.string.onboarding_step_of, progress.first, progress.second), style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
    }
}

@Composable
private fun ColumnScope.Centred(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        content = content,
    )
}

@Composable
private fun Medallion(icon: ImageVector) {
    Box(Modifier.padding(bottom = 8.dp).size(72.dp).clip(CircleShape).background(BtTheme.colors.bgSurfaceRaised), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = BtTheme.colors.textSecondary, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun QuietButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(BtShapes.full).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BtTheme.type.label, color = BtTheme.colors.textSecondary) }
}

/** English or Azerbaijani, before anything is set up, for someone who can't read the one the phone picked. */
@Composable
private fun LanguageSwitch(modifier: Modifier) {
    val context = LocalContext.current
    val chosen by Language.flow.collectAsStateWithLifecycle()
    val azerbaijani = chosen == AppLanguage.AZERBAIJANI || (chosen == AppLanguage.SYSTEM && Language.isAzerbaijani)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BtChip("English", selected = !azerbaijani, onClick = { Language.set(context, AppLanguage.ENGLISH) })
        BtChip("Azərbaycanca", selected = azerbaijani, onClick = { Language.set(context, AppLanguage.AZERBAIJANI) })
    }
}
