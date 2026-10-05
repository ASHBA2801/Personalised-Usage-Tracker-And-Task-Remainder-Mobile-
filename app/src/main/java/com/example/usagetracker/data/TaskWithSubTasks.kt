package com.example.usagetracker.data

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithSubTasks(
    @Embedded val task: FocusTask,
    @Relation(parentColumn = "id", entityColumn = "taskId") val subTasks: List<SubTask>,
) {
    /** @Relation can't order, so callers use this. */
    val orderedSubTasks: List<SubTask> get() = subTasks.sortedWith(compareBy({ it.sortOrder }, { it.id }))
}
