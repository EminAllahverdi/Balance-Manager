package com.eminallahverdi.balance.core.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The activity behind this context, looking through wrappers like the one that gives screens the app's language. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
