package com.example.usagetracker

import android.app.Application
import com.example.usagetracker.retention.RetentionScheduler

class UsageTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RetentionScheduler.schedule(this)
    }
}
