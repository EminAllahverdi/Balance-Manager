package com.eminallahverdi.balance.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.eminallahverdi.balance.core.common.UiText

/** [UiText] in the language showing now. */
@Composable
fun UiText.text(): String {
    raw?.let { return it }
    // An argument can be a text of its own, like "week of 26 Oct" inside "−₼96.00 vs week of 26 Oct".
    val values = args.map { if (it is UiText) it.text() else it }.toTypedArray()
    val count = count
    return if (count != null) pluralStringResource(id, count, *values) else stringResource(id, *values)
}
