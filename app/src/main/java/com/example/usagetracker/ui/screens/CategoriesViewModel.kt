package com.example.usagetracker.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.usagetracker.data.AppDatabase
import com.example.usagetracker.data.CategoryRepository
import com.example.usagetracker.data.CategoryResolver
import com.example.usagetracker.data.TrackedApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [resolver] is the effective-category lookup; null until the first emission. */
data class CategoriesState(val apps: List<TrackedApp>, val resolver: CategoryResolver?)

class CategoriesViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repo = CategoryRepository(app)

    val state: StateFlow<CategoriesState> = combine(
        db.usageSessionDao().observeTrackedApps(),
        db.categoryRuleDao().observeAll(),
    ) { apps, rules -> CategoriesState(apps, CategoryResolver(rules)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesState(emptyList(), null))

    init {
        // Make sure defaults exist even if the user opens this screen before any tracking ran.
        viewModelScope.launch { repo.ensureSeeded() }
    }

    /** Writes the rule and rewrites history; the dashboard's live query picks the change up. */
    fun setCategory(packageName: String, contentTag: String?, category: String) {
        viewModelScope.launch { repo.setRule(packageName, contentTag, category) }
    }
}
