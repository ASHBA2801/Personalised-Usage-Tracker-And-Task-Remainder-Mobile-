package com.example.usagetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "usage_sessions", indices = [Index("startTime")])
data class UsageSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val category: String, // "USEFUL", "LOW_VALUE", "UNCATEGORIZED"
    val contentTag: String? = null,
    val startTime: Long,
    val endTime: Long,
)
