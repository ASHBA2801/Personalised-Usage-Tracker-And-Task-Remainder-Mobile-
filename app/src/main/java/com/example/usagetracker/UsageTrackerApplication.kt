package com.example.usagetracker

import android.app.Application
import com.example.usagetracker.notifications.Notifications
import com.example.usagetracker.notifications.TaskReminderScheduler
import com.example.usagetracker.retention.RetentionScheduler

class UsageTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        RetentionScheduler.schedule(this)
        TaskReminderScheduler.schedule(this)
    }
}
