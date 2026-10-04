package com.example.usagetracker.tracking

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Starts and stops the polling job.
 *
 * This is NOT real-time. WorkManager's minimum periodic interval is 15 minutes, and the OS may delay
 * runs further under Doze or battery restrictions. Without a foreground service, that's the tightest
 * polling Android reliably allows, so sessions show up in the database up to ~15+ minutes after they
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
        val request = PeriodicWorkRequestBuilder<UsagePollWorker>(15, TimeUnit.MINUTES).build()
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
