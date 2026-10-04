package com.example.usagetracker.retention

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RetentionCutoffTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun cutoffIsThreeCalendarMonthsBack() {
        val now = ZonedDateTime.of(2026, 10, 4, 12, 0, 0, 0, utc)
        val expected = ZonedDateTime.of(2026, 7, 4, 12, 0, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals(expected, RetentionCleanupWorker.cutoffMillis(now))
    }

    @Test
    fun cutoffClampsToEndOfShorterMonth() {
        val now = ZonedDateTime.of(2026, 5, 31, 0, 0, 0, 0, utc)
        val expected = ZonedDateTime.of(2026, 2, 28, 0, 0, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals(expected, RetentionCleanupWorker.cutoffMillis(now))
    }
}
