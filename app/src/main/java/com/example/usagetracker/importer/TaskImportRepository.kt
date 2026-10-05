package com.example.usagetracker.importer

import androidx.room.withTransaction
import com.example.usagetracker.data.AppDatabase
import com.example.usagetracker.data.FocusTask
import com.example.usagetracker.data.SubTask
import java.util.UUID

/** Writes validated imports to the database and undoes them by batch. */
class TaskImportRepository(private val db: AppDatabase) {
    private val dao = db.focusTaskDao()

    /** Indexes into [tasks] of those whose title (ignoring case) and date match a task already stored. */
    suspend fun findDuplicates(tasks: List<ValidTask>): Set<Int> {
        if (tasks.isEmpty()) return emptySet()
        val existing = dao.getDateTitlesForDates(tasks.map { it.date.toString() }.distinct())
            .mapTo(HashSet()) { key(it.date, it.title) }
        return tasks.indices.filterTo(HashSet()) { key(tasks[it].date.toString(), tasks[it].title) in existing }
    }

    /**
     * Inserts [tasks] (minus duplicates when [skipDuplicates]) and their subtasks in one transaction,
     * so either everything is saved or nothing is. Returns the batch id for [undo] and the task count.
     */
    suspend fun import(tasks: List<ValidTask>, skipDuplicates: Boolean, now: Long = System.currentTimeMillis()): ImportResult {
        val batchId = UUID.randomUUID().toString()
        val count = db.withTransaction {
            val skip = if (skipDuplicates) findDuplicates(tasks) else emptySet()
            var inserted = 0
            tasks.forEachIndexed { i, t ->
                if (i in skip) return@forEachIndexed
                dao.insertWithSubTasks(
                    FocusTask(
                        title = t.title,
                        date = t.date.toString(),
                        isCompleted = t.completed,
                        createdAt = now,
                        deadline = t.deadline,
                        priority = t.priority,
                        notes = t.notes,
                        tags = FocusTask.joinTags(t.tags),
                        estimatedMinutes = t.estimatedMinutes,
                        importBatchId = batchId,
                    ),
                    t.subTasks.mapIndexed { order, s ->
                        SubTask(taskId = 0, title = s.title, isCompleted = s.completed, deadline = s.deadline, sortOrder = order)
                    },
                )
                inserted++
            }
            inserted
        }
        return ImportResult(batchId, count)
    }

    /** Deletes exactly the tasks of one import; their subtasks go with them via the cascade. */
    suspend fun undo(batchId: String): Int = dao.deleteByImportBatch(batchId)

    private fun key(date: String, title: String) = date + "\u0000" + title.lowercase()
}

data class ImportResult(val batchId: String, val insertedCount: Int)
