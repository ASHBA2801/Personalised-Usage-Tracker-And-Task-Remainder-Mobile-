package com.example.usagetracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One checklist step of a [FocusTask]; deleted with its parent via the cascading foreign key. */
@Entity(
    tableName = "sub_tasks",
    foreignKeys = [
        ForeignKey(
            entity = FocusTask::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class SubTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val isCompleted: Boolean = false,
    /** Epoch millis in the device zone. */
    val deadline: Long? = null,
    val sortOrder: Int,
)
