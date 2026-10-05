package com.example.usagetracker.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubTaskDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun taskWithSteps(title: String = "Task", steps: Int = 2): Long =
        db.focusTaskDao().insertWithSubTasks(
            FocusTask(title = title, date = "2026-10-05", createdAt = 1),
            List(steps) { SubTask(taskId = 0, title = "Step $it", sortOrder = it) },
        )

    @Test
    fun deletingTask_cascadesToSubTasks() = runTest {
        val keep = taskWithSteps("Keep")
        val drop = taskWithSteps("Drop", steps = 3)
        db.focusTaskDao().deleteById(drop)
        assertEquals(2, db.subTaskDao().count())
        assertEquals(listOf("Step 0", "Step 1"), db.subTaskDao().getForTask(keep).map { it.title })
    }

    @Test
    fun deleteAllData_clearsSubTasks() = runTest {
        taskWithSteps()
        // Same statements as Settings → "Delete all data".
        db.withTransaction {
            db.usageSessionDao().deleteAll()
            db.focusTaskDao().deleteAll()
        }
        assertEquals(0, db.focusTaskDao().count())
        assertEquals(0, db.subTaskDao().count())
    }

    @Test
    fun retentionCleanup_leavesTasksAndSubTasksAlone() = runTest {
        taskWithSteps()
        db.usageSessionDao().insert(UsageSession(packageName = "a", appName = "A", category = "USEFUL", startTime = 1, endTime = 2))
        db.usageSessionDao().deleteOlderThan(Long.MAX_VALUE)
        assertEquals(1, db.focusTaskDao().count())
        assertEquals(2, db.subTaskDao().count())
    }

    @Test
    fun completingParent_completesSubTasks_andReopeningSubTaskReopensParent() = runTest {
        val id = taskWithSteps()
        val dao = db.focusTaskDao()
        dao.setTaskCompleted(id, true)
        assertTrue(db.subTaskDao().getForTask(id).all { it.isCompleted })
        assertTrue(dao.getForDate("2026-10-05").single().isCompleted)

        val first = db.subTaskDao().getForTask(id).first()
        dao.setSubTaskCompleted(first, false)
        assertFalse(dao.getForDate("2026-10-05").single().isCompleted)
        assertFalse(db.subTaskDao().getForTask(id).first().isCompleted)
    }

    @Test
    fun checkingAllSubTasks_doesNotCompleteParent() = runTest {
        val id = taskWithSteps()
        db.subTaskDao().getForTask(id).forEach { db.focusTaskDao().setSubTaskCompleted(it, true) }
        assertFalse(db.focusTaskDao().getForDate("2026-10-05").single().isCompleted)
    }
}
