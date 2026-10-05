package com.example.usagetracker.tracking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.example.usagetracker.data.AppDatabase
import com.example.usagetracker.data.CategoryRepository
import com.example.usagetracker.data.MIN_SESSION_MILLIS
import com.example.usagetracker.data.UsageSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UsagePoller(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = TrackerPrefs(appContext)
    private val categories = CategoryRepository(appContext)
    private val dao = AppDatabase.getInstance(appContext).usageSessionDao()

    /** Reads events since the last poll and stores finished sessions. Returns the number inserted. */
    suspend fun poll(now: Long = System.currentTimeMillis()): Int = pollLock.withLock { pollLocked(now) }

    private suspend fun pollLocked(now: Long): Int {
        val since = prefs.lastPolledTimestamp ?: (now - DEFAULT_LOOKBACK_MS)
        val result = SessionPairer.pair(queryEvents(since, now))

        val resolver = categories.resolver()
        val appNames = HashMap<String, String>()
        val toInsert = ArrayList<UsageSession>()
        for (s in result.sessions) {
            if (s.endTime - s.startTime < MIN_SESSION_MILLIS) continue
            // The cursor may re-read a window (see below), so skip sessions already stored.
            if (dao.countByPackageAndStart(s.packageName, s.startTime) > 0) continue
            // The accessibility service writes finer-grained, content-tagged rows for this window;
            // inserting the coarse one too would double count the time.
            if (dao.countOverlapping(s.packageName, s.startTime, s.endTime) > 0) continue
            val appName = appNames.getOrPut(s.packageName) { resolveAppName(s.packageName) }
            toInsert += UsageSession(
                    packageName = s.packageName,
                    appName = appName,
                    category = resolver.resolve(s.packageName, null),
                    contentTag = null,
                    startTime = s.startTime,
                    endTime = s.endTime,
                )
        }
        // One write for the whole batch. The cursor below only moves once it has succeeded.
        if (toInsert.isNotEmpty()) dao.insertAll(toInsert)

        // An app still in the foreground has no end time yet. Rewind the cursor to its start so the
        // next poll sees the whole session; otherwise it would be lost. Give up on sessions that
        // have been "open" implausibly long (missed background event, reboot) so we don't re-read forever.
        val openSince = result.openSince?.takeIf { now - it < MAX_OPEN_CARRY_MS }
        prefs.lastPolledTimestamp = openSince ?: now
        return toInsert.size
    }

    private fun queryEvents(from: Long, to: Long): List<RawEvent> {
        val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(from, to)
        val out = ArrayList<RawEvent>()
        val event = UsageEvents.Event() // reused by getNextEvent
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
            ) {
                out += RawEvent(event.packageName, event.eventType, event.timeStamp)
            }
        }
        return out
    }

    private fun resolveAppName(packageName: String): String = try {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName // uninstalled since the session
    }

    private companion object {
        // The scheduled chain and the on-open poll must not read the same window at once (double inserts).
        val pollLock = Mutex()
        const val DEFAULT_LOOKBACK_MS = 15 * 60 * 1000L
        const val MAX_OPEN_CARRY_MS = 6 * 60 * 60 * 1000L
    }
}
