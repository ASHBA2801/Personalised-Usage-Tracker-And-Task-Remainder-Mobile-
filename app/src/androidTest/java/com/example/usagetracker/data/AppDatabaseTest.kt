package com.example.usagetracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun usageSession_roundTrip() = runTest {
        val session = UsageSession(
            packageName = "com.youtube", appName = "YouTube", category = "LOW_VALUE",
            contentTag = "shorts", startTime = 1_000, endTime = 2_000,
        )
        val id = db.usageSessionDao().insert(session)

        val result = db.usageSessionDao().getBetween(0, 5_000)
        assertEquals(listOf(session.copy(id = id)), result)
    }

    @Test
    fun usageSession_deleteOlderThan() = runTest {
        val dao = db.usageSessionDao()
        dao.insert(UsageSession(packageName = "a", appName = "A", category = "USEFUL", startTime = 100, endTime = 200))
        dao.insert(UsageSession(packageName = "b", appName = "B", category = "USEFUL", startTime = 900, endTime = 1_000))

        dao.deleteOlderThan(500)

        assertEquals(listOf("b"), dao.getBetween(0, 5_000).map { it.packageName })
    }

    @Test
    fun focusTask_roundTripUpdateDelete() = runTest {
        val dao = db.focusTaskDao()
        val task = FocusTask(
            title = "Write report", date = "2026-10-04", carriedOverFromDate = "2026-10-03", createdAt = 123,
        )
        val id = dao.insert(task)

        val saved = dao.getForDate("2026-10-04").single()
        assertEquals(task.copy(id = id), saved)
        assertEquals(false, saved.isCompleted)

        dao.update(saved.copy(isCompleted = true, title = "Write final report"))
        val updated = dao.getForDate("2026-10-04").single()
        assertTrue(updated.isCompleted)
        assertEquals("Write final report", updated.title)

        dao.deleteById(id)
        assertTrue(dao.getForDate("2026-10-04").isEmpty())
    }
}
