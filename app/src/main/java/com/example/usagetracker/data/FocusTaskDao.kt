package com.example.usagetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusTaskDao {
    @Insert
    suspend fun insert(task: FocusTask): Long

    @Update
    suspend fun update(task: FocusTask)

    @Query("SELECT * FROM focus_tasks WHERE date = :date ORDER BY createdAt")
    suspend fun getForDate(date: String): List<FocusTask>

    @Query("SELECT * FROM focus_tasks WHERE date = :date AND isCompleted = 0 ORDER BY createdAt, id")
    suspend fun getIncompleteForDate(date: String): List<FocusTask>

    @Query("SELECT * FROM focus_tasks WHERE date = :date ORDER BY createdAt, id")
    fun observeForDate(date: String): Flow<List<FocusTask>>

    @Query("DELETE FROM focus_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
