package com.example.usagetracker.tracking

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Starts and stops the polling job.
 *
 * This is NOT real-time. WorkManager's periodic jobs cannot run more often than every 15 minutes, so
 * the poll is a chain of one-time jobs: each run schedules the next one 5 minutes later. The OS may
 * still delay runs under Doze or battery restrictions. A poll also runs whenever the app is opened
 * ([pollNow]), so the screen is current when it is looked at. Timestamps are exact either way, because
 * they come from the raw event log, not from when we poll.
 */
object TrackerScheduler {
    private const val CHAIN_WORK = "usage_poll_chain"
    private const val NOW_WORK = "usage_poll_now"
    private const val LEGACY_PERIODIC_WORK = "usage_poll"
    const val KEY_CHAIN = "chain"
    const val POLL_INTERVAL_MINUTES = 5L

    fun enable(context: Context) {
        val prefs = TrackerPrefs(context)
        // Only track from the moment the user turned it on, not the device's whole event history.
        if (!prefs.enabled || prefs.lastPolledTimestamp == null) {
            prefs.lastPolledTimestamp = System.currentTimeMillis()
        }
        prefs.enabled = true
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_PERIODIC_WORK)
        scheduleNext(context, ExistingWorkPolicy.REPLACE)
    }

    /** Re-arms the chain after an app update or process restart, without touching a chain that is already queued. */
    fun ensureScheduled(context: Context) {
        if (TrackerPrefs(context).enabled) {
            WorkManager.getInstance(context).cancelUniqueWork(LEGACY_PERIODIC_WORK)
            scheduleNext(context, ExistingWorkPolicy.KEEP)
        }
    }

    /** Queues the next link of the chain, 5 minutes out. */
    private fun scheduleNext(context: Context, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<UsagePollWorker>()
            .setInitialDelay(POLL_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_CHAIN to true))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(CHAIN_WORK, policy, request)
    }

    /** Called by [UsagePollWorker] while it is still running; APPEND_OR_REPLACE lets it queue its successor. */
    internal fun scheduleNextFromWorker(context: Context) =
        scheduleNext(context, ExistingWorkPolicy.APPEND_OR_REPLACE)

    /** One immediate poll that does not extend the chain. */
    fun pollNow(context: Context) {
        if (!TrackerPrefs(context).enabled) return
        val request = OneTimeWorkRequestBuilder<UsagePollWorker>()
            .setInputData(workDataOf(KEY_CHAIN to false))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(CHAIN_WORK)
            cancelUniqueWork(NOW_WORK)
            cancelUniqueWork(LEGACY_PERIODIC_WORK)
        }
        TrackerPrefs(context).apply {
            enabled = false
            lastPolledTimestamp = null
        }
    }
}
