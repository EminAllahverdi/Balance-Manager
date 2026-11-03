package com.eminallahverdi.balance.widget

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.eminallahverdi.balance.core.datastore.WidgetBalanceMode
import com.eminallahverdi.balance.core.model.TransactionKind
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

private val KEY = 48.dp
private val KEY_GAP = 8.dp
private val EDGE = 12.dp

/** The balance with a mic and a +: what's there, then adding to it by voice or by hand, from one 4x1 widget. */
class BalanceVoiceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = context.widgetData
        val states = combine(data.transactions().balances, data.preferences().preferences, BalanceReveal.secondsLeft) { balances, preferences, secondsLeft ->
            balanceWidgetState(balances, preferences, secondsLeft, preferences.colors(context))
        }
        val first = states.first()
        provideContent {
            val state by states.collectAsState(first)
            BalanceVoiceContent(state)
        }
    }
}

class BalanceVoiceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = BalanceVoiceWidget()
}

/** Three tap areas: the mic listens, the + adds an expense, and the balance follows the Widget balance setting. */
@Composable
private fun BalanceVoiceContent(state: BalanceWidgetState) {
    val c = state.colors
    val context = LocalContext.current
    val room = LocalSize.current.width - 16.dp - KEY * 2 - KEY_GAP - EDGE * 2 - 2.dp
    val countingDown = state.revealed && state.mode != WidgetBalanceMode.ALWAYS_SHOW
    WidgetSurface(c, onClick = state.onTap(context)) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("BALANCE", style = widgetText(c.textMuted, 11.sp, FontWeight.Bold))
                Text(state.amount, style = widgetText(c.textPrimary, amountTextSize(state.amount, room, context.resources.configuration.fontScale)), maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!countingDown) {
                        StatusIcon(state)
                        Spacer(GlanceModifier.width(4.dp))
                    }
                    Text(state.caption, style = widgetText(if (countingDown) c.accentText else c.textSecondary, 12.sp), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(EDGE))
            Key(Icons.Rounded.Mic, "Add by voice", c.accentOn, c.accentFill, voiceAction(context))
            Spacer(GlanceModifier.width(KEY_GAP))
            Key(Icons.Rounded.Add, "Add expense", c.accentText, c.bgSurfaceHigh, quickAddAction(context, TransactionKind.EXPENSE))
            Spacer(GlanceModifier.width(EDGE))
        }
    }
}

@Composable
private fun Key(icon: ImageVector, label: String, tint: Color, fill: Color, action: Action) {
    Box(
        GlanceModifier.size(KEY).cornerRadius(16.dp).background(ColorProvider(fill)).clickable(action),
        contentAlignment = Alignment.Center,
    ) { WidgetIcon(icon, tint, 24.dp, label) }
}
