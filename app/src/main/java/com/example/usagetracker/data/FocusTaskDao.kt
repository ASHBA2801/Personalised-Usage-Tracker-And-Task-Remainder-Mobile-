package com.example.usagetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Transaction
    @Query("SELECT * FROM focus_tasks WHERE date = :date ORDER BY createdAt, id")
    fun observeWithSubTasksForDate(date: String): Flow<List<TaskWithSubTasks>>

    /** Only the two columns the import duplicate check needs. */
    @Query("SELECT date, title FROM focus_tasks WHERE date IN (:dates)")
    suspend fun getDateTitlesForDates(dates: List<String>): List<DateTitle>

    @Query("DELETE FROM focus_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM focus_tasks WHERE importBatchId = :batchId")
    suspend fun deleteByImportBatch(batchId: String): Int

    @Query("SELECT COUNT(*) FROM focus_tasks")
    suspend fun count(): Int

    /** How many copies of [title] already carried over from [from] onto [date]; guards against double carry-over. */
    @Query("SELECT COUNT(*) FROM focus_tasks WHERE date = :date AND title = :title AND carriedOverFromDate = :from")
    suspend fun countCarriedOver(date: String, title: String, from: String): Int

    @Query("DELETE FROM focus_tasks")
    suspend fun deleteAll()

    @Query("UPDATE focus_tasks SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompletedFlag(id: Long, completed: Boolean)

    @Query("UPDATE sub_tasks SET isCompleted = 1 WHERE taskId = :taskId")
    suspend fun completeAllSubTasks(taskId: Long)

    @Query("UPDATE sub_tasks SET isCompleted = :completed WHERE id = :subTaskId")
    suspend fun setSubTaskCompletedFlag(subTaskId: Long, completed: Boolean)

    @Query("SELECT * FROM sub_tasks WHERE taskId = :taskId AND isCompleted = 0 ORDER BY sortOrder, id")
    suspend fun getIncompleteSubTasks(taskId: Long): List<SubTask>

    @Insert
    suspend fun insertSubTasks(subTasks: List<SubTask>)

    /** Completing a task completes all its subtasks; reopening it leaves them as they are. */
    @Transaction
    suspend fun setTaskCompleted(taskId: Long, completed: Boolean) {
        setCompletedFlag(taskId, completed)
        if (completed) completeAllSubTasks(taskId)
    }

    /** Reopening a subtask reopens its parent. Finishing every subtask does not complete the parent. */
    @Transaction
    suspend fun setSubTaskCompleted(subTask: SubTask, completed: Boolean) {
        setSubTaskCompletedFlag(subTask.id, completed)
        if (!completed) setCompletedFlag(subTask.taskId, false)
    }

    /**
     * Copies [task] onto [toDate] as a fresh incomplete task, keeping its details and only its incomplete
     * subtasks (order and deadlines kept). The copy is not part of any import batch.
     */
    @Transaction
    suspend fun carryOver(task: FocusTask, toDate: String, now: Long): Long {
        val copy = task.copy(
            id = 0,
            date = toDate,
            isCompleted = false,
            carriedOverFromDate = task.date,
            createdAt = now,
            importBatchId = null,
        )
        return insertWithSubTasks(copy, getIncompleteSubTasks(task.id))
    }

    /** Inserts [task] and its [subTasks] (their taskId is filled in) atomically; returns the new task id. */
    @Transaction
    suspend fun insertWithSubTasks(task: FocusTask, subTasks: List<SubTask>): Long {
        val id = insert(task)
        if (subTasks.isNotEmpty()) insertSubTasks(subTasks.map { it.copy(id = 0, taskId = id) })
        return id
    }
}

data class DateTitle(val date: String, val title: String)
