package com.example.usagetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageSessionDao {
    @Insert
    suspend fun insert(session: UsageSession): Long

    @Query("SELECT * FROM usage_sessions WHERE startTime >= :from AND startTime < :to ORDER BY startTime")
    suspend fun getBetween(from: Long, to: Long): List<UsageSession>

    /**
     * Live rows overlapping [start, end), so a session straddling a boundary (e.g. midnight) is
     * included; callers clip durations to the window. Re-emits whenever the table changes.
     */
    @Query("SELECT * FROM usage_sessions WHERE startTime < :end AND endTime > :start ORDER BY startTime")
    fun getSessionsBetween(start: Long, end: Long): Flow<List<UsageSession>>

    @Query("SELECT COUNT(*) FROM usage_sessions WHERE packageName = :packageName AND startTime = :startTime")
    suspend fun countByPackageAndStart(packageName: String, startTime: Long): Int

    @Query("UPDATE usage_sessions SET endTime = :endTime WHERE id = :id")
    suspend fun updateEndTime(id: Long, endTime: Long)

    /** Rows for [packageName] that overlap [start, end]; zero-length (just opened) rows count. */
    @Query("SELECT COUNT(*) FROM usage_sessions WHERE packageName = :packageName AND startTime < :end AND endTime >= :start")
    suspend fun countOverlapping(packageName: String, start: Long, end: Long): Int

    /** One row per app ever tracked, labelled with its most recent name. */
    @Query("SELECT packageName, appName, MAX(startTime) AS lastStart FROM usage_sessions GROUP BY packageName ORDER BY appName COLLATE NOCASE")
    fun observeTrackedApps(): Flow<List<TrackedApp>>

    @Query("SELECT DISTINCT packageName, contentTag FROM usage_sessions")
    suspend fun distinctKeys(): List<SessionKey>

    /** `IS` rather than `=` so a null [contentTag] matches rows with no tag. */
    @Query("UPDATE usage_sessions SET category = :category WHERE packageName = :packageName AND contentTag IS :contentTag")
    suspend fun updateCategory(packageName: String, contentTag: String?, category: String)

    @Query("DELETE FROM usage_sessions WHERE endTime < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    @Query("DELETE FROM usage_sessions")
    suspend fun deleteAll()
}

data class TrackedApp(val packageName: String, val appName: String, val lastStart: Long)

data class SessionKey(val packageName: String, val contentTag: String?)
