package com.example.usagetracker.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TaskFormattingTest {
    private val zone = ZoneId.of("UTC")
    private fun at(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun dateOnlyDeadline_onItsOwnDay_readsEndOfDay() {
        assertEquals("Due end of day", TaskFormatting.deadline(at(7, 23, 59), LocalDate.of(2026, 10, 7), zone))
    }

    @Test
    fun dateOnlyDeadline_onAnotherDay_showsDateOnly() {
        assertEquals("Due 7 Oct", TaskFormatting.deadline(at(7, 23, 59), LocalDate.of(2026, 10, 5), zone))
    }

    @Test
    fun dayLabels() {
        val today = LocalDate.of(2026, 10, 5)
        assertEquals("Today", TaskFormatting.dayLabel(today, today))
        assertEquals("Tomorrow", TaskFormatting.dayLabel(today.plusDays(1), today))
        assertEquals("Yesterday", TaskFormatting.dayLabel(today.minusDays(1), today))
    }

    @Test
    fun estimates() {
        assertEquals("45 min", TaskFormatting.estimate(45))
        assertEquals("1 h 30 min", TaskFormatting.estimate(90))
        assertEquals("2 h", TaskFormatting.estimate(120))
    }
}
