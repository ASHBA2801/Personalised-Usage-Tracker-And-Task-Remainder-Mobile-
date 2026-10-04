package com.example.usagetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "focus_tasks", indices = [Index("date")])
data class FocusTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: String, // yyyy-MM-dd
    val isCompleted: Boolean = false,
    val carriedOverFromDate: String? = null,
    val createdAt: Long,
)
