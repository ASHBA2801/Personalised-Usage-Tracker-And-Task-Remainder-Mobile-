package com.example.usagetracker.tracking

import android.content.Context
import androidx.core.content.edit
import com.example.usagetracker.service.ContentDetectionService

/** Tiny SharedPreferences wrapper: the master switch, the poll cursor and the seeded flag. */
class TrackerPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tracker", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_ENABLED, value) }
            ContentDetectionService.trackingEnabled = value
        }

    /** Start of the window the next poll should read; null if the tracker has never run. */
    var lastPolledTimestamp: Long?
        get() = if (prefs.contains(KEY_LAST_POLLED)) prefs.getLong(KEY_LAST_POLLED, 0L) else null
        set(value) = prefs.edit {
            if (value == null) remove(KEY_LAST_POLLED) else putLong(KEY_LAST_POLLED, value)
        }

    /** Set once the default category rules have been inserted, so user edits are never overwritten. */
    var defaultsSeeded: Boolean
        get() = prefs.getBoolean(KEY_DEFAULTS_SEEDED, false)
        set(value) = prefs.edit { putBoolean(KEY_DEFAULTS_SEEDED, value) }

    private companion object {
        const val KEY_DEFAULTS_SEEDED = "defaults_seeded"

        const val KEY_ENABLED = "enabled"
        const val KEY_LAST_POLLED = "last_polled"
    }
}
