package com.example.usagetracker.endofday

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.usagetracker.data.AppDatabase
import com.example.usagetracker.data.FocusTask
import com.example.usagetracker.notifications.Notifications
import java.time.LocalDate

/** Carries today's incomplete focus tasks over to tomorrow, notifies, then queues tomorrow's run. */
class EndOfDayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val now = LocalDate.now()
            val today = now.toString() // yyyy-MM-dd
            val tomorrow = now.plusDays(1).toString()
            val dao = AppDatabase.getInstance(applicationContext).focusTaskDao()
            val incompleteTasks = dao.getIncompleteForDate(today)
            // Copy, never move: the original stays incomplete on today's date so history is accurate.
            incompleteTasks.forEach { task ->
                if (dao.countCarriedOver(tomorrow, task.title, today) == 0) {
                    dao.insert(
                        FocusTask(
                            title = task.title,
                            date = tomorrow,
                            isCompleted = false,
                            carriedOverFromDate = today,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            Notifications.showEndOfDay(applicationContext, incompleteTasks.map { it.title })
        } catch (e: Exception) {
            Log.w(TAG, "End-of-day check failed", e)
        } finally {
            // Always queue the next day's check, even if this one failed, so the chain never breaks.
            EndOfDayScheduler.schedule(applicationContext)
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "EndOfDayCheck"
    }
}
