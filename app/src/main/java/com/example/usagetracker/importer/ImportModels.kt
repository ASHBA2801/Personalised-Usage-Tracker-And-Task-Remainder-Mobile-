package com.example.usagetracker.importer

import java.time.LocalDate

/*
 * Import pipeline: a parser (JSON or DOCX) turns the file into [ParsedImport] with raw text values,
 * then [ImportValidator] normalizes, sanitizes and caps them into [ValidatedImport].
 * Nothing here touches Android UI classes, so it is unit-tested on the JVM.
 */

enum class Severity { ERROR, WARNING }

/** [location] is human friendly: "Task 4", "Task 4, subtask 2", "Table row 6", or "Line 3, column 7". */
data class ImportIssue(val severity: Severity, val location: String, val message: String)

/** One subtask exactly as the file had it. Dates stay as text so both formats share one interpretation. */
data class ImportedSubTask(
    val title: String?,
    val completed: Boolean = false,
    val deadline: String? = null,
)

/** One task exactly as the file had it (aliases already resolved by the parser). */
data class ImportedTask(
    /** Where the task came from, e.g. "Task 4" or "Table row 6". */
    val location: String,
    val title: String?,
    val date: String? = null,
    val deadline: String? = null,
    val priority: String? = null,
    val estimatedMinutes: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val completed: Boolean = false,
    val subTasks: List<ImportedSubTask> = emptyList(),
)

data class ParsedImport(val tasks: List<ImportedTask>, val issues: List<ImportIssue>)

data class ValidSubTask(val title: String, val completed: Boolean, val deadline: Long?)

data class ValidTask(
    val location: String,
    val title: String,
    val date: LocalDate,
    val deadline: Long?,
    /** One of FocusTask.PRIORITY_*. */
    val priority: Int,
    val estimatedMinutes: Int?,
    val tags: List<String>,
    val notes: String?,
    val completed: Boolean,
    val subTasks: List<ValidSubTask>,
)

data class ValidatedImport(val tasks: List<ValidTask>, val issues: List<ImportIssue>) {
    val errorCount: Int get() = issues.count { it.severity == Severity.ERROR }
    val warningCount: Int get() = issues.count { it.severity == Severity.WARNING }
    val subTaskCount: Int get() = tasks.sumOf { it.subTasks.size }
}

/** Thrown by a parser when the whole file is unusable; [issue] says why and where. */
class ImportException(val issue: ImportIssue) : Exception(issue.message)

object ImportLimits {
    const val MAX_JSON_BYTES = 2L * 1024 * 1024
    const val MAX_DOCX_BYTES = 5L * 1024 * 1024
    const val MAX_DOCX_UNCOMPRESSED_BYTES = 20L * 1024 * 1024
    /** Compressed-to-uncompressed ratio above which a zip entry is treated as a zip bomb. */
    const val MAX_COMPRESSION_RATIO = 100
    const val MAX_TASKS = 500
    const val MAX_SUBTASKS = 100
    const val MAX_TITLE = 200
    const val MAX_NOTES = 2000
    const val MAX_TAGS = 10
    const val MAX_TAG = 30
}
