package com.example.usagetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UsageSession::class, FocusTask::class, CategoryRule::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun focusTaskDao(): FocusTaskDao
    abstract fun categoryRuleDao(): CategoryRuleDao

    companion object {
        /** v1 -> v2: adds category_rules. Existing usage rows are recategorized when defaults seed. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `category_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`packageName` TEXT NOT NULL, `contentTag` TEXT, `category` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_category_rules_packageName_contentTag` " +
                        "ON `category_rules` (`packageName`, `contentTag`)",
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "usage_tracker.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
