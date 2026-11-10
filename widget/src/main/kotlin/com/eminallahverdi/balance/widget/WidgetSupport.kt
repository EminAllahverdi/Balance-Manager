package com.eminallahverdi.balance.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.eminallahverdi.balance.core.common.ApplicationScope
import com.eminallahverdi.balance.core.common.Language
import com.eminallahverdi.balance.core.data.BankAlertRepository
import com.eminallahverdi.balance.core.data.CatalogRepository
import com.eminallahverdi.balance.core.data.EXTRA_OPEN_CONFIRM
import com.eminallahverdi.balance.core.data.TransactionRepository
import com.eminallahverdi.balance.core.datastore.ThemePreference
import com.eminallahverdi.balance.core.datastore.UserPreferences
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.designsystem.theme.BtColors
import com.eminallahverdi.balance.core.designsystem.theme.DarkBtColors
import com.eminallahverdi.balance.core.designsystem.theme.LightBtColors
import com.eminallahverdi.balance.core.model.TransactionKind
import com.eminallahverdi.balance.feature.transaction.QuickAddViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope

// The activities live in :app, which this module can't see, so they're reached by name.
private const val MAIN_ACTIVITY = "com.eminallahverdi.balance.MainActivity"
private const val QUICK_ADD_ACTIVITY = "com.eminallahverdi.balance.QuickAddActivity"
private const val VOICE_ACTIVITY = "com.eminallahverdi.balance.VoiceEntryActivity"

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun transactions(): TransactionRepository
    fun bankAlerts(): BankAlertRepository
    fun catalog(): CatalogRepository
    fun preferences(): UserPreferencesRepository

    @ApplicationScope fun scope(): CoroutineScope
}

/** Widgets and their receivers are created by the launcher, outside Hilt. */
internal val Context.widgetData: WidgetEntryPoint
    get() = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

internal fun UserPreferences.colors(context: Context): BtColors {
    val dark = when (theme) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    return if (dark) DarkBtColors else LightBtColors
}

/** Quick add over the home screen, without opening the full app. */
internal fun quickAddAction(context: Context, kind: TransactionKind, categoryId: Long? = null): Action {
    val intent = Intent().setClassName(context.packageName, QUICK_ADD_ACTIVITY).putExtra(QuickAddViewModel.ARG_KIND, kind.name)
    if (categoryId != null) intent.putExtra(QuickAddViewModel.ARG_CATEGORY, categoryId)
    return actionStartActivity(intent)
}

/** Listening, then quick add filled in with what was said. */
internal fun voiceAction(context: Context): Action = actionStartActivity(Intent().setClassName(context.packageName, VOICE_ACTIVITY))

/** Brings the running app forward instead of stacking a second copy on top of it. */
internal fun openAppAction(context: Context): Action = actionStartActivity(
    Intent().setClassName(context.packageName, MAIN_ACTIVITY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
)

/** The app, on To confirm. */
internal fun openConfirmAction(context: Context): Action = actionStartActivity(
    Intent().setClassName(context.packageName, MAIN_ACTIVITY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_OPEN_CONFIRM, true),
)

/** radius-lg and a translucent bg-surface, inside the design's hairline. */
@Composable
internal fun WidgetSurface(colors: BtColors, onClick: Action? = null, content: @Composable () -> Unit) {
    Box(GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(ColorProvider(colors.textPrimary.copy(alpha = 0.07f))).padding(1.dp)) {
        val surface = GlanceModifier.fillMaxSize().cornerRadius(27.dp).background(ColorProvider(colors.bgSurface.copy(alpha = 0.88f)))
        Box(if (onClick != null) surface.clickable(onClick) else surface) { content() }
    }
}

@Composable
internal fun WidgetIcon(icon: ImageVector, tint: Color, size: Dp, description: String? = null) {
    val context = LocalContext.current
    val bitmap = remember(icon, tint, size) { icon.toBitmap(context, size, tint) }
    Image(ImageProvider(bitmap), description, GlanceModifier.size(size))
}

internal fun widgetText(color: Color, size: TextUnit, weight: FontWeight = FontWeight.Medium) =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = weight)

/** Glance can't draw an [ImageVector], so the app's icons reach widgets as small bitmaps. */
internal fun ImageVector.toBitmap(context: Context, size: Dp, tint: Color): Bitmap {
    val px = (size.value * context.resources.displayMetrics.density).roundToInt()
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap).apply { scale(px / viewportWidth, px / viewportHeight) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint.toArgb() }
    fun draw(group: VectorGroup) {
        for (node in group) {
            when (node) {
                is VectorGroup -> draw(node)
                is VectorPath -> canvas.drawPath(node.pathData.toPath().apply { fillType = node.pathFillType }.asAndroidPath(), paint)
            }
        }
    }
    draw(root)
    return bitmap
}

/**
 * A text in the language picked in Settings. A widget draws with whatever context the launcher's broadcast brought, which
 * may still speak the language the process started in, so the texts come from one set to the picked language.
 */
@Composable
internal fun widgetString(@StringRes id: Int, vararg args: Any): String = widgetTexts().getString(id, *args)

@Composable
internal fun widgetPlural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
    widgetTexts().resources.getQuantityString(id, count, *args)

@Composable
private fun widgetTexts(): Context {
    val context = LocalContext.current
    val locale = Language.locale
    return remember(context, locale) { Language.wrap(context) }
}
