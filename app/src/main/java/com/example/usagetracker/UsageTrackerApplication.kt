package com.example.usagetracker

import android.app.Application
import com.example.usagetracker.endofday.EndOfDayScheduler
import com.example.usagetracker.notifications.Notifications
import com.example.usagetracker.notifications.TaskReminderScheduler
import com.example.usagetracker.retention.RetentionScheduler
import com.example.usagetracker.tracking.TrackerScheduler

class UsageTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        RetentionScheduler.schedule(this)
        TaskReminderScheduler.schedule(this)
        EndOfDayScheduler.schedule(this)
        TrackerScheduler.ensureScheduled(this)
    }
}
