package com.example.usagetracker.retention

import android.content.Context
import androidx.core.content.edit

/** When the cleanup last ran and how many rows it removed, for verifying it fires. */
class RetentionPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("retention", Context.MODE_PRIVATE)

    val lastRunTimestamp: Long? get() = if (prefs.contains(KEY_LAST_RUN)) prefs.getLong(KEY_LAST_RUN, 0L) else null
    val lastDeletedCount: Int get() = prefs.getInt(KEY_LAST_DELETED, 0)

    fun recordRun(timestamp: Long, deleted: Int) = prefs.edit {
        putLong(KEY_LAST_RUN, timestamp)
        putInt(KEY_LAST_DELETED, deleted)
    }

    private companion object {
        const val KEY_LAST_RUN = "last_run"
        const val KEY_LAST_DELETED = "last_deleted"
    }
}
