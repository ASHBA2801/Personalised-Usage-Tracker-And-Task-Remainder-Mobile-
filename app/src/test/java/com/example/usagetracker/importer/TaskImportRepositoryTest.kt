package com.example.usagetracker.importer

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.usagetracker.data.AppDatabase
import com.example.usagetracker.data.FocusTask
import com.example.usagetracker.data.UsageSession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TaskImportRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TaskImportRepository
    private val today = LocalDate.of(2026, 10, 5)
    private val zone = ZoneId.systemDefault()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = TaskImportRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun template(): List<ValidTask> = ImportValidator(today, zone)
        .validate(ImportFileReader.read(File("src/main/assets/templates/tasks_template.json").inputStream(), "tasks_template.json"))
        .tasks

    private fun task(title: String, date: String = "2026-10-05", subs: List<String> = emptyList()) = ValidTask(
        location = "Task", title = title, date = LocalDate.parse(date), deadline = null, priority = 0,
        estimatedMinutes = null, tags = emptyList(), notes = null, completed = false,
        subTasks = subs.map { ValidSubTask(it, false, null) },
    )

    @Test
    fun importTemplate_createsTasksWithSubtasksInOrder() = runTest {
        val result = repo.import(template(), skipDuplicates = true)
        assertEquals(3, result.insertedCount)
        val first = db.focusTaskDao().getForDate("2026-10-05").single()
        assertEquals(FocusTask.PRIORITY_HIGH, first.priority)
        assertEquals("study,networking", first.tags)
        assertEquals(90, first.estimatedMinutes)
        assertEquals(result.batchId, first.importBatchId)
        assertEquals(
            listOf("Watch lectures 3.1 to 3.4", "Take notes on congestion control", "Solve the 5 practice questions"),
            db.subTaskDao().getForTask(first.id).map { it.title },
        )
        assertEquals(listOf(0, 1, 2), db.subTaskDao().getForTask(first.id).map { it.sortOrder })
        assertEquals(1, db.focusTaskDao().getForDate("2026-10-06").size)
        assertEquals(1, db.focusTaskDao().getForDate("2026-10-07").size)
        assertEquals(6, db.subTaskDao().count())
    }

    @Test
    fun insertIsAllOrNothing() = runTest {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER fail_on_boom BEFORE INSERT ON sub_tasks WHEN NEW.title = 'boom' BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )
        val tasks = listOf(task("ok 1", subs = listOf("fine")), task("ok 2"), task("bad", subs = listOf("boom")))
        try {
            repo.import(tasks, skipDuplicates = false)
            fail("Expected the insert to fail")
        } catch (_: Exception) {
        }
        assertEquals(0, db.focusTaskDao().count())
        assertEquals(0, db.subTaskDao().count())
    }

    @Test
    fun dedupe_onSkipsSameTitleIgnoringCaseAndSameDate() = runTest {
        db.focusTaskDao().insert(FocusTask(title = "GO FOR A 30 MINUTE WALK", date = "2026-10-06", createdAt = 1))
        db.focusTaskDao().insert(FocusTask(title = "Submit the monthly expense report", date = "2026-10-08", createdAt = 1))
        val tasks = template()
        assertEquals(setOf(1), repo.findDuplicates(tasks))
        assertEquals(2, repo.import(tasks, skipDuplicates = true).insertedCount)
        assertEquals(1, db.focusTaskDao().getForDate("2026-10-06").size)
    }

    @Test
    fun dedupe_offImportsEverything() = runTest {
        db.focusTaskDao().insert(FocusTask(title = "Go for a 30 minute walk", date = "2026-10-06", createdAt = 1))
        assertEquals(3, repo.import(template(), skipDuplicates = false).insertedCount)
        assertEquals(2, db.focusTaskDao().getForDate("2026-10-06").size)
    }

    @Test
    fun undo_removesExactlyThatBatchAndItsSubtasks() = runTest {
        val manualId = db.focusTaskDao().insertWithSubTasks(
            FocusTask(title = "Manual", date = "2026-10-05", createdAt = 1),
            listOf(com.example.usagetracker.data.SubTask(taskId = 0, title = "manual step", sortOrder = 0)),
        )
        val other = repo.import(listOf(task("Other import", subs = listOf("x"))), skipDuplicates = false)
        val batch = repo.import(template(), skipDuplicates = false)

        assertEquals(3, repo.undo(batch.batchId))

        assertEquals(listOf("Manual", "Other import"), db.focusTaskDao().getForDate("2026-10-05").map { it.title })
        assertTrue(db.focusTaskDao().getForDate("2026-10-06").isEmpty())
        assertEquals(listOf("manual step"), db.subTaskDao().getForTask(manualId).map { it.title })
        assertEquals(2, db.subTaskDao().count())
        assertEquals(1, repo.undo(other.batchId))
        assertEquals(1, db.subTaskDao().count())
    }

    @Test
    fun deleteAllData_clearsImportedSubtasks_retentionLeavesThem() = runTest {
        repo.import(template(), skipDuplicates = false)
        db.usageSessionDao().insert(UsageSession(packageName = "a", appName = "A", category = "USEFUL", startTime = 1, endTime = 2))
        db.usageSessionDao().deleteOlderThan(Long.MAX_VALUE)
        assertEquals(6, db.subTaskDao().count())

        db.withTransaction {
            db.usageSessionDao().deleteAll()
            db.focusTaskDao().deleteAll()
        }
        assertEquals(0, db.subTaskDao().count())
    }

    @Test
    fun malformedJson_neverReachesDatabase() = runTest {
        try {
            ImportFileReader.read("[{\"title\": \"a\"".byteInputStream(), "bad.json")
            fail("Expected ImportException")
        } catch (e: ImportException) {
            assertTrue(e.issue.location.startsWith("Line 1"))
        }
        assertEquals(0, db.focusTaskDao().count())
    }
}
