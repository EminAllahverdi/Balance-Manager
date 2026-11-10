package com.eminallahverdi.balance.core.common

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's language: the phone's, or one picked in Settings. [tag] is null for the phone's. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    AZERBAIJANI("az"),
}

/**
 * Which language the app speaks. English unless the phone is in Azerbaijani, and English for anything not translated, which
 * is how Android picks resources anyway. A language picked in Settings overrides the phone's.
 *
 * It's kept in a small file of its own, read at once when the app starts, before anything draws: a language that arrived a
 * moment later would show a flash of the other one. It belongs to this install, so a backup doesn't carry it.
 */
object Language {
    private const val FILE = "app_language"
    private const val KEY = "language"

    private val chosen = MutableStateFlow(AppLanguage.SYSTEM)

    /** The language picked, which open screens follow as it changes. */
    val flow: StateFlow<AppLanguage> = chosen.asStateFlow()

    /** The phone's own language, as it was before the app set one of its own. */
    private val phone: Locale get() = runCatching { Resources.getSystem().configuration.locales[0] }.getOrNull() ?: Locale.getDefault()

    /** What texts and dates use: the one picked, or the phone's. */
    val locale: Locale
        get() = when (chosen.value) {
            AppLanguage.SYSTEM -> phone
            AppLanguage.ENGLISH -> Locale.ENGLISH
            AppLanguage.AZERBAIJANI -> AZERBAIJANI_LOCALE
        }

    /** Whether texts are Azerbaijani now, for the few written in code rather than resources, like Today in a date. */
    val isAzerbaijani: Boolean get() = locale.language == AZERBAIJANI_LOCALE.language

    /** Reads what was picked. Called first thing, from the application's base context. */
    fun load(context: Context) {
        val saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        chosen.value = AppLanguage.entries.firstOrNull { it.name == saved } ?: AppLanguage.SYSTEM
        Locale.setDefault(locale)
    }

    /** Picks a language. Open screens change at once, without starting again, and so do widgets and notifications from now on. */
    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, language.name).apply()
        chosen.value = language
        Locale.setDefault(locale)
        // The application's own resources, which widgets, notifications and view models read texts from.
        val app = context.applicationContext.resources
        @Suppress("DEPRECATION")
        app.updateConfiguration(configured(app.configuration), app.displayMetrics)
    }

    /** [base] speaking the language picked, for an application's or activity's base context. */
    fun wrap(base: Context): Context = base.createConfigurationContext(configured(base.resources.configuration))

    /** [configuration] with the language picked. */
    fun configured(configuration: Configuration): Configuration {
        // Read outside apply, where `locale` would be the configuration's own, the language it already had.
        val picked = locale
        return Configuration(configuration).apply { setLocales(LocaleList(picked)) }
    }

    private val AZERBAIJANI_LOCALE: Locale = Locale.forLanguageTag("az")
}
