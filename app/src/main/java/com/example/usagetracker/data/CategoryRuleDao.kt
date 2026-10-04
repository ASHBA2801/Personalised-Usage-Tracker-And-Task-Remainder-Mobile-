package com.example.usagetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules")
    suspend fun getAll(): List<CategoryRule>

    @Query("SELECT * FROM category_rules")
    fun observeAll(): Flow<List<CategoryRule>>

    @Insert
    suspend fun insert(rule: CategoryRule): Long

    @Insert
    suspend fun insertAll(rules: List<CategoryRule>)

    /** `IS` so a null [contentTag] matches the package-wide rule. */
    @Query("DELETE FROM category_rules WHERE packageName = :packageName AND contentTag IS :contentTag")
    suspend fun delete(packageName: String, contentTag: String?)

    /** Replace-by-key: there is no unique index because SQLite treats NULL tags as distinct. */
    @Transaction
    suspend fun upsert(packageName: String, contentTag: String?, category: String) {
        delete(packageName, contentTag)
        insert(CategoryRule(packageName = packageName, contentTag = contentTag, category = category))
    }
}
