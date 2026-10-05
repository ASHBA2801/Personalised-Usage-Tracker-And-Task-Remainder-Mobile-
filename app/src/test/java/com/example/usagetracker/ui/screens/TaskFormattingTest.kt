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

class DeadlineLabelTest {
    private val zone = ZoneId.of("UTC")
    private fun at(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = at(5, 10, 0)
    private fun label(millis: Long, h24: Boolean) = TaskFormatting.deadlineLabel(millis, now, h24, zone, java.util.Locale.US)

    @Test
    fun today_tomorrow_andLaterDates_12Hour() {
        assertEquals("Today 6:00 PM", label(at(5, 18, 0), false))
        assertEquals("Tomorrow 9:00 AM", label(at(6, 9, 0), false))
        assertEquals("12 Oct, 6:00 PM", label(at(12, 18, 0), false))
    }

    @Test
    fun twentyFourHourClock_isRespected() {
        assertEquals("Today 18:00", label(at(5, 18, 0), true))
        assertEquals("12 Oct, 18:00", label(at(12, 18, 0), true))
    }

    @Test
    fun otherYear_showsYear_andDateOnlyDropsTime() {
        val next = LocalDateTime.of(2027, 1, 3, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("3 Jan 2027, 9:00 AM", label(next, false))
        assertEquals("Tomorrow", label(at(6, 23, 59), false))
    }

    @Test
    fun quickDeadlines_landOnTheAdvertisedTimes() {
        val today = LocalDate.of(2026, 10, 5)
        assertEquals(at(5, 18, 0), TaskFormatting.quickDeadline(TaskFormatting.QuickDeadline.TODAY_6PM, today, zone))
        assertEquals(at(5, 21, 0), TaskFormatting.quickDeadline(TaskFormatting.QuickDeadline.TONIGHT_9PM, today, zone))
        assertEquals(at(6, 9, 0), TaskFormatting.quickDeadline(TaskFormatting.QuickDeadline.TOMORROW_9AM, today, zone))
    }
}
