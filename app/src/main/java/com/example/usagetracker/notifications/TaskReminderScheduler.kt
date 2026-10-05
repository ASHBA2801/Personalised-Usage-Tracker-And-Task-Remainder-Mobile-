package com.example.usagetracker.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object TaskReminderScheduler {
    private const val WORK_NAME = "task_reminder"

    // Doze note: when the phone is idle Android may delay this notification by a few minutes. That is
    // fine here; exact alarms or a foreground service would cost far more battery, so we don't use them.
    /** Safe to call on every launch: KEEP leaves an already-scheduled job (and its timing) alone. */
    fun schedule(context: Context) {
        // No constraints on purpose: reminders should still arrive when the battery is low.
        val request = PeriodicWorkRequestBuilder<TaskReminderWorker>(4, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }
}
