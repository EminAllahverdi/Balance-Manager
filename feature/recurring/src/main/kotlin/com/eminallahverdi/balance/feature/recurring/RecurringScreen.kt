package com.eminallahverdi.balance.feature.recurring

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.component.BtCard
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.component.BtIconButton
import com.eminallahverdi.balance.core.designsystem.component.CategoryTile
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.component.PrimaryButton
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.RecurringFrequency
import com.eminallahverdi.balance.core.model.RecurringPayment

internal val RecurringFrequency.label: String
    get() = when (this) {
        RecurringFrequency.WEEKLY -> "Weekly"
        RecurringFrequency.MONTHLY -> "Monthly"
        RecurringFrequency.YEARLY -> "Yearly"
    }

/** Reminders for what repeats. [onAddPayment] opens quick add filled in from a payment, for the user to save themselves. */
@Composable
fun RecurringRoute(onBack: () -> Unit, onAddPayment: (Long) -> Unit, viewModel: RecurringViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = BtTheme.colors
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LazyColumn(
        Modifier.fillMaxSize().background(c.bgCanvas).navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Text("Recurring payments", style = BtTheme.type.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                BtIconButton(Icons.Rounded.Add, "New payment", viewModel::newPayment)
            }
        }
        if (!state.loading && state.payments.isEmpty()) {
            item {
                Text(
                    "Add subscriptions, rent or loan payments. You get a reminder if one isn't in your list by its date, ready to log with a tap.",
                    style = BtTheme.type.body,
                    color = c.textMuted,
                    modifier = Modifier.padding(4.dp),
                )
            }
            item { PrimaryButton("Add a payment", viewModel::newPayment) }
        }
        if (state.payments.isNotEmpty()) {
            item {
                val count = state.payments.size
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Overline(if (count == 1) "About a month · 1 payment" else "About a month · $count payments")
                    Text(MoneyFormat.format(state.monthlyCost), style = BtTheme.type.amountLarge, color = c.textPrimary)
                }
            }
        }
        if (state.due.isNotEmpty()) {
            item(key = "due") { Overline("Due", Modifier.padding(top = 8.dp, start = 4.dp)) }
            items(state.due, key = { "d${it.id}" }) { payment -> DueCard(payment, state, viewModel, onAddPayment) }
        }
        if (state.upcoming.isNotEmpty()) {
            item(key = "upcoming") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Overline("Upcoming", Modifier.padding(top = 8.dp, start = 4.dp))
                    BtCard {
                        Column {
                            state.upcoming.forEachIndexed { i, payment ->
                                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                                PaymentRow(payment, state, Modifier.clickable { viewModel.edit(payment) })
                            }
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { draft ->
        RecurringEditorSheet(draft, state, viewModel) {
            // The reminder is the point of a payment, so this is when to ask. Android only shows it once.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            viewModel.saveEditor()
        }
    }
}

@Composable
private fun DueCard(payment: RecurringPayment, state: RecurringUiState, vm: RecurringViewModel, onAddPayment: (Long) -> Unit) {
    BtCard(bordered = true) {
        Column {
            PaymentRow(payment, state, Modifier.clickable { vm.edit(payment) })
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                BtChip("Skip", selected = false, onClick = { vm.skip(payment) })
                BtChip("Add", selected = true, onClick = { onAddPayment(payment.id) })
            }
        }
    }
}

@Composable
private fun PaymentRow(payment: RecurringPayment, state: RecurringUiState, modifier: Modifier = Modifier) {
    val c = BtTheme.colors
    val category = state.category(payment.categoryId)
    val meta = listOfNotNull(
        payment.frequency.label,
        DateLabels.relative(payment.nextDate, state.today),
        state.account(payment.accountId)?.name,
    ).joinToString(" · ")
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryTile(category?.icon ?: "more_horiz", category?.color ?: 0xFF8A94A3)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(payment.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(MoneyFormat.format(payment.amountDue), style = BtTheme.type.amountMedium, color = c.textPrimary)
    }
}
