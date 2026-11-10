package com.eminallahverdi.balance

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.eminallahverdi.balance.core.common.Language

/**
 * [content] in the language picked in Settings, changing the moment it's picked. Nothing starts again, which is what
 * keeps a change of language from flashing the screen: the texts under it are read again in the new language, and the
 * screen, its scroll and what's open stay as they were.
 */
@Composable
fun LocalizedContent(content: @Composable () -> Unit) {
    val language by Language.flow.collectAsState()
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(language, configuration) { Language.configured(configuration) }
    // Only the resources change: everything else is the activity's own, so starting a screen or a picker still works.
    val context = remember(base, localized) { LocalizedContext(base, base.createConfigurationContext(localized).resources) }
    CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides localized) { content() }
}

private class LocalizedContext(base: Context, private val localized: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localized
}
