package com.example.usagetracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentDetectionTest {
    private fun snap(
        ids: Set<String> = emptySet(),
        seek: Boolean = false,
        scrollers: List<Scroller> = emptyList(),
    ) = ScreenSnapshot(1080, 2400, ids, seek, scrollers)

    @Test fun reelIdMeansShorts() {
        val c = ContentClassifier.classify(snap(ids = setOf("reel_recycler", "pivot_bar")))
        assertEquals(ContentTag.SHORTS, c.tag)
        assertEquals("id:reel_recycler", c.signal)
    }

    @Test fun shortsTabInNavBarIsNotShorts() {
        assertEquals(ContentTag.OTHER, ContentClassifier.classify(snap(ids = setOf("pivot_shorts_tab"))).tag)
    }

    @Test fun watchIdsMeanVideo() {
        assertEquals(ContentTag.VIDEO, ContentClassifier.classify(snap(ids = setOf("watch_player", "reel_time_bar"))).tag)
    }

    @Test fun staleReelTimeBarAndAlwaysPresentIdsAreOther() {
        val ids = setOf("reel_time_bar", "watch_while_layout_coordinator_layout", "results", "pivot_bar", "player_view")
        assertEquals(ContentTag.OTHER, ContentClassifier.classify(snap(ids = ids)).tag)
    }

    @Test fun miniplayerIsNotVideo() {
        assertEquals(ContentTag.OTHER, ContentClassifier.classify(snap(ids = setOf("mini_watch_player"))).tag)
    }

    @Test fun layoutFallbackVideo() {
        val c = ContentClassifier.classify(snap(seek = true, scrollers = listOf(Scroller(0, 900, 1080, 2400, 10))))
        assertEquals("layout:seekbar+list-below", c.signal)
    }

    @Test fun layoutFallbackShorts() {
        val c = ContentClassifier.classify(snap(scrollers = listOf(Scroller(0, 0, 1080, 2400, 2))))
        assertEquals(ContentTag.SHORTS, c.tag)
    }

    @Test fun fullScreenScrollerOnBrowseScreenIsNotShorts() {
        val c = ContentClassifier.classify(
            snap(ids = setOf("browse_fragment_layout_coordinator_layout", "results"), scrollers = listOf(Scroller(0, 0, 1080, 2337, 2))),
        )
        assertEquals(ContentTag.OTHER, c.tag)
    }

    @Test fun homeFeedStaysOther() {
        val c = ContentClassifier.classify(snap(scrollers = listOf(Scroller(0, 300, 1080, 2200, 12))))
        assertEquals(ContentTag.OTHER, c.tag)
    }

    @Test fun debouncerNeedsTwoChecksAndHold() {
        val d = ClassificationDebouncer(2, 1000)
        assertNull(d.observe(ContentTag.SHORTS, 0))
        assertNull(d.observe(ContentTag.SHORTS, 500)) // 2 checks but held only 500ms
        assertEquals(Transition(null, ContentTag.SHORTS, 0), d.observe(ContentTag.SHORTS, 1200))
    }

    @Test fun flickerDoesNotCommit() {
        val d = ClassificationDebouncer(2, 1000)
        d.observe(ContentTag.SHORTS, 0); d.observe(ContentTag.SHORTS, 1200)
        assertNull(d.observe(ContentTag.OTHER, 2000))
        assertNull(d.observe(ContentTag.SHORTS, 2500)) // back before confirmation
        assertNull(d.observe(ContentTag.SHORTS, 3000))
        assertEquals(ContentTag.SHORTS, d.committed)
    }

    @Test fun leavingYouTubeIsATransition() {
        val d = ClassificationDebouncer(2, 1000)
        d.observe(ContentTag.VIDEO, 0); d.observe(ContentTag.VIDEO, 1200)
        d.observe(null, 5000)
        assertEquals(Transition(ContentTag.VIDEO, null, 5000), d.observe(null, 6100))
    }
}
