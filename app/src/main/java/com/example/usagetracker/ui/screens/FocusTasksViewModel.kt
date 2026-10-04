package com.example.usagetracker.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.usagetracker.data.AppDatabase
import com.example.usagetracker.data.FocusTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class FocusTasksViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).focusTaskDao()

    /** Emits today's "yyyy-MM-dd", and a new value after local midnight. */
    private val today = flow {
        while (true) {
            emit(LocalDate.now().toString())
            val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
            delay((nextMidnight.toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(1_000))
        }
    }.distinctUntilChanged()

    /** Null until the first DB emission, so the UI doesn't flash the empty state. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: StateFlow<List<FocusTask>?> = today
        .flatMapLatest { dao.observeForDate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addTask(title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            dao.insert(
                FocusTask(
                    title = clean,
                    date = LocalDate.now().toString(),
                    isCompleted = false,
                    carriedOverFromDate = null,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun toggleComplete(taskId: Long) = modify(taskId) { it.copy(isCompleted = !it.isCompleted) }

    fun updateTitle(taskId: Long, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isNotEmpty()) modify(taskId) { it.copy(title = clean) }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { dao.deleteById(taskId) }
    }

    private fun modify(taskId: Long, change: (FocusTask) -> FocusTask) {
        val task = tasks.value?.firstOrNull { it.id == taskId } ?: return
        viewModelScope.launch { dao.update(change(task)) }
    }
}
