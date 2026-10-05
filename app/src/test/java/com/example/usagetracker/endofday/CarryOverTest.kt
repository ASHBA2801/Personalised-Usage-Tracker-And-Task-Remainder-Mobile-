package com.example.usagetracker.endofday

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.usagetracker.data.AppDatabase
import com.example.usagetracker.data.FocusTask
import com.example.usagetracker.data.SubTask
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The same DAO call EndOfDayWorker makes for each incomplete task. */
@RunWith(AndroidJUnit4::class)
class CarryOverTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun copiesDetailsAndOnlyIncompleteSubtasks_withoutImportBatch() = runTest {
        val dao = db.focusTaskDao()
        val original = FocusTask(
            title = "Report", date = "2026-10-05", createdAt = 1, deadline = 123_456L, priority = FocusTask.PRIORITY_HIGH,
            notes = "notes", tags = "work,q4", estimatedMinutes = 45, importBatchId = "batch-1",
        )
        val id = dao.insertWithSubTasks(
            original,
            listOf(
                SubTask(taskId = 0, title = "done step", isCompleted = true, sortOrder = 0),
                SubTask(taskId = 0, title = "open A", deadline = 999L, sortOrder = 1),
                SubTask(taskId = 0, title = "open B", sortOrder = 2),
            ),
        )
        val stored = dao.getIncompleteForDate("2026-10-05").single()

        val copyId = dao.carryOver(stored, "2026-10-06", now = 50)

        val copy = dao.getForDate("2026-10-06").single()
        assertEquals(copyId, copy.id)
        assertEquals("Report", copy.title)
        assertFalse(copy.isCompleted)
        assertEquals("2026-10-05", copy.carriedOverFromDate)
        assertEquals(50, copy.createdAt)
        assertEquals(123_456L, copy.deadline)
        assertEquals(FocusTask.PRIORITY_HIGH, copy.priority)
        assertEquals("notes", copy.notes)
        assertEquals("work,q4", copy.tags)
        assertEquals(45, copy.estimatedMinutes)
        assertNull(copy.importBatchId)

        val subs = db.subTaskDao().getForTask(copyId)
        assertEquals(listOf("open A", "open B"), subs.map { it.title })
        assertEquals(listOf(1, 2), subs.map { it.sortOrder })
        assertEquals(listOf(999L, null), subs.map { it.deadline })
        assertTrue(subs.none { it.isCompleted })

        // The original and its subtasks are untouched, and undoing its import leaves the copy alone.
        assertEquals(3, db.subTaskDao().getForTask(id).size)
        dao.deleteByImportBatch("batch-1")
        assertEquals(1, dao.getForDate("2026-10-06").size)
        assertEquals(2, db.subTaskDao().count())
    }
}
