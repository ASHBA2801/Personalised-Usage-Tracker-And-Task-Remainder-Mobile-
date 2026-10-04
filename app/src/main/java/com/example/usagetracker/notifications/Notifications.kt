package com.example.usagetracker.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.usagetracker.MainActivity
import com.example.usagetracker.R

object Notifications {
    const val CHANNEL_FOCUS_TASKS = "focus_tasks"

    /** Intent extra that tells MainActivity to open the end-of-day screen. */
    const val EXTRA_SHOW_END_OF_DAY = "show_end_of_day"

    private const val INCOMPLETE_TASKS_NOTIFICATION_ID = 1
    private const val END_OF_DAY_NOTIFICATION_ID = 2

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

    /** Blunt end-of-day notification; tapping it opens the end-of-day screen. No-op when nothing is incomplete. */
    fun showEndOfDay(context: Context, titles: List<String>) {
        if (titles.isEmpty() || !hasPermission(context)) return
        val title = if (titles.size == 1) "You didn't finish 1 task today" else "You didn't finish ${titles.size} tasks today"
        val tapIntent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SHOW_END_OF_DAY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context, END_OF_DAY_NOTIFICATION_ID, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_FOCUS_TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(titles.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(titles.joinToString("\n")))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(END_OF_DAY_NOTIFICATION_ID, notification)
    }
}
