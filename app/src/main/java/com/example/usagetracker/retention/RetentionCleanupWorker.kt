package com.example.usagetracker.retention

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.usagetracker.data.AppDatabase
import java.time.ZoneId
import java.time.ZonedDateTime

/** Deletes usage sessions past the retention window. Runs regardless of the tracking toggle. */
class RetentionCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val cutoff = cutoffMillis()
        val deleted = AppDatabase.getInstance(applicationContext).usageSessionDao().deleteOlderThan(cutoff)
        RetentionPrefs(applicationContext).recordRun(System.currentTimeMillis(), deleted)
        Log.d(TAG, "Retention cleanup deleted $deleted session(s) ended before $cutoff")
        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "Retention cleanup failed", e)
        Result.retry()
    }

    companion object {
        const val RETENTION_MONTHS = 3L
        private const val TAG = "RetentionCleanup"

        /** Calendar-based "3 months ago" in the device zone, as epoch millis. */
        fun cutoffMillis(now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): Long =
            now.minusMonths(RETENTION_MONTHS).toInstant().toEpochMilli()
    }
}
