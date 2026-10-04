package com.example.usagetracker.tracking

import android.app.usage.UsageEvents

data class RawEvent(val packageName: String, val type: Int, val timestamp: Long)

data class PairedSession(val packageName: String, val startTime: Long, var endTime: Long)

data class PairingResult(
    val sessions: List<PairedSession>,
    /** Start of the earliest foreground event with no matching background yet, if any. */
    val openSince: Long?,
)

/**
 * Turns a time-ordered stream of foreground/background events into closed sessions.
 * Pure function so it can be unit tested without a device.
 */
object SessionPairer {
    /**
     * Android emits per-activity events: moving between two activities of one app produces
     * BACKGROUND(a1) then FOREGROUND(a2) a few ms apart. Sessions of the same package separated by
     * less than this are merged so in-app navigation doesn't shatter one session into many.
     */
    const val MERGE_GAP_MS = 3_000L

    fun pair(events: List<RawEvent>): PairingResult {
        val open = LinkedHashMap<String, Long>() // package -> foreground start
        val merged = ArrayList<PairedSession>()
        val lastByPackage = HashMap<String, PairedSession>()

        for (e in events) {
            when (e.type) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> open.putIfAbsent(e.packageName, e.timestamp)
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    // A background with no foreground in this window started before it; skip it.
                    val start = open.remove(e.packageName) ?: continue
                    if (e.timestamp <= start) continue
                    val prev = lastByPackage[e.packageName]
                    if (prev != null && start - prev.endTime <= MERGE_GAP_MS) {
                        prev.endTime = e.timestamp
                    } else {
                        PairedSession(e.packageName, start, e.timestamp).also {
                            merged += it
                            lastByPackage[e.packageName] = it
                        }
                    }
                }
            }
        }
        return PairingResult(merged.sortedBy { it.startTime }, open.values.minOrNull())
    }
}
