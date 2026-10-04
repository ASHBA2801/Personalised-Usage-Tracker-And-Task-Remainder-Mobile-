package com.example.usagetracker.tracking

import android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND as BG
import android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND as FG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionPairerTest {
    private fun e(pkg: String, type: Int, t: Long) = RawEvent(pkg, type, t)

    @Test fun pairsForegroundWithBackground() {
        val r = SessionPairer.pair(listOf(e("a", FG, 1000), e("a", BG, 9000), e("b", FG, 9000), e("b", BG, 20000)))
        assertEquals(listOf(PairedSession("a", 1000, 9000), PairedSession("b", 9000, 20000)), r.sessions)
        assertNull(r.openSince)
    }

    @Test fun mergesActivityHopWithinSameApp() {
        val r = SessionPairer.pair(listOf(e("a", FG, 1000), e("a", BG, 5000), e("a", FG, 5100), e("a", BG, 8000)))
        assertEquals(listOf(PairedSession("a", 1000, 8000)), r.sessions)
    }

    @Test fun reportsStillOpenSessionAndIgnoresOrphanBackground() {
        val r = SessionPairer.pair(listOf(e("x", BG, 500), e("a", FG, 1000), e("b", FG, 2000), e("b", BG, 4000)))
        assertEquals(listOf(PairedSession("b", 2000, 4000)), r.sessions)
        assertEquals(1000L, r.openSince)
    }
}
