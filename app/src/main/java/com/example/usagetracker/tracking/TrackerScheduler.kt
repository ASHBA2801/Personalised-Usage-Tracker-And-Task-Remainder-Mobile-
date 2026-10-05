package com.example.usagetracker.tracking

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Starts and stops the polling job.
 *
 * This is NOT real-time. The poll runs every 30 minutes (WorkManager's minimum is 15), and the OS may delay
 * runs further under Doze or battery restrictions. Without a foreground service, that's the tightest
 * polling Android reliably allows, so sessions show up in the database up to ~30+ minutes after they
 * end. The timestamps themselves are exact, because they come from the raw event log, not from
 * when we poll.
 */
object TrackerScheduler {
    private const val WORK_NAME = "usage_poll"

    fun enable(context: Context) {
        val prefs = TrackerPrefs(context)
        // Only track from the moment the user turned it on, not the device's whole event history.
        if (!prefs.enabled || prefs.lastPolledTimestamp == null) {
            prefs.lastPolledTimestamp = System.currentTimeMillis()
        }
        prefs.enabled = true
        // Every 30 min is plenty: lastPolledTimestamp makes each run catch up on everything it missed.
        val request = PeriodicWorkRequestBuilder<UsagePollWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request,
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        TrackerPrefs(context).apply {
            enabled = false
            lastPolledTimestamp = null
        }
    }
}
