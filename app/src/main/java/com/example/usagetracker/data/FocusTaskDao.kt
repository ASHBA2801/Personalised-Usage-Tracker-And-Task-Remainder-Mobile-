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

    @Query("SELECT * FROM focus_tasks WHERE id = :id")
    suspend fun getById(id: Long): FocusTask?

    /** Task totals for one day in a single aggregate row; feeds the Home card without loading the tasks. */
    @Query("SELECT COUNT(*) AS total, COALESCE(SUM(isCompleted), 0) AS done FROM focus_tasks WHERE date = :date")
    fun observeDayCounts(date: String): Flow<DayCounts>

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

    @Update
    suspend fun updateSubTask(subTask: SubTask)

    @Query("SELECT * FROM sub_tasks WHERE taskId = :taskId ORDER BY sortOrder, id")
    suspend fun getSubTasks(taskId: Long): List<SubTask>

    @Query("DELETE FROM sub_tasks WHERE id IN (:ids)")
    suspend fun deleteSubTasksByIds(ids: List<Long>)

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

    /**
     * Saves an edit and reconciles the sub-tasks in one transaction. [task] carries the edited fields; its
     * completion state is taken from the stored row so a stale copy can't undo a tick made meanwhile.
     * [subTasks] is the wanted list in display order: an entry with an id of an existing sub-task of this task
     * is kept (completed state and deadline preserved, title and sortOrder updated), id 0 is inserted, and
     * existing sub-tasks not listed are deleted. A completed task that gains an open sub-task is reopened,
     * the same as unchecking a sub-task. Returns false if the task no longer exists.
     */
    @Transaction
    suspend fun updateWithSubTasks(task: FocusTask, subTasks: List<SubTaskInput>): Boolean {
        val stored = getById(task.id) ?: return false
        val existing = getSubTasks(task.id).associateBy { it.id }
        val keptIds = subTasks.mapNotNull { it.id.takeIf { id -> id in existing } }.toSet()
        val removed = existing.keys - keptIds
        if (removed.isNotEmpty()) deleteSubTasksByIds(removed.toList())

        val inserts = ArrayList<SubTask>()
        subTasks.forEachIndexed { index, input ->
            val current = existing[input.id]
            if (current == null) {
                inserts += SubTask(taskId = task.id, title = input.title, sortOrder = index)
            } else if (current.title != input.title || current.sortOrder != index) {
                updateSubTask(current.copy(title = input.title, sortOrder = index))
            }
        }
        if (inserts.isNotEmpty()) insertSubTasks(inserts)

        val reopen = stored.isCompleted && inserts.isNotEmpty()
        update(task.copy(isCompleted = stored.isCompleted && !reopen))
        return true
    }
}

/** A sub-task as edited in the task sheet: [id] is 0 for a new one. */
data class SubTaskInput(val id: Long, val title: String)

/** Aggregate row behind the daily progress cards. */
data class DayCounts(val total: Int, val done: Int)

data class DateTitle(val date: String, val title: String)
