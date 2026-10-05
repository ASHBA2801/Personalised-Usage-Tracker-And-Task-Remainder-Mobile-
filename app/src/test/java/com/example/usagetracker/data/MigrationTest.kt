package com.example.usagetracker.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate3To4_keepsExistingTasksAndAddsDefaults() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO focus_tasks (id, title, date, isCompleted, carriedOverFromDate, createdAt) " +
                    "VALUES (1, 'Old task', '2026-10-01', 1, '2026-09-30', 42)",
            )
            db.execSQL(
                "INSERT INTO usage_sessions (packageName, appName, category, contentTag, startTime, endTime) " +
                    "VALUES ('a', 'A', 'USEFUL', NULL, 100, 200)",
            )
        }

        // Validates the migrated schema against the exported v4 schema.
        helper.runMigrationsAndValidate(DB, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT title, date, isCompleted, carriedOverFromDate, createdAt, priority, deadline, notes, tags, estimatedMinutes, importBatchId FROM focus_tasks").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals("Old task", c.getString(0))
                assertEquals("2026-10-01", c.getString(1))
                assertEquals(1, c.getInt(2))
                assertEquals("2026-09-30", c.getString(3))
                assertEquals(42L, c.getLong(4))
                assertEquals(0, c.getInt(5))
                for (i in 6..10) assertEquals(true, c.isNull(i))
            }
            db.query("SELECT COUNT(*) FROM usage_sessions").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            db.query("SELECT COUNT(*) FROM sub_tasks").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }

    @Test
    fun migrateAllVersions_opensWithRoom() = runTest {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO focus_tasks (title, date, isCompleted, createdAt) VALUES ('From v1', '2026-01-01', 0, 1)")
        }
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, DB)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val task = db.focusTaskDao().getForDate("2026-01-01").single()
            assertEquals("From v1", task.title)
            assertEquals(FocusTask.PRIORITY_NONE, task.priority)
            assertNull(task.importBatchId)
            // Foreign keys must be enforced on a migrated database, or cascade deletes would orphan subtasks.
            db.subTaskDao().insert(SubTask(taskId = task.id, title = "step", sortOrder = 0))
            db.focusTaskDao().deleteById(task.id)
            assertEquals(0, db.subTaskDao().count())
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
