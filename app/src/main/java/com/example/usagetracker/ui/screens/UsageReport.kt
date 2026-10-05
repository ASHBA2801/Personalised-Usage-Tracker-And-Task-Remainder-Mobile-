package com.example.usagetracker.ui.screens

import com.example.usagetracker.data.AppTotal
import com.example.usagetracker.data.CategoryTotal
import com.example.usagetracker.data.ContentTotal
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

/** Turns the DAO's small per-app / per-category / per-tag totals into the screen's [UsageReport]. */
object UsageAggregator {
    fun build(apps: List<AppTotal>, categories: List<CategoryTotal>, youTubeTags: List<ContentTotal>): UsageReport {
        val useful = categories.filter { it.category == "USEFUL" }.sumOf { it.totalMillis }
        val lowValue = categories.filter { it.category == "LOW_VALUE" }.sumOf { it.totalMillis }
        val total = categories.sumOf { it.totalMillis }

        val usage = apps.filter { it.totalMillis > 0 }.map { a ->
            val youTube = if (a.packageName == UsageReport.YOUTUBE) {
                fun tagMs(tag: ContentTag) = youTubeTags.filter { it.contentTag == tag.value }.sumOf { it.totalMillis }
                val shorts = tagMs(ContentTag.SHORTS)
                val video = tagMs(ContentTag.VIDEO)
                // Untagged rows (coarse poller sessions) fall into Other so the three sum to the total.
                YouTubeSplit(shorts, video, a.totalMillis - shorts - video)
            } else null
            AppUsage(a.packageName, a.appName, a.totalMillis, youTube)
        }.sortedByDescending { it.totalMs }

        return UsageReport(total, useful, lowValue, total - useful - lowValue, usage)
    }
}
