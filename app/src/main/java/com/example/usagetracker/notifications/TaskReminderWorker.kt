package com.example.usagetracker.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.usagetracker.data.AppDatabase
import java.time.LocalDate

/** Posts a reminder listing today's incomplete focus tasks; stays silent when there are none. */
class TaskReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val today = LocalDate.now().toString() // yyyy-MM-dd
        val incomplete = AppDatabase.getInstance(applicationContext).focusTaskDao().getIncompleteForDate(today)
        if (incomplete.isNotEmpty()) {
            Notifications.showIncompleteTasks(applicationContext, incomplete.map { it.title })
        }
        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "Task reminder failed", e)
        Result.retry()
    }

    private companion object {
        const val TAG = "TaskReminder"
    }
}
