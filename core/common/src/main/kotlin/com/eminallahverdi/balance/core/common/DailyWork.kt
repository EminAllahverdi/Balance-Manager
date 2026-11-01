package com.eminallahverdi.balance.core.common

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.eminallahverdi.balance.core.model.nextReminderAt
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Books [W] for the next [minuteOfDay]. Each run calls this again, with UPDATE, because a plain daily period slips later
 * by however late the last run was. The app starting uses KEEP: what's booked may be today's, running late under Doze.
 * Waits until it's booked: a worker that ends first leaves its next run queued with nothing to start it.
 */
suspend inline fun <reified W : ListenableWorker> Context.bookDailyWork(name: String, minuteOfDay: Int, policy: ExistingPeriodicWorkPolicy) {
    val next = nextReminderAt(LocalDateTime.now(), minuteOfDay).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val request = PeriodicWorkRequestBuilder<W>(Duration.ofDays(1)).setNextScheduleTimeOverride(next).build()
    WorkManager.getInstance(this).enqueueUniquePeriodicWork(name, policy, request).await()
}
