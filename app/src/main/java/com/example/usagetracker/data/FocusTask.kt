package com.example.usagetracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "focus_tasks", indices = [Index("date"), Index("importBatchId")])
data class FocusTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: String, // yyyy-MM-dd
    val isCompleted: Boolean = false,
    val carriedOverFromDate: String? = null,
    val createdAt: Long,
    /** Epoch millis in the device zone. */
    val deadline: Long? = null,
    /** One of the PRIORITY_* constants. */
    @ColumnInfo(defaultValue = "0") val priority: Int = PRIORITY_NONE,
    val notes: String? = null,
    /** Display-only, joined with [TAG_SEPARATOR]; see [tagList]. */
    val tags: String? = null,
    val estimatedMinutes: Int? = null,
    /** Shared by every task created in one import, so the import can be undone as a unit. */
    val importBatchId: String? = null,
) {
    val tagList: List<String> get() = tags?.split(TAG_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    companion object {
        const val PRIORITY_NONE = 0
        const val PRIORITY_LOW = 1
        const val PRIORITY_MEDIUM = 2
        const val PRIORITY_HIGH = 3

        /** Tags never contain commas (import splits on them), so a comma is a safe separator. */
        const val TAG_SEPARATOR = ","

        fun joinTags(tags: List<String>): String? = tags.takeIf { it.isNotEmpty() }?.joinToString(TAG_SEPARATOR)
    }
}
