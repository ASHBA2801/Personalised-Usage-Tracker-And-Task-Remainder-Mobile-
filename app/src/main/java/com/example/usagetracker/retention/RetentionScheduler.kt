package com.example.usagetracker.retention

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RetentionScheduler {
    private const val WORK_NAME = "retention_cleanup"

    /** Safe to call on every launch: KEEP leaves an already-scheduled job (and its timing) alone. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RetentionCleanupWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }
}
