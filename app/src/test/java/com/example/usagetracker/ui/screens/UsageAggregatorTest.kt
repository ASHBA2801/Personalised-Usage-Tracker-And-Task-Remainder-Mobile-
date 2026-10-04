package com.example.usagetracker.ui.screens

import com.example.usagetracker.data.UsageSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageAggregatorTest {
    private val yt = UsageReport.YOUTUBE
    private fun s(pkg: String, start: Long, end: Long, tag: String? = null, cat: String = "UNCATEGORIZED") =
        UsageSession(packageName = pkg, appName = pkg.uppercase(), category = cat, contentTag = tag, startTime = start, endTime = end)

    @Test fun groupsByPackageAndSortsDescending() {
        val r = UsageAggregator.aggregate(listOf(s("a", 0, 1000), s("b", 1000, 5000), s("a", 5000, 6000)), 0, 10_000)
        assertEquals(listOf("b", "a"), r.apps.map { it.packageName })
        assertEquals(listOf(4000L, 2000L), r.apps.map { it.totalMs })
        assertEquals(6000L, r.totalMs)
        assertEquals(6000L, r.uncategorizedMs)
    }

    @Test fun clipsSessionsToWindow() {
        val r = UsageAggregator.aggregate(listOf(s("a", -2000, 1000), s("a", 9000, 12_000)), 0, 10_000)
        assertEquals(2000L, r.totalMs)
    }

    @Test fun ignoresZeroLengthAndOutOfWindowRows() {
        val r = UsageAggregator.aggregate(listOf(s("a", 500, 500), s("b", 20_000, 30_000)), 0, 10_000)
        assertEquals(true, r.isEmpty)
    }

    @Test fun splitsYouTubeByTagWithUntaggedAsOther() {
        val r = UsageAggregator.aggregate(
            listOf(s(yt, 0, 3000, "shorts"), s(yt, 3000, 4000, "video"), s(yt, 4000, 4500, "other"), s(yt, 4500, 5000)),
            0, 10_000,
        )
        assertEquals(YouTubeSplit(3000, 1000, 1000), r.apps.single().youTube)
    }

    @Test fun nonYouTubeHasNoSplit() {
        assertNull(UsageAggregator.aggregate(listOf(s("a", 0, 1000)), 0, 10_000).apps.single().youTube)
    }

    @Test fun bucketsByCategory() {
        val r = UsageAggregator.aggregate(
            listOf(s("a", 0, 1000, cat = "USEFUL"), s("b", 0, 2000, cat = "LOW_VALUE"), s("c", 0, 4000)), 0, 10_000,
        )
        assertEquals(listOf(1000L, 2000L, 4000L), listOf(r.usefulMs, r.lowValueMs, r.uncategorizedMs))
    }

    @Test fun formatsDurations() {
        assertEquals("0m", formatDuration(0))
        assertEquals("<1m", formatDuration(30_000))
        assertEquals("14m", formatDuration(14 * 60_000L))
        assertEquals("2h 05m", formatDuration(125 * 60_000L))
    }
}
