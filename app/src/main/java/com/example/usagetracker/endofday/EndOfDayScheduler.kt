package com.example.usagetracker.endofday

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.usagetracker.settings.EndOfDayPrefs
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object EndOfDayScheduler {
    private const val WORK_NAME = "end_of_day_check"

    /**
     * Millis from now until the next occurrence of the configured end-of-day time;
     * tomorrow's if today's has already passed.
     */
    fun calculateDelayUntilEndOfDay(context: Context, now: ZonedDateTime = ZonedDateTime.now()): Long {
        val prefs = EndOfDayPrefs(context)
        var target = now.withHour(prefs.endOfDayHour).withMinute(prefs.endOfDayMinute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis()
    }

    // Doze note: when the phone is idle Android may delay this notification by a few minutes. That is
    // fine here; exact alarms or a foreground service would cost far more battery, so we don't use them.
    /** Queues the next check, replacing any pending one. Call on app start, after a time change, and from the worker. */
    fun schedule(context: Context) {
        // No constraints on purpose: the end-of-day notice should still arrive when the battery is low.
        val request = OneTimeWorkRequestBuilder<EndOfDayWorker>()
            .setInitialDelay(calculateDelayUntilEndOfDay(context), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
