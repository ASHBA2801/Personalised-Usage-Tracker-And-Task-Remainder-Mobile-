package com.example.usagetracker.retention

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RetentionScheduler {
    private const val WORK_NAME = "retention_cleanup"

    /** Safe to call on every launch: UPDATE applies new settings without resetting the job's timing. */
    fun schedule(context: Context) {
        // Runs once a day, so it can wait for the phone to be idle and the battery not low.
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()
        val request = PeriodicWorkRequestBuilder<RetentionCleanupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request,
        )
    }
}
