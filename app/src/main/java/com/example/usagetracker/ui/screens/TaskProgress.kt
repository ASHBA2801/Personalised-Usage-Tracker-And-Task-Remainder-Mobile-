package com.example.usagetracker.ui.screens

import com.example.usagetracker.data.DayCounts
import com.example.usagetracker.data.SubTask
import com.example.usagetracker.data.TaskWithSubTasks

/** Done out of total, with the derived numbers the progress UI shows. Plain Kotlin so it is easy to unit test. */
data class Progress(val done: Int, val total: Int) {
    val hasItems: Boolean get() = total > 0

    /** 0f when there is nothing to do, never above 1f. */
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

    /** Rounded down, so 199 of 200 never reads as 100%. */
    val percent: Int get() = if (total <= 0) 0 else (done.coerceIn(0, total) * 100L / total).toInt()

    val isComplete: Boolean get() = total > 0 && done >= total
}

/** Tasks done today; a task counts by its own checkbox, whatever its sub-tasks say. */
fun dayProgress(tasks: List<TaskWithSubTasks>): Progress =
    Progress(done = tasks.count { it.task.isCompleted }, total = tasks.size)

fun dayProgress(counts: DayCounts): Progress = Progress(done = counts.done, total = counts.total)

fun subTaskProgress(subTasks: List<SubTask>): Progress =
    Progress(done = subTasks.count { it.isCompleted }, total = subTasks.size)
