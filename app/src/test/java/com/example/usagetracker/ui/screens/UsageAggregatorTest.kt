package com.example.usagetracker.ui.screens

import com.example.usagetracker.data.AppTotal
import com.example.usagetracker.data.CategoryTotal
import com.example.usagetracker.data.ContentTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageAggregatorTest {
    private val yt = UsageReport.YOUTUBE
    private fun app(pkg: String, ms: Long) = AppTotal(pkg, pkg.uppercase(), ms)

    @Test fun keepsDaoOrderOfApps() {
        val r = UsageAggregator.build(listOf(app("b", 4000), app("a", 2000)), listOf(CategoryTotal("UNCATEGORIZED", 6000)), emptyList())
        assertEquals(listOf("b", "a"), r.apps.map { it.packageName })
        assertEquals(6000L, r.totalMs)
        assertEquals(6000L, r.uncategorizedMs)
    }

    @Test fun emptyInputIsEmptyReport() {
        assertEquals(true, UsageAggregator.build(emptyList(), emptyList(), emptyList()).isEmpty)
    }

    @Test fun splitsYouTubeByTagWithUntaggedAsOther() {
        val r = UsageAggregator.build(
            listOf(app(yt, 5000)),
            listOf(CategoryTotal("LOW_VALUE", 5000)),
            listOf(ContentTotal("shorts", 3000), ContentTotal("video", 1000), ContentTotal("other", 500), ContentTotal(null, 500)),
        )
        assertEquals(YouTubeSplit(3000, 1000, 1000), r.apps.single().youTube)
    }

    @Test fun nonYouTubeHasNoSplit() {
        assertNull(UsageAggregator.build(listOf(app("a", 1000)), emptyList(), emptyList()).apps.single().youTube)
    }

    @Test fun bucketsByCategoryWithUnknownAsUncategorized() {
        val r = UsageAggregator.build(
            emptyList(),
            listOf(CategoryTotal("USEFUL", 1000), CategoryTotal("LOW_VALUE", 2000), CategoryTotal("UNCATEGORIZED", 3000), CategoryTotal("WEIRD", 1000)),
            emptyList(),
        )
        assertEquals(listOf(1000L, 2000L, 4000L), listOf(r.usefulMs, r.lowValueMs, r.uncategorizedMs))
        assertEquals(7000L, r.totalMs)
    }

    @Test fun formatsDurations() {
        assertEquals("0m", formatDuration(0))
        assertEquals("<1m", formatDuration(30_000))
        assertEquals("14m", formatDuration(14 * 60_000L))
        assertEquals("2h 05m", formatDuration(125 * 60_000L))
    }
}
