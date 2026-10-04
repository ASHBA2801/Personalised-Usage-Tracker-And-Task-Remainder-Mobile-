package com.example.usagetracker.settings

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

/** The time of day the scold/carry-over check runs, stored as hour (0-23) and minute (0-59). */
class EndOfDayPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val endOfDayHour: Int get() = prefs.getInt(KEY_HOUR, DEFAULT_HOUR)
    val endOfDayMinute: Int get() = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)

    fun setEndOfDay(hour: Int, minute: Int) = prefs.edit {
        putInt(KEY_HOUR, hour)
        putInt(KEY_MINUTE, minute)
    }

    companion object {
        const val DEFAULT_HOUR = 23
        const val DEFAULT_MINUTE = 0
        private const val KEY_HOUR = "end_of_day_hour"
        private const val KEY_MINUTE = "end_of_day_minute"

        /** e.g. 23:00 -> "11:00 PM". */
        fun format(hour: Int, minute: Int): String {
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            val suffix = if (hour < 12) "AM" else "PM"
            return String.format(Locale.getDefault(), "%d:%02d %s", h12, minute, suffix)
        }
    }
}
