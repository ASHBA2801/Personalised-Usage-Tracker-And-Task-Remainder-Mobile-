package com.example.usagetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UsageSession::class, FocusTask::class, CategoryRule::class, SubTask::class],
    version = 4,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun focusTaskDao(): FocusTaskDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun subTaskDao(): SubTaskDao

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

        /**
         * v3 -> v4: task details for bulk import (deadline, priority, notes, tags, estimate, import batch)
         * and the sub_tasks checklist table. Existing tasks keep their data and get the column defaults.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `focus_tasks` ADD COLUMN `deadline` INTEGER")
                db.execSQL("ALTER TABLE `focus_tasks` ADD COLUMN `priority` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `focus_tasks` ADD COLUMN `notes` TEXT")
                db.execSQL("ALTER TABLE `focus_tasks` ADD COLUMN `tags` TEXT")
                db.execSQL("ALTER TABLE `focus_tasks` ADD COLUMN `estimatedMinutes` INTEGER")
                db.execSQL("ALTER TABLE `focus_tasks` ADD COLUMN `importBatchId` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_tasks_importBatchId` ON `focus_tasks` (`importBatchId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sub_tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`taskId` INTEGER NOT NULL, `title` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, " +
                        "`deadline` INTEGER, `sortOrder` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`taskId`) REFERENCES `focus_tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sub_tasks_taskId` ON `sub_tasks` (`taskId`)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "usage_tracker.db",
                ).addMigrations(*ALL_MIGRATIONS).build().also { instance = it }
            }
    }
}
