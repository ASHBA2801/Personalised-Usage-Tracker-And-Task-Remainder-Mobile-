package com.example.usagetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SubTaskDao {
    @Insert
    suspend fun insert(subTask: SubTask): Long

    @Insert
    suspend fun insertAll(subTasks: List<SubTask>)

    @Query("SELECT * FROM sub_tasks WHERE taskId = :taskId ORDER BY sortOrder, id")
    suspend fun getForTask(taskId: Long): List<SubTask>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM sub_tasks WHERE taskId = :taskId")
    suspend fun maxSortOrder(taskId: Long): Int

    @Query("UPDATE sub_tasks SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Query("UPDATE sub_tasks SET isCompleted = :completed WHERE taskId = :taskId")
    suspend fun setAllCompleted(taskId: Long, completed: Boolean)

    @Query("DELETE FROM sub_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM sub_tasks")
    suspend fun count(): Int
}
