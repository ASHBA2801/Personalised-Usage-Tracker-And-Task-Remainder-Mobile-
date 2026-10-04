package com.example.usagetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object Category {
    const val USEFUL = "USEFUL"
    const val LOW_VALUE = "LOW_VALUE"
    const val UNCATEGORIZED = "UNCATEGORIZED"
}

/**
 * Maps an app (and optionally one content type within it) to a category. A null [contentTag]
 * applies to the whole app. UNCATEGORIZED rules are legal: they override a more general rule.
 */
@Entity(tableName = "category_rules", indices = [Index("packageName", "contentTag")])
data class CategoryRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val contentTag: String? = null,
    val category: String,
)
