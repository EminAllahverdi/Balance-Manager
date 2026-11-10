package com.eminallahverdi.balance.core.common

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * A text a view model or a list hands its screen, kept as a resource, so it's read in the language showing when it's drawn
 * rather than the one it was decided in. [count] makes [id] a plural. [raw] is a text that needs no translating, like a
 * name the user typed, so names and resources can sit in one list.
 */
data class UiText(val id: Int, val args: List<Any> = emptyList(), val count: Int? = null, val raw: String? = null)

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText(id, args.toList())

fun pluralText(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = UiText(id, args.toList(), count)

fun rawText(text: String): UiText = UiText(0, raw = text)
