package com.example.usagetracker.ui.screens

import com.example.usagetracker.data.DayCounts
import com.example.usagetracker.data.FocusTask
import com.example.usagetracker.data.SubTask
import com.example.usagetracker.data.TaskWithSubTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskProgressTest {
    private fun task(done: Boolean, subs: List<Boolean> = emptyList()) = TaskWithSubTasks(
        FocusTask(id = 1, title = "t", date = "2026-10-05", isCompleted = done, createdAt = 0),
        subs.mapIndexed { i, d -> SubTask(id = i + 1L, taskId = 1, title = "s$i", isCompleted = d, sortOrder = i) },
    )

    @Test
    fun zeroTasks_isEmptyAndNeverDivides() {
        val p = dayProgress(emptyList())
        assertFalse(p.hasItems)
        assertEquals(0f, p.fraction, 0f)
        assertEquals(0, p.percent)
        assertFalse(p.isComplete)
    }

    @Test
    fun allDone_is100Percent() {
        val p = dayProgress(listOf(task(true), task(true)))
        assertEquals(Progress(2, 2), p)
        assertEquals(1f, p.fraction, 0f)
        assertEquals(100, p.percent)
        assertTrue(p.isComplete)
    }

    @Test
    fun partial_roundsPercentDown() {
        val p = dayProgress(listOf(task(true), task(false), task(false)))
        assertEquals(1, p.done)
        assertEquals(3, p.total)
        assertEquals(33, p.percent)
        assertFalse(p.isComplete)
    }

    @Test
    fun dayProgress_countsTheTaskCheckbox_notItsSubTasks() {
        // All sub-tasks ticked but the parent open: not done. Parent done with no sub-tasks: done.
        val p = dayProgress(listOf(task(false, listOf(true, true)), task(true)))
        assertEquals(Progress(1, 2), p)
    }

    @Test
    fun almostComplete_neverShows100() {
        assertEquals(99, Progress(199, 200).percent)
    }

    @Test
    fun subTaskProgress_countsCompletedSteps() {
        val subs = task(false, listOf(true, false, true, false, false)).subTasks
        val p = subTaskProgress(subs)
        assertEquals(Progress(2, 5), p)
        assertEquals(0.4f, p.fraction, 0.0001f)
        assertFalse(subTaskProgress(emptyList()).hasItems)
    }

    @Test
    fun dayCounts_mapToProgress() {
        assertEquals(Progress(3, 7), dayProgress(DayCounts(total = 7, done = 3)))
        assertEquals(42, dayProgress(DayCounts(total = 7, done = 3)).percent)
    }
}
