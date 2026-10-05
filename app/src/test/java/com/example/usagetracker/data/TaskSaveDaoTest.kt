package com.example.usagetracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.example.usagetracker.ui.screens.TaskFormatting
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TaskSaveDaoTest {
    private lateinit var db: AppDatabase
    private val dao get() = db.focusTaskDao()
    private val date = "2026-10-05"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun task(title: String = "Task", deadline: Long? = null) =
        FocusTask(title = title, date = date, createdAt = 1, deadline = deadline, priority = FocusTask.PRIORITY_HIGH, notes = "n", estimatedMinutes = 30)

    private fun steps(vararg titles: String) = titles.mapIndexed { i, t -> SubTask(taskId = 0, title = t, sortOrder = i) }

    @Test
    fun saveWithSubTasks_storesEverythingInOrder() = runTest {
        val id = dao.insertWithSubTasks(task(deadline = 1_000L), steps("a", "b", "c"))
        val saved = dao.getById(id)!!
        assertEquals(FocusTask.PRIORITY_HIGH, saved.priority)
        assertEquals("n", saved.notes)
        assertEquals(30, saved.estimatedMinutes)
        assertEquals(1_000L, saved.deadline)
        assertEquals(listOf("a", "b", "c"), db.subTaskDao().getForTask(id).map { it.title })
        assertEquals(listOf(0, 1, 2), db.subTaskDao().getForTask(id).map { it.sortOrder })
    }

    @Test
    fun editReconcile_keepsCompletedState_insertsNew_deletesRemoved() = runTest {
        val id = dao.insertWithSubTasks(task(), steps("a", "b", "c"))
        val (a, b, c) = db.subTaskDao().getForTask(id)
        dao.setSubTaskCompleted(a, true)
        dao.setSubTaskCompleted(c, true)

        // a kept as-is, b removed, c retitled and moved up, d new.
        val edited = task("Renamed").copy(id = id, deadline = 5_000L)
        assertTrue(dao.updateWithSubTasks(edited, listOf(SubTaskInput(a.id, "a"), SubTaskInput(c.id, "c2"), SubTaskInput(0, "d"))))

        val after = db.subTaskDao().getForTask(id)
        assertEquals(listOf("a", "c2", "d"), after.map { it.title })
        assertEquals(listOf(0, 1, 2), after.map { it.sortOrder })
        assertEquals(listOf(true, true, false), after.map { it.isCompleted })
        assertEquals(c.id, after[1].id) // retitled in place, not recreated
        assertFalse(after.any { it.id == b.id })
        val task = dao.getById(id)!!
        assertEquals("Renamed", task.title)
        assertEquals(5_000L, task.deadline)
    }

    @Test
    fun editReconcile_ignoresIdsFromOtherTasks() = runTest {
        val mine = dao.insertWithSubTasks(task("Mine"), steps("a"))
        val other = dao.insertWithSubTasks(task("Other"), steps("x"))
        val foreign = db.subTaskDao().getForTask(other).single()
        dao.updateWithSubTasks(dao.getById(mine)!!, listOf(SubTaskInput(foreign.id, "hijack")))
        assertEquals(listOf("x"), db.subTaskDao().getForTask(other).map { it.title })
        assertEquals(listOf("hijack"), db.subTaskDao().getForTask(mine).map { it.title })
    }

    @Test
    fun editReconcile_staleCopyDoesNotUndoCompletion_andNewOpenStepReopens() = runTest {
        val id = dao.insertWithSubTasks(task(), steps("a"))
        val stale = dao.getById(id)!! // isCompleted = false
        dao.setTaskCompleted(id, true)
        val kept = db.subTaskDao().getForTask(id).single()

        dao.updateWithSubTasks(stale, listOf(SubTaskInput(kept.id, "a"))) // no new steps
        assertTrue(dao.getById(id)!!.isCompleted)

        dao.updateWithSubTasks(stale, listOf(SubTaskInput(kept.id, "a"), SubTaskInput(0, "new")))
        assertFalse(dao.getById(id)!!.isCompleted)
        assertTrue(db.subTaskDao().getForTask(id).first().isCompleted)
    }

    @Test
    fun editOfDeletedTask_returnsFalse_andInsertsNothing() = runTest {
        val id = dao.insertWithSubTasks(task(), steps("a"))
        val gone = dao.getById(id)!!
        dao.deleteById(id)
        assertFalse(dao.updateWithSubTasks(gone, steps("z").map { SubTaskInput(0, it.title) }))
        assertEquals(0, db.subTaskDao().count())
    }

    @Test
    fun deletingTask_cascadesItsSubTasks() = runTest {
        val id = dao.insertWithSubTasks(task(), steps("a", "b"))
        dao.deleteById(id)
        assertNull(dao.getById(id))
        assertEquals(0, db.subTaskDao().count())
    }

    @Test
    fun deadline_roundTripsAcrossTimeZones() = runTest {
        // 6 PM on 12 Oct as the user typed it in Kolkata (+05:30).
        val kolkata = ZoneId.of("Asia/Kolkata")
        val millis = TaskFormatting.deadlineMillis(LocalDate.of(2026, 10, 12), LocalTime.of(18, 0), kolkata)
        val id = dao.insert(task(deadline = millis))
        val read = dao.getById(id)!!.deadline!!
        assertEquals(millis, read)

        fun local(zone: String) = java.time.Instant.ofEpochMilli(read).atZone(ZoneId.of(zone)).toLocalDateTime().toString()
        assertEquals("2026-10-12T18:00", local("Asia/Kolkata"))
        assertEquals("2026-10-12T12:30", local("UTC"))
        // Same instant, so a traveller in New York sees the same moment, not a shifted wall-clock time.
        assertEquals("2026-10-12T08:30", local("America/New_York"))
    }

    @Test
    fun dayCounts_aggregateInOneRow_andFollowChangesLive() = runTest {
        assertEquals(DayCounts(0, 0), dao.observeDayCounts(date).first())
        val a = dao.insertWithSubTasks(task("A"), steps("s"))
        dao.insert(task("B"))
        dao.insert(task("C").copy(date = "2026-10-06"))
        assertEquals(DayCounts(total = 2, done = 0), dao.observeDayCounts(date).first())
        dao.setTaskCompleted(a, true)
        assertEquals(DayCounts(total = 2, done = 1), dao.observeDayCounts(date).first())
    }

    @Test
    fun observeWithSubTasks_carriesSubTaskCountsInOneQuery() = runTest {
        val id = dao.insertWithSubTasks(task(), steps("a", "b", "c"))
        dao.setSubTaskCompleted(db.subTaskDao().getForTask(id).first(), true)
        val item = dao.observeWithSubTasksForDate(date).first().single()
        assertEquals(3, item.subTasks.size)
        assertEquals(1, item.subTasks.count { it.isCompleted })
    }
}
