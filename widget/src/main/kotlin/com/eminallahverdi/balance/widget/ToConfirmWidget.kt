package com.eminallahverdi.balance.widget

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import com.eminallahverdi.balance.core.designsystem.theme.BtColors
import kotlinx.coroutines.flow.first

class ToConfirmWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val preferences = context.widgetData.preferences()
        val alerts = context.widgetData.bankAlerts()
        val firstPreferences = preferences.current()
        val firstCount = alerts.waitingCount.first()
        provideContent {
            val current by preferences.preferences.collectAsState(firstPreferences)
            val count by alerts.waitingCount.collectAsState(firstCount)
            ToConfirm(count, current.colors(context))
        }
    }
}

class ToConfirmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ToConfirmWidget()
}

@Composable
private fun ToConfirm(count: Int, c: BtColors) {
    WidgetSurface(c, openConfirmAction(LocalContext.current)) {
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(widgetString(R.string.widget_to_confirm_caps), style = widgetText(c.textMuted, 11.sp, FontWeight.Bold), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                WidgetIcon(Icons.Rounded.Notifications, if (count > 0) c.statusWarning else c.textMuted, 18.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$count", style = widgetText(c.textPrimary, 24.sp, FontWeight.Medium), maxLines = 1)
                Spacer(GlanceModifier.width(6.dp))
                Text(widgetPlural(R.plurals.widget_payments, count), style = widgetText(c.textSecondary, 12.sp), maxLines = 1)
            }
        }
    }
}
