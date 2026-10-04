package com.example.usagetracker.ui.screens

import com.example.usagetracker.data.UsageSession
import com.example.usagetracker.service.ContentTag

data class YouTubeSplit(val shortsMs: Long, val videoMs: Long, val otherMs: Long)

data class AppUsage(
    val packageName: String,
    val appName: String,
    val totalMs: Long,
    /** Non-null only for YouTube, the one app with content-level data. */
    val youTube: YouTubeSplit? = null,
)

data class UsageReport(
    val totalMs: Long,
    val usefulMs: Long,
    val lowValueMs: Long,
    val uncategorizedMs: Long,
    val apps: List<AppUsage>,
) {
    val isEmpty get() = apps.isEmpty()

    companion object {
        val EMPTY = UsageReport(0, 0, 0, 0, emptyList())
        const val YOUTUBE = "com.google.android.youtube"
    }
}

/** Pure aggregation, kept out of the ViewModel so it can be unit tested. */
object UsageAggregator {
    /** Sums [sessions] clipped to [windowStart, windowEnd), grouped by package (and tag for YouTube). */
    fun aggregate(sessions: List<UsageSession>, windowStart: Long, windowEnd: Long): UsageReport {
        val byPackage = HashMap<String, MutableList<Pair<UsageSession, Long>>>()
        var useful = 0L
        var lowValue = 0L
        var uncategorized = 0L

        for (s in sessions) {
            val ms = minOf(s.endTime, windowEnd) - maxOf(s.startTime, windowStart)
            if (ms <= 0) continue
            byPackage.getOrPut(s.packageName) { ArrayList() } += s to ms
            when (s.category) {
                "USEFUL" -> useful += ms
                "LOW_VALUE" -> lowValue += ms
                else -> uncategorized += ms
            }
        }

        val apps = byPackage.map { (pkg, rows) ->
            val youTube = if (pkg == UsageReport.YOUTUBE) {
                fun tagMs(tag: ContentTag) = rows.filter { it.first.contentTag == tag.value }.sumOf { it.second }
                val shorts = tagMs(ContentTag.SHORTS)
                val video = tagMs(ContentTag.VIDEO)
                val total = rows.sumOf { it.second }
                // Untagged rows (coarse poller sessions) fall into Other so the three sum to the total.
                YouTubeSplit(shorts, video, total - shorts - video)
            } else null
            AppUsage(
                packageName = pkg,
                // Rows are start-ordered, so the last name is the most recent label.
                appName = rows.last().first.appName,
                totalMs = rows.sumOf { it.second },
                youTube = youTube,
            )
        }.sortedByDescending { it.totalMs }

        return UsageReport(useful + lowValue + uncategorized, useful, lowValue, uncategorized, apps)
    }
}
