package com.example.usagetracker.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.usagetracker.R

object Notifications {
    const val CHANNEL_FOCUS_TASKS = "focus_tasks"

    private const val INCOMPLETE_TASKS_NOTIFICATION_ID = 1

    /** Idempotent; safe to call on every launch. */
    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_FOCUS_TASKS,
            "Focus Task Reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Always true below Android 13, where the permission doesn't exist. */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Posts (or replaces) the single incomplete-tasks reminder. No-op without notification permission. */
    fun showIncompleteTasks(context: Context, titles: List<String>) {
        if (titles.isEmpty() || !hasPermission(context)) return
        val title = if (titles.size == 1) "1 task still incomplete" else "${titles.size} tasks still incomplete"
        val body = titles.joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL_FOCUS_TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(titles.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(INCOMPLETE_TASKS_NOTIFICATION_ID, notification)
    }
}
