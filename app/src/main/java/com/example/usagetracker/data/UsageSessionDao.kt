package com.example.usagetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageSessionDao {
    @Insert
    suspend fun insert(session: UsageSession): Long

    @Query("SELECT * FROM usage_sessions WHERE startTime >= :from AND startTime < :to ORDER BY startTime")
    suspend fun getBetween(from: Long, to: Long): List<UsageSession>

    @Insert
    suspend fun insertAll(sessions: List<UsageSession>)

    // The three aggregates below sum the part of each row inside [start, end), so a session straddling
    // a boundary (e.g. midnight) counts only for the time within the window. Re-emit on table change.
    @RewriteQueriesToDropUnusedColumns
    @Query(
        "SELECT packageName, appName, SUM(MIN(endTime, :end) - MAX(startTime, :start)) AS totalMillis, MAX(startTime) AS lastStart " +
            "FROM usage_sessions WHERE startTime < :end AND endTime > :start " +
            "GROUP BY packageName ORDER BY totalMillis DESC",
    )
    fun observeAppTotals(start: Long, end: Long): Flow<List<AppTotal>>

    @Query(
        "SELECT category, SUM(MIN(endTime, :end) - MAX(startTime, :start)) AS totalMillis " +
            "FROM usage_sessions WHERE startTime < :end AND endTime > :start GROUP BY category",
    )
    fun observeCategoryTotals(start: Long, end: Long): Flow<List<CategoryTotal>>

    @Query(
        "SELECT contentTag, SUM(MIN(endTime, :end) - MAX(startTime, :start)) AS totalMillis " +
            "FROM usage_sessions WHERE startTime < :end AND endTime > :start " +
            "AND packageName = 'com.google.android.youtube' GROUP BY contentTag",
    )
    fun observeYouTubeContentTotals(start: Long, end: Long): Flow<List<ContentTotal>>

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

    @Query("DELETE FROM usage_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM usage_sessions WHERE endTime < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    @Query("DELETE FROM usage_sessions")
    suspend fun deleteAll()
}

data class TrackedApp(val packageName: String, val appName: String, val lastStart: Long)

data class SessionKey(val packageName: String, val contentTag: String?)

/** Sessions shorter than this are screen flickers / quick app switches and are not stored. */
const val MIN_SESSION_MILLIS = 2000L

data class AppTotal(val packageName: String, val appName: String, val totalMillis: Long)

data class CategoryTotal(val category: String, val totalMillis: Long)

data class ContentTotal(val contentTag: String?, val totalMillis: Long)
