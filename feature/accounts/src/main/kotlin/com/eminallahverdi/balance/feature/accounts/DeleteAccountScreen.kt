package com.eminallahverdi.balance.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.ChoiceSheet
import com.eminallahverdi.balance.core.designsystem.component.OptionRow
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.Account
import com.eminallahverdi.balance.core.model.RecurringPayment
import com.eminallahverdi.balance.feature.history.DayGroup
import com.eminallahverdi.balance.feature.history.rememberRowTap

/**
 * Deleting an archived account: everything it would take with it, listed as History lists it. [onOpen] opens one entry to
 * change or delete. Moving them all is the safe way out, so that's the main button.
 */
@Composable
fun DeleteAccountRoute(onBack: () -> Unit, onOpen: (Long) -> Unit, viewModel: DeleteAccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = BtTheme.colors
    var picking by rememberSaveable { mutableStateOf(false) }
    var target by remember { mutableStateOf<Account?>(null) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val onTap = rememberRowTap(onOpen, viewModel::deleteEntry)

    LaunchedEffect(state.isGone) { if (state.isGone) onBack() }

    Column(Modifier.fillMaxSize().background(c.bgCanvas).navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Text("Delete ${state.account?.name.orEmpty()}", style = BtTheme.type.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!state.loading) {
                item(key = "intro") {
                    Text(state.intro, style = BtTheme.type.body, color = c.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            if (state.payments.isNotEmpty()) item(key = "payments") { RecurringPayments(state.payments) }
            items(state.days, key = { it.date.toEpochDay() }) { DayGroup(it, state.hideAmounts, onTap) }
        }
        if (!state.loading) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.isUnused) {
                    PrimaryButton("Delete account", viewModel::deleteAccount)
                } else {
                    PrimaryButton("Move all to another account", { picking = true }, enabled = state.targets.isNotEmpty())
                    Box(
                        Modifier.padding(top = 4.dp).heightIn(min = 48.dp).clip(BtShapes.full).clickable(role = Role.Button) { confirmingDelete = true }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(state.deleteLabel, style = BtTheme.type.label, color = c.moneyExpense) }
                }
            }
        }
    }

    if (picking) {
        ChoiceSheet("Move to", onClose = { picking = false }) { choose ->
            state.targets.forEach { account ->
                OptionRow(text = account.name, selected = false, caption = account.type.label, onClick = { choose { target = account } })
            }
        }
    }
    target?.let { account ->
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text(state.moveTitle(account)) },
            text = { Text(state.moveWarning(account)) },
            confirmButton = { TextButton(onClick = { target = null; viewModel.moveAll(account) }) { Text("Move") } },
            dismissButton = { TextButton(onClick = { target = null }) { Text("Cancel") } },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(state.deleteTitle) },
            text = { Text(state.deleteWarning) },
            confirmButton = { TextButton(onClick = { confirmingDelete = false; viewModel.deleteAccount() }) { Text("Delete", color = c.moneyExpense) } },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RecurringPayments(payments: List<RecurringPayment>) {
    val c = BtTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline("Recurring payments", Modifier.padding(horizontal = 4.dp))
        BtCard {
            Column {
                payments.forEachIndexed { i, payment ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(payment.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(MoneyFormat.format(payment.amountDue), style = BtTheme.type.amountMedium, color = c.textPrimary)
                    }
                }
            }
        }
    }
}
