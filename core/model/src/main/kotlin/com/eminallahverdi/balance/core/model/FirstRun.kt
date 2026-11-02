package com.eminallahverdi.balance.core.model

private const val ANDROID_13 = 33

/**
 * Whether this is a fresh install. Installs from before onboarding have no flag saying it was done, but every one of them
 * was given a starter catalog, so having data is what tells them apart. A wrong answer here can only ever show onboarding
 * on an empty database.
 */
fun needsOnboarding(done: Boolean, hasData: Boolean): Boolean = !done && !hasData

/**
 * Whether to ask for Android's notification permission before Home. [remindersOn] can be true without the user ever
 * touching a switch, because the evening reminder starts on and a restore brings it back on. [asked] belongs to this
 * install, not to the user's data, so a backup can't carry it over.
 */
fun needsNotificationAsk(sdk: Int, remindersOn: Boolean, allowed: Boolean, asked: Boolean): Boolean =
    sdk >= ANDROID_13 && remindersOn && !allowed && !asked
