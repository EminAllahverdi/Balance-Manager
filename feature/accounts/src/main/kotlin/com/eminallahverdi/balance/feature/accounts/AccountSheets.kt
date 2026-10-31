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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtChip
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
        EditorTitle(if (editor.isNew) "New account" else "Edit account", onRemove = if (canRemove) ({ confirmRemove = true }) else null) {
            CategoryTile(account.icon, account.color)
        }
        BtTextField(account.name, "Name", { vm.update(account.copy(name = it)) })
        EditorField("Type") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountType.entries.forEach { type ->
                    BtChip(type.label, account.type == type, { vm.update(account.copy(type = type)) })
                }
            }
        }
        EditorField("Icon") {
            IconPicker(account.color, account.icon, { vm.update(account.copy(icon = it)) }, keys = ACCOUNT_ICONS)
        }
        EditorField("Colour") {
            ColourPicker(CategoryPalette, account.color) { vm.update(account.copy(color = it)) }
        }
        PrimaryButton("Save", vm::saveEditor, enabled = account.name.isNotBlank())
    }

    // Outside the sheet: a dialog created inside it takes on the sheet's window and sticks to the bottom edge.
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${account.name}?") },
            text = { Text("If it has transactions it's archived, so your history stays the same, and its balance leaves your total.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeEditing() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdjustBalanceSheet(draft: AccountsSheet.Adjust, state: AccountsUiState, vm: AccountsViewModel) {
    val c = BtTheme.colors
    val current = state.balanceOf(draft.accountId)
    val target = parseAmountInput(draft.amountText)

    AccountsSheetFrame(vm::closeSheet) {
        Text("Adjust balance", style = BtTheme.type.titleMedium, color = c.textPrimary)
        EditorField("Account") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.balances.forEach { (account, _) ->
                    BtChip(account.name, account.id == draft.accountId, { vm.selectAdjustAccount(account.id) }, leading = { PersonDot(account.color) })
                }
            }
        }
        EditorField("Actual balance") {
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
            (if (difference.minor > 0) " · Change +" else " · Change ") + MoneyFormat.format(difference, signed = true)
        }.orEmpty()
        Text("Now $now$change", style = BtTheme.type.caption, color = c.textSecondary)
        Text("This adds a correction so the account matches your bank. It isn't counted as spending or income.", style = BtTheme.type.caption, color = c.textMuted)
        PrimaryButton("Save balance", vm::saveAdjust, enabled = target != null)
    }
}
