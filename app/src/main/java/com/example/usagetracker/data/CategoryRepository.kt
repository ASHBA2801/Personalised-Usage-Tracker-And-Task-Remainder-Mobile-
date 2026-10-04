package com.example.usagetracker.data

import android.content.Context
import androidx.room.withTransaction
import com.example.usagetracker.tracking.TrackerPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Rule storage, seeding, write-time lookup, and the one-off "recategorize everything" pass. */
class CategoryRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val rules = db.categoryRuleDao()
    private val sessions = db.usageSessionDao()
    private val prefs = TrackerPrefs(context)

    /** Seeds defaults on first use, then returns a resolver over the current rules. */
    suspend fun resolver(): CategoryResolver {
        ensureSeeded()
        return CategoryResolver(rules.getAll())
    }

    /** Inserts the defaults once and applies them to rows recorded before rules existed. */
    suspend fun ensureSeeded() {
        if (prefs.defaultsSeeded) return
        seedLock.withLock {
            if (prefs.defaultsSeeded) return
            db.withTransaction {
                rules.insertAll(DefaultCategoryRules.rules)
                recategorizeAll()
            }
            prefs.defaultsSeeded = true
        }
    }

    suspend fun setRule(packageName: String, contentTag: String?, category: String) {
        ensureSeeded()
        db.withTransaction {
            rules.upsert(packageName, contentTag, category)
            recategorizeAll()
        }
    }

    /** Rewrites every stored session's category from the current rules. Call after editing rules. */
    suspend fun recategorizeAll() {
        val resolver = CategoryResolver(rules.getAll())
        db.withTransaction {
            for (key in sessions.distinctKeys()) {
                sessions.updateCategory(key.packageName, key.contentTag, resolver.resolve(key.packageName, key.contentTag))
            }
        }
    }

    private companion object {
        // Worker and accessibility service each build their own repository; share one lock.
        val seedLock = Mutex()
    }
}
