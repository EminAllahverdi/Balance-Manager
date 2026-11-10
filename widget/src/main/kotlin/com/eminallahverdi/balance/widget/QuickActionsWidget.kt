package com.eminallahverdi.balance.widget

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.eminallahverdi.balance.core.designsystem.theme.BtColors
import com.eminallahverdi.balance.core.model.TransactionKind

private val EDGE = 10.dp
private val GAP = 8.dp

// Expense gets the most room. Glance rows can only weigh children equally, so the widths are worked out.
// Expense leads; Transfer and Debt are icons alone, so all four fit a 4x1 widget with the words still whole.
private const val EXPENSE_SHARE = 1.8f / 4.8f
private const val INCOME_SHARE = 1.4f / 4.8f
private const val TRANSFER_SHARE = 0.8f / 4.8f
private const val DEBT_SHARE = 0.8f / 4.8f

class QuickActionsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val preferences = context.widgetData.preferences()
        val first = preferences.current()
        provideContent {
            val current by preferences.preferences.collectAsState(first)
            QuickActions(current.colors(context))
        }
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = QuickActionsWidget()
}

@Composable
private fun QuickActions(c: BtColors) {
    val context = LocalContext.current
    val room = LocalSize.current.width - EDGE * 2 - GAP * 3 - 2.dp
    WidgetSurface(c) {
        Row(GlanceModifier.fillMaxSize().padding(EDGE)) {
            ActionButton(quickAddAction(context, TransactionKind.EXPENSE), c.accentFill, room * EXPENSE_SHARE) {
                WidgetIcon(Icons.Rounded.Remove, c.accentOn, 22.dp)
                Spacer(GlanceModifier.width(6.dp))
                Text(widgetString(R.string.widget_expense), style = widgetText(c.accentOn, 15.sp, FontWeight.Bold), maxLines = 1)
            }
            Spacer(GlanceModifier.width(GAP))
            ActionButton(quickAddAction(context, TransactionKind.INCOME), c.bgSurfaceHigh, room * INCOME_SHARE) {
                WidgetIcon(Icons.Rounded.Add, c.moneyIncome, 22.dp)
                Spacer(GlanceModifier.width(6.dp))
                Text(widgetString(R.string.widget_income), style = widgetText(c.textPrimary, 15.sp, FontWeight.Bold), maxLines = 1)
            }
            Spacer(GlanceModifier.width(GAP))
            ActionButton(quickAddAction(context, TransactionKind.TRANSFER), c.bgSurfaceHigh, room * TRANSFER_SHARE) {
                WidgetIcon(Icons.Rounded.SwapHoriz, c.textPrimary, 24.dp, widgetString(R.string.widget_transfer))
            }
            Spacer(GlanceModifier.width(GAP))
            ActionButton(quickAddAction(context, TransactionKind.DEBT), c.bgSurfaceHigh, room * DEBT_SHARE) {
                WidgetIcon(Icons.Rounded.Handshake, c.textPrimary, 24.dp, widgetString(R.string.widget_debt))
            }
        }
    }
}

@Composable
private fun ActionButton(action: Action, fill: Color, width: Dp, content: @Composable RowScope.() -> Unit) {
    Row(
        GlanceModifier.width(width).fillMaxHeight().cornerRadius(20.dp).background(ColorProvider(fill)).clickable(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
