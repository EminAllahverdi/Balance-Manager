package com.eminallahverdi.balance.widget

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.datastore.UserPreferences
import com.eminallahverdi.balance.core.datastore.WidgetBalanceMode
import com.eminallahverdi.balance.core.designsystem.theme.BtColors
import com.eminallahverdi.balance.core.model.AccountBalance
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.sum
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

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

    val caption: String get() = when {
        mode == WidgetBalanceMode.ALWAYS_SHOW -> accounts
        mode == WidgetBalanceMode.ALWAYS_HIDE -> "Hidden"
        revealed -> "Hides in $secondsLeft s"
        mode == WidgetBalanceMode.TAP_TO_SHOW -> "Tap to show"
        else -> "Touch to show"
    }
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

@Composable
private fun BalanceContent(state: BalanceWidgetState) {
    val c = state.colors
    val context = LocalContext.current
    WidgetSurface(c, onClick = state.onTap(context)) {
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("BALANCE", GlanceModifier.defaultWeight(), style = widgetText(c.textMuted, 11.sp, FontWeight.Bold))
                StatusIcon(state)
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(state.amount, style = widgetText(c.textPrimary, 24.sp), maxLines = 1)
            if (state.caption.isNotEmpty()) {
                val captionColor = if (state.secondsLeft > 0 && state.revealed && state.mode != WidgetBalanceMode.ALWAYS_SHOW) c.accentText else c.textSecondary
                Text(state.caption, style = widgetText(captionColor, 12.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatusIcon(state: BalanceWidgetState) {
    val c = state.colors
    when {
        state.mode == WidgetBalanceMode.ALWAYS_SHOW -> WidgetIcon(Icons.Rounded.Visibility, c.textMuted, 18.dp)
        state.mode == WidgetBalanceMode.ALWAYS_HIDE -> WidgetIcon(Icons.Rounded.VisibilityOff, c.textMuted, 18.dp)
        state.revealed -> CircularProgressIndicator(GlanceModifier.size(18.dp), color = ColorProvider(c.accentFill))
        state.mode == WidgetBalanceMode.TAP_TO_SHOW -> WidgetIcon(Icons.Rounded.Visibility, c.textMuted, 18.dp)
        else -> WidgetIcon(Icons.Rounded.Fingerprint, c.accentText, 20.dp)
    }
}

private fun BalanceWidgetState.onTap(context: Context): Action = when {
    revealed || mode == WidgetBalanceMode.ALWAYS_HIDE -> openAppAction(context)
    mode == WidgetBalanceMode.TAP_TO_SHOW -> actionSendBroadcast<BalanceRevealReceiver>()
    else -> actionStartActivity<WidgetUnlockActivity>()
}
