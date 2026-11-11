package com.eminallahverdi.balance.widget

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.eminallahverdi.balance.core.common.DateLabels
import com.eminallahverdi.balance.core.common.Language
import com.eminallahverdi.balance.core.common.MoneyFormat
import com.eminallahverdi.balance.core.designsystem.icon.BtIcons
import com.eminallahverdi.balance.core.designsystem.theme.BtColors
import com.eminallahverdi.balance.core.designsystem.theme.tileGlyph
import com.eminallahverdi.balance.core.model.Category
import com.eminallahverdi.balance.core.model.CategoryType
import com.eminallahverdi.balance.core.model.DateRange
import com.eminallahverdi.balance.core.model.Money
import com.eminallahverdi.balance.core.model.TransactionDetails
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.core.model.sum
import java.time.LocalDate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

private const val QUICK_CATEGORIES = 4

internal data class TodayWidgetState(
    val colors: BtColors,
    val date: LocalDate,
    val spent: Money,
    val entries: Int,
    val categories: List<Category>,
)

/** The one-tap categories are the first ones in the user's own order, so dragging them in Categories changes the widget. */
internal fun todayWidgetState(date: LocalDate, items: List<TransactionDetails>, categories: List<Category>, colors: BtColors): TodayWidgetState {
    val expenses = items.filter { it.transaction.kind == TransactionKind.EXPENSE }
    return TodayWidgetState(
        colors = colors,
        date = date,
        spent = expenses.map { it.transaction.amount }.sum(),
        entries = expenses.size,
        categories = categories.filter { it.parentId == null && it.type == CategoryType.EXPENSE && !it.isArchived }.take(QUICK_CATEGORIES),
    )
}

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = context.widgetData
        val today = LocalDate.now()
        val states = combine(data.transactions().observeDetails(DateRange(today, today)), data.catalog().categories, data.preferences().preferences) { items, categories, preferences ->
            todayWidgetState(today, items, categories, preferences.colors(context))
        }
        val first = states.first()
        provideContent {
            val state by states.collectAsState(first)
            TodayContent(state)
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TodayWidget()
}

@Composable
private fun TodayContent(state: TodayWidgetState) {
    val c = state.colors
    val context = LocalContext.current
    WidgetSurface(c) {
        Column(GlanceModifier.fillMaxSize().padding(16.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(DateLabels.dayHeader(state.date, state.date).uppercase(Language.locale), style = widgetText(c.textMuted, 11.sp, FontWeight.Bold), maxLines = 1)
                    Spacer(GlanceModifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(MoneyFormat.format(state.spent), style = widgetText(c.textPrimary, 26.sp), maxLines = 1)
                        Spacer(GlanceModifier.width(8.dp))
                        Text(widgetPlural(R.plurals.widget_spent_entries, state.entries, state.entries), GlanceModifier.padding(bottom = 4.dp), style = widgetText(c.textSecondary, 12.sp), maxLines = 1)
                    }
                }
                Box(
                    GlanceModifier.size(52.dp).cornerRadius(18.dp).background(ColorProvider(c.accentFill)).clickable(quickAddAction(context, TransactionKind.EXPENSE)),
                    contentAlignment = Alignment.Center,
                ) { WidgetIcon(Icons.Rounded.Add, c.accentOn, 30.dp, widgetString(R.string.widget_add_expense)) }
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth()) {
                state.categories.forEach { CategoryTile(it, c) }
            }
        }
    }
}

@Composable
private fun RowScope.CategoryTile(category: Category, c: BtColors) {
    val context = LocalContext.current
    val hue = Color(category.color)
    Column(
        GlanceModifier.defaultWeight().clickable(quickAddAction(context, TransactionKind.EXPENSE, category.id)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            GlanceModifier.size(48.dp).cornerRadius(16.dp).background(ColorProvider(hue.copy(alpha = 0.18f))),
            contentAlignment = Alignment.Center,
        ) { WidgetIcon(BtIcons.of(category.icon), c.tileGlyph(hue), 24.dp) }
        Spacer(GlanceModifier.height(6.dp))
        Text(category.name, style = widgetText(c.textSecondary, 11.sp, FontWeight.Medium), maxLines = 1)
    }
}
