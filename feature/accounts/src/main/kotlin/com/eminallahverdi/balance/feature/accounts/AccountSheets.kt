package com.eminallahverdi.balance.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.BtSwitch
import com.eminallahverdi.balance.core.designsystem.component.BtTextField
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.ColourPicker
import com.eminallahverdi.balance.core.designsystem.component.EditorField
import com.eminallahverdi.balance.core.designsystem.component.EditorTitle
import com.eminallahverdi.balance.core.designsystem.component.IconPicker
import com.eminallahverdi.balance.core.designsystem.component.PersonDot
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.designsystem.theme.CategoryPalette
import com.eminallahverdi.balance.core.model.AccountType

private val ACCOUNT_ICONS = listOf("credit_card", "payments", "account_balance_wallet", "account_balance", "savings")
private const val CARD_DIGITS = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountsSheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BtTheme.colors.bgSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountEditorSheet(editor: AccountsSheet.Editor, state: AccountsUiState, vm: AccountsViewModel) {
    val account = editor.account
    var confirmRemove by remember { mutableStateOf(false) }
    val canRemove = !editor.isNew && state.canRemoveAccount

    AccountsSheetFrame(vm::closeSheet) {
        EditorTitle(if (editor.isNew) stringResource(R.string.accounts_new_account) else stringResource(R.string.accounts_edit_account), onRemove = if (canRemove) ({ confirmRemove = true }) else null) {
            CategoryTile(account.icon, account.color)
        }
        BtTextField(account.name, stringResource(R.string.accounts_name), { vm.update(account.copy(name = it)) })
        EditorField(stringResource(R.string.accounts_type)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountType.entries.forEach { type ->
                    BtChip(type.label, account.type == type, { vm.update(account.copy(type = type)) })
                }
            }
        }
        if (account.type != AccountType.CASH) {
            EditorField(stringResource(R.string.accounts_card_digits)) {
                BtTextField(
                    value = account.cardEnding,
                    placeholder = "",
                    onChange = { vm.update(account.copy(cardEnding = it.filter(Char::isDigit).take(CARD_DIGITS))) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(stringResource(R.string.accounts_card_digits_hint), style = BtTheme.type.caption, color = BtTheme.colors.textMuted)
            }
        }
        // How the account takes part: where expenses start, what Total counts, and what the balance widgets add up.
        Column(Modifier.fillMaxWidth().clip(BtShapes.md).background(BtTheme.colors.bgSurfaceRaised)) {
            SwitchRow(
                icon = if (editor.isDefault) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                iconTint = if (editor.isDefault) BtTheme.colors.accentText else BtTheme.colors.textSecondary,
                title = stringResource(R.string.accounts_default_for_expenses),
                caption = if (editor.wasDefault) stringResource(R.string.accounts_default_on_caption) else stringResource(R.string.accounts_default_off_caption),
                checked = editor.isDefault,
                onChange = vm::setEditorDefault,
            )
            SwitchDivider()
            SwitchRow(Icons.Rounded.Functions, BtTheme.colors.textSecondary, stringResource(R.string.accounts_count_in_total), stringResource(R.string.accounts_count_in_total_caption), account.includeInTotal) {
                vm.update(account.copy(includeInTotal = it))
            }
            SwitchDivider()
            SwitchRow(Icons.Rounded.Widgets, BtTheme.colors.textSecondary, stringResource(R.string.accounts_show_on_widget), stringResource(R.string.accounts_show_on_widget_caption), account.showOnWidget) {
                vm.update(account.copy(showOnWidget = it))
            }
        }
        EditorField(stringResource(R.string.accounts_icon)) {
            IconPicker(account.color, account.icon, { vm.update(account.copy(icon = it)) }, keys = ACCOUNT_ICONS)
        }
        EditorField(stringResource(R.string.accounts_colour)) {
            ColourPicker(CategoryPalette, account.color, icon = account.icon) { vm.update(account.copy(color = it)) }
        }
        PrimaryButton(stringResource(R.string.accounts_save), vm::saveEditor, enabled = account.name.isNotBlank())
    }

    // Outside the sheet: a dialog created inside it takes on the sheet's window and sticks to the bottom edge.
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.accounts_remove_named, account.name)) },
            text = { Text(stringResource(R.string.accounts_remove_text)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeEditing() }) { Text(stringResource(R.string.accounts_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.accounts_cancel)) } },
        )
    }
}

/** One on/off line in a grouped box: the whole row toggles, not just the switch. */
@Composable
private fun SwitchRow(icon: ImageVector, iconTint: Color, title: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = BtTheme.type.bodyStrong, color = BtTheme.colors.textPrimary)
            Text(caption, style = BtTheme.type.caption, color = BtTheme.colors.textSecondary)
        }
        BtSwitch(checked, onChange)
    }
}

@Composable
private fun SwitchDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(BtTheme.colors.borderDefault))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdjustBalanceSheet(draft: AccountsSheet.Adjust, state: AccountsUiState, vm: AccountsViewModel) {
    val c = BtTheme.colors
    val current = state.balanceOf(draft.accountId)
    val target = parseAmountInput(draft.amountText)

    AccountsSheetFrame(vm::closeSheet) {
        Text(stringResource(R.string.accounts_adjust_balance), style = BtTheme.type.titleMedium, color = c.textPrimary)
        EditorField(stringResource(R.string.accounts_account)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.balances.forEach { (account, _) ->
                    BtChip(account.name, account.id == draft.accountId, { vm.selectAdjustAccount(account.id) }, leading = { PersonDot(account.color) })
                }
            }
        }
        EditorField(stringResource(R.string.accounts_actual_balance)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BtTextField(
                    value = draft.amountText,
                    placeholder = "${MoneyFormat.SYMBOL}0.00",
                    onChange = vm::setAdjustAmount,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                // Not every number keyboard has a minus key, and an overdrawn account needs one.
                Box(
                    Modifier.size(52.dp).clip(BtShapes.md).background(c.bgSurfaceRaised).clickable(role = Role.Button, onClick = vm::flipAdjustSign),
                    contentAlignment = Alignment.Center,
                ) { Text("+/−", style = BtTheme.type.label, color = c.textPrimary) }
            }
        }
        val now = if (state.hideAmounts) MoneyFormat.HIDDEN else MoneyFormat.format(current, signed = true)
        val change = target?.takeUnless { state.hideAmounts }?.let { it - current }?.let { difference ->
            " · " + stringResource(R.string.accounts_change_by, (if (difference.minor > 0) "+" else "") + MoneyFormat.format(difference, signed = true))
        }.orEmpty()
        Text(stringResource(R.string.accounts_now_balance, now) + change, style = BtTheme.type.caption, color = c.textSecondary)
        Text(stringResource(R.string.accounts_adjust_hint), style = BtTheme.type.caption, color = c.textMuted)
        PrimaryButton(stringResource(R.string.accounts_save_balance), vm::saveAdjust, enabled = target != null)
    }
}
