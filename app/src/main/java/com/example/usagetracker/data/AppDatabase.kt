package com.example.usagetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UsageSession::class, FocusTask::class, CategoryRule::class], version = 3)
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

        /**
         * v2 -> v3: speeds up range/group-by queries and the cleanup job. startTime (usage_sessions),
         * date (focus_tasks) and (packageName, contentTag) (category_rules) were already indexed, and
         * the latter's leading column already serves packageName lookups, so only these two are new.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_sessions_endTime` ON `usage_sessions` (`endTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_sessions_packageName` ON `usage_sessions` (`packageName`)")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "usage_tracker.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
