package com.example.usagetracker.tracking

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.usagetracker.permissions.PermissionChecker

class UsagePollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Permission can be revoked at any time from system settings. Stop polling entirely
        // rather than running a job that can only fail; the user re-enables it from Settings.
        if (!PermissionChecker.hasUsageAccess(applicationContext)) {
            TrackerScheduler.disable(applicationContext)
            return Result.success()
        }
        return try {
            val inserted = UsagePoller(applicationContext).poll()
            Log.d(TAG, "Poll finished, $inserted new session(s)")
            if (inputData.getBoolean(TrackerScheduler.KEY_CHAIN, false)) {
                TrackerScheduler.scheduleNextFromWorker(applicationContext)
            }
            Result.success()
        } catch (e: SecurityException) {
            TrackerScheduler.disable(applicationContext)
            Result.success()
        } catch (e: Exception) {
            // Cursor is only advanced after a successful poll, so a retry re-reads the same window.
            Log.w(TAG, "Poll failed", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "UsagePollWorker"
    }
}
