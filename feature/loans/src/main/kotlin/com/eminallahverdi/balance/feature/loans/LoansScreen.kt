package com.eminallahverdi.balance.feature.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.eminallahverdi.balance.core.designsystem.component.FittedAmount
import com.eminallahverdi.balance.core.designsystem.component.FloatingAddButton
import com.eminallahverdi.balance.core.designsystem.component.Overline
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.DebtBalance
import com.eminallahverdi.balance.core.model.DebtDirection
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.RecurringPayment

private const val LOAN_COLOR = 0xFFF5B544
private const val DUE_SOON_DAYS = 7L

@Composable
fun LoansRoute(onBack: () -> Unit, viewModel: LoansViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = BtTheme.colors

    Box(Modifier.fillMaxSize().background(c.bgCanvas)) {
        // The bottom padding keeps the last row clear of the add button.
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BtIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                    Text("Loans & debts", style = BtTheme.type.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                }
            }
            if (!state.loading && state.isEmpty) {
                item {
                    Text(
                        "Add a bank loan, or note what you owe someone and what they owe you.",
                        style = BtTheme.type.body,
                        color = c.textMuted,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
            if (state.loans.isNotEmpty()) {
                item { Summary(state) }
                item(key = "bank") { Overline("Bank loans", Modifier.padding(top = 4.dp, start = 4.dp)) }
                items(state.loans, key = { "l${it.id}" }) { loan -> LoanCard(loan, state, onClick = { viewModel.editLoan(loan) }) }
            }
            if (state.cards.isNotEmpty()) {
                item(key = "cards") { Overline("Cards", Modifier.padding(top = 4.dp, start = 4.dp)) }
                items(state.cards, key = { "c${it.account.id}" }) { CardRow(it) }
            }
            if (state.openDebts.isNotEmpty()) {
                item(key = "people") { Overline("People", Modifier.padding(top = 4.dp, start = 4.dp)) }
                items(state.openDebts, key = { "p${it.name.lowercase()}${it.direction}" }) { person -> PersonCard(person, state, viewModel) }
            }
        }
        FloatingAddButton("New loan or debt", viewModel::openNew, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp))
    }

    when (val sheet = state.sheet) {
        LoansSheet.New -> NewEntrySheet(viewModel)
        is LoansSheet.Loan -> LoanEditorSheet(sheet.draft, state, viewModel)
        is LoansSheet.NewDebt -> DebtEditorSheet(sheet.draft, state, viewModel)
        is LoansSheet.NewBill -> BillEditorSheet(sheet.draft, state, viewModel)
        is LoansSheet.Pay -> PaySheet(sheet.draft, state, viewModel)
        null -> Unit
    }
}

@Composable
private fun Summary(state: LoansUiState) {
    val c = BtTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(BtShapes.md).background(c.bgSurfaceRaised).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Overline("Due in ${DateLabels.monthName(state.today)}")
            FittedAmount(MoneyFormat.format(state.dueThisMonth), BtTheme.type.amountLarge)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Overline("Still to pay")
            FittedAmount(MoneyFormat.format(state.stillToPay), BtTheme.type.amountMedium)
        }
    }
}

@Composable
private fun LoanCard(loan: RecurringPayment, state: LoansUiState, onClick: () -> Unit) {
    val c = BtTheme.colors
    val terms = loan.loan ?: return
    val last = terms.lastPayment
    val subtitle = when {
        loan.isFinished -> "Paid off"
        last != null -> "Last payment ${MoneyFormat.format(last)}"
        else -> "Monthly · ${state.accounts.firstOrNull { it.id == loan.accountId }?.name.orEmpty()}"
    }
    val dueSoon = !loan.isFinished && !loan.nextDate.isAfter(state.today.plusDays(DUE_SOON_DAYS))
    BtCard(Modifier.clip(BtShapes.md).clickable(onClick = onClick), bordered = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryTile("account_balance", LOAN_COLOR)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(loan.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(MoneyFormat.format(loan.amount), style = BtTheme.type.amountMedium, color = c.textPrimary)
                    Text("per month", style = BtTheme.type.caption, color = c.textSecondary)
                }
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(BtShapes.full).background(c.bgSurfaceHigh)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(terms.progress).clip(BtShapes.full).background(c.accentFill))
            }
            if (!loan.isFinished) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val left = terms.paymentsLeft
                    Text(
                        "${if (left == 1) "1 payment" else "$left payments"} left · ${MoneyFormat.format(loan.stillToPay)}",
                        style = BtTheme.type.caption,
                        color = c.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    val tint = if (dueSoon) c.statusWarning else c.textSecondary
                    Icon(Icons.Rounded.Schedule, null, tint = tint, modifier = Modifier.size(14.dp))
                    Text(" ${DateLabels.relative(loan.nextDate, state.today)}", style = BtTheme.type.caption, color = tint)
                }
            }
        }
    }
}

@Composable
private fun CardRow(card: CardUse) {
    val c = BtTheme.colors
    BtCard {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryTile(card.account.icon, card.account.color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(card.account.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Used this month", style = BtTheme.type.caption, color = c.textSecondary)
            }
            Text(MoneyFormat.format(card.used), style = BtTheme.type.amountMedium, color = c.textPrimary)
        }
    }
}

@Composable
private fun PersonCard(person: PersonDebts, state: LoansUiState, vm: LoansViewModel) {
    val c = BtTheme.colors
    val iOwe = person.direction == DebtDirection.I_OWE
    BtCard {
        Column {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(BtShapes.full).background(c.bgSurfaceHigh), contentAlignment = Alignment.Center) {
                    Text(person.name.take(1).uppercase(), style = BtTheme.type.bodyStrong, color = c.textPrimary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(person.name, style = BtTheme.type.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (iOwe) "I owe" else "Owes me", style = BtTheme.type.caption, color = if (iOwe) c.moneyExpense else c.moneyIncome)
                }
                Text(amount(person.total, person.isSensitive, state), style = BtTheme.type.amountMedium, color = c.textPrimary)
            }
            person.debts.forEach { balance ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                DebtRow(balance, state, iOwe, onPay = { vm.openPay(listOf(balance)) })
            }
            if (person.debts.size > 1) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderDefault))
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.End) {
                    val total = amount(person.total, person.isSensitive, state)
                    BtChip(if (iOwe) "Pay all $total" else "Received all $total", selected = true, onClick = { vm.openPay(person.debts) })
                }
            }
        }
    }
}

@Composable
private fun DebtRow(balance: DebtBalance, state: LoansUiState, iOwe: Boolean, onPay: () -> Unit) {
    val c = BtTheme.colors
    val debt = balance.debt
    val title = debt.note.ifBlank { state.category(debt.categoryId)?.name ?: if (iOwe) "Borrowed" else "Lent" }
    // With no note the title already is the category, so only what's above it is left to say.
    val where = if (debt.note.isBlank()) state.category(state.category(debt.categoryId)?.parentId)?.name else state.categoryPath(debt.categoryId)
    val details = listOfNotNull(where, DateLabels.relative(debt.date, state.today)).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPay).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = BtTheme.type.body, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(details, style = BtTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(amount(balance.left, debt.movesMoney, state), style = BtTheme.type.amountSmall, color = c.textPrimary)
        BtChip(if (iOwe) "Pay" else "Received", selected = false, onClick = onPay)
    }
}

/** A share of lunch is spending, which the eye leaves visible. A plain loan moves balances, so it hides. */
private fun amount(money: Money, sensitive: Boolean, state: LoansUiState): String =
    if (state.hideAmounts && sensitive) MoneyFormat.HIDDEN else MoneyFormat.format(money)
