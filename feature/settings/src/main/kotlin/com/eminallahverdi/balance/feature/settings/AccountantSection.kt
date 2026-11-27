package com.eminallahverdi.balance.feature.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.designsystem.component.BtSwitch
import com.eminallahverdi.balance.core.designsystem.component.BtTextField

/**
 * The accountant copy's row under Backup, only once it's open on this phone: on or off, and when it last went to the
 * folder the accountant reads.
 */
@Composable
internal fun AccountantRow(viewModel: AccountantViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.saved.unlocked) return
    Divider()
    SettingRow(icon = Icons.Rounded.FolderShared, title = stringResource(R.string.settings_accountant), caption = state.caption()) {
        BtSwitch(state.saved.on, viewModel::setOn)
    }
}

@Composable
private fun AccountantUiState.caption(): String = when {
    sending -> stringResource(R.string.settings_accountant_sending)
    !saved.on -> stringResource(R.string.settings_accountant_off)
    !backupOn -> stringResource(R.string.settings_accountant_needs_backup)
    saved.failed -> stringResource(R.string.settings_accountant_failed)
    else -> saved.copiedAtMillis?.let { stringResource(R.string.settings_accountant_copied, backupTime(it)) } ?: stringResource(R.string.settings_accountant_not_yet)
}

/**
 * The PIN box, Google's screen for the files the app makes, and leaving Settings after a round of wrong tries. The box
 * gives nothing away: a letter keyboard, dots, no length and no count of tries.
 */
@Composable
internal fun AccountantEffects(onLeave: () -> Unit, viewModel: AccountantViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onConsent(result.data)
    }
    LaunchedEffect(state.consent) {
        state.consent?.let {
            viewModel.consentShown()
            consent.launch(IntentSenderRequest.Builder(it).build())
        }
    }
    LaunchedEffect(state.sentAway) {
        if (state.sentAway) {
            viewModel.sentAwayHandled()
            onLeave()
        }
    }
    if (!state.asking) return
    AlertDialog(
        onDismissRequest = viewModel::cancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.settings_accountant_pin)) },
        text = {
            BtTextField(
                value = state.typed,
                placeholder = "",
                onChange = viewModel::type,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                visualTransformation = PasswordVisualTransformation(),
            )
        },
        confirmButton = { TextButton(onClick = viewModel::submit) { Text(stringResource(R.string.settings_ok)) } },
    )
}
