package com.eminallahverdi.balance.widget

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.datastore.UserPreferences
import com.eminallahverdi.balance.core.datastore.WidgetBalanceMode
import com.eminallahverdi.balance.core.designsystem.theme.BtColors
import com.eminallahverdi.balance.core.model.AccountBalance
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.core.model.sum
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

private val TEXT_START = 16.dp
private val TEXT_END = 8.dp

private const val AMOUNT_MAX_SP = 20f
private const val AMOUNT_MIN_SP = 12f

/** Widest a character of an amount runs, as a share of the text size. Separators are narrower, so this errs on the safe side. */
private const val AMOUNT_CHAR_WIDTH = 0.46f

/** Glance can't shrink text to fit, so the size comes from the room there is. Long amounts get smaller, never cut off. */
internal fun amountTextSize(amount: String, room: Dp, fontScale: Float): TextUnit =
    (room.value / (amount.length * AMOUNT_CHAR_WIDTH * fontScale)).coerceIn(AMOUNT_MIN_SP, AMOUNT_MAX_SP).sp

internal data class BalanceWidgetState(
    val colors: BtColors,
    val mode: WidgetBalanceMode,
    val total: Money,
    val accounts: String,
    val secondsLeft: Int,
) {
    val revealed: Boolean get() = when (mode) {
        WidgetBalanceMode.ALWAYS_SHOW -> true
        WidgetBalanceMode.ALWAYS_HIDE -> false
        WidgetBalanceMode.TAP_TO_SHOW, WidgetBalanceMode.FINGERPRINT -> secondsLeft > 0
    }

    val amount: String get() = if (revealed) MoneyFormat.format(total, signed = true) else MoneyFormat.HIDDEN

    val caption: BalanceCaption get() = when {
        mode == WidgetBalanceMode.ALWAYS_SHOW -> BalanceCaption.Accounts(accounts)
        mode == WidgetBalanceMode.ALWAYS_HIDE -> BalanceCaption.Hidden
        revealed -> BalanceCaption.HidesIn(secondsLeft)
        mode == WidgetBalanceMode.TAP_TO_SHOW -> BalanceCaption.TapToShow
        else -> BalanceCaption.TouchToShow
    }
}

/** The line under the balance: which accounts it adds up, or how to see it. */
internal sealed interface BalanceCaption {
    data class Accounts(val names: String) : BalanceCaption
    data object Hidden : BalanceCaption
    data class HidesIn(val seconds: Int) : BalanceCaption
    data object TapToShow : BalanceCaption
    data object TouchToShow : BalanceCaption
}

@Composable
internal fun BalanceCaption.text(): String = when (this) {
    is BalanceCaption.Accounts -> names
    BalanceCaption.Hidden -> widgetString(R.string.widget_hidden)
    is BalanceCaption.HidesIn -> widgetString(R.string.widget_hides_in, seconds)
    BalanceCaption.TapToShow -> widgetString(R.string.widget_tap_to_show)
    BalanceCaption.TouchToShow -> widgetString(R.string.widget_touch_to_show)
}

/** Only the accounts switched on for the widget in Accounts count here. */
internal fun balanceWidgetState(balances: List<AccountBalance>, preferences: UserPreferences, secondsLeft: Int, colors: BtColors): BalanceWidgetState {
    val shown = balances.filter { it.account.showOnWidget }
    return BalanceWidgetState(
        colors = colors,
        mode = preferences.widgetBalanceMode,
        total = shown.map { it.balance }.sum(),
        accounts = shown.joinToString(" + ") { it.account.name },
        secondsLeft = secondsLeft,
    )
}

class BalanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = context.widgetData
        val states = combine(data.transactions().balances, data.preferences().preferences, BalanceReveal.secondsLeft) { balances, preferences, secondsLeft ->
            balanceWidgetState(balances, preferences, secondsLeft, preferences.colors(context))
        }
        val first = states.first()
        provideContent {
            val state by states.collectAsState(first)
            BalanceContent(state)
        }
    }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = BalanceWidget()
}

/** Two tap areas: the + always adds an expense, the rest of the card follows the Widget balance setting. */
@Composable
private fun BalanceContent(state: BalanceWidgetState) {
    val c = state.colors
    val context = LocalContext.current
    val room = LocalSize.current.width - TEXT_START - TEXT_END - 2.dp
    val countingDown = state.revealed && state.mode != WidgetBalanceMode.ALWAYS_SHOW
    WidgetSurface(c, onClick = state.onTap(context)) {
        Column(GlanceModifier.fillMaxSize()) {
            // The amount sits below this row, so the + can never cover it.
            Row(GlanceModifier.fillMaxWidth()) {
                Text(widgetString(R.string.widget_balance_caps), GlanceModifier.defaultWeight().padding(start = TEXT_START, top = 14.dp), style = widgetText(c.textMuted, 11.sp, FontWeight.Bold))
                AddExpenseButton(c)
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                state.amount,
                GlanceModifier.padding(start = TEXT_START, end = TEXT_END),
                style = widgetText(c.textPrimary, amountTextSize(state.amount, room, context.resources.configuration.fontScale)),
                maxLines = 1,
            )
            Row(GlanceModifier.padding(start = TEXT_START, end = TEXT_END, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!countingDown) {
                    StatusIcon(state)
                    Spacer(GlanceModifier.width(4.dp))
                }
                Text(state.caption.text(), style = widgetText(if (countingDown) c.accentText else c.textSecondary, 12.sp), maxLines = 1)
            }
        }
    }
}

/** The tap area is bigger than the key, which has to stay small to leave the amount clear. */
@Composable
private fun AddExpenseButton(c: BtColors) {
    Box(
        GlanceModifier.size(44.dp, 38.dp).clickable(quickAddAction(LocalContext.current, TransactionKind.EXPENSE)),
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(GlanceModifier.padding(top = 7.dp, end = 7.dp)) {
            Box(GlanceModifier.size(30.dp).cornerRadius(11.dp).background(ColorProvider(c.bgSurfaceHigh)), contentAlignment = Alignment.Center) {
                WidgetIcon(Icons.Rounded.Add, c.accentText, 20.dp, widgetString(R.string.widget_add_expense))
            }
        }
    }
}

@Composable
internal fun StatusIcon(state: BalanceWidgetState) {
    val c = state.colors
    when (state.mode) {
        WidgetBalanceMode.ALWAYS_SHOW, WidgetBalanceMode.TAP_TO_SHOW -> WidgetIcon(Icons.Rounded.Visibility, c.textMuted, 14.dp)
        WidgetBalanceMode.ALWAYS_HIDE -> WidgetIcon(Icons.Rounded.VisibilityOff, c.textMuted, 14.dp)
        WidgetBalanceMode.FINGERPRINT -> WidgetIcon(Icons.Rounded.Fingerprint, c.accentText, 14.dp)
    }
}

internal fun BalanceWidgetState.onTap(context: Context): Action = when {
    revealed || mode == WidgetBalanceMode.ALWAYS_HIDE -> openAppAction(context)
    mode == WidgetBalanceMode.TAP_TO_SHOW -> actionSendBroadcast<BalanceRevealReceiver>()
    else -> actionStartActivity<WidgetUnlockActivity>()
}
