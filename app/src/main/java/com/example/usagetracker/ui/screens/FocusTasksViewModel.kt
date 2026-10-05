package com.example.usagetracker.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.usagetracker.data.AppDatabase
import com.example.usagetracker.data.FocusTask
import com.example.usagetracker.data.SubTask
import com.example.usagetracker.data.SubTaskInput
import com.example.usagetracker.data.TaskWithSubTasks
import com.example.usagetracker.importer.ImportLimits
import com.example.usagetracker.importer.Sanitizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class FocusTasksViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val dao = db.focusTaskDao()
    private val subTaskDao = db.subTaskDao()

    /** Emits today's date, and a new value after local midnight. */
    private val today = flow {
        while (true) {
            emit(LocalDate.now())
            val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
            delay((nextMidnight.toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(1_000))
        }
    }.distinctUntilChanged()

    /** The picked day, or null to follow today (so the screen rolls over at midnight as before). */
    private val pickedDate = MutableStateFlow<LocalDate?>(null)

    val todayDate: StateFlow<LocalDate> = today.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now())

    val selectedDate: StateFlow<LocalDate> = combine(pickedDate, today) { picked, now -> picked ?: now }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now())

    /** Null until the first DB emission, so the UI doesn't flash the empty state. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: StateFlow<List<TaskWithSubTasks>?> = selectedDate
        .flatMapLatest { dao.observeWithSubTasksForDate(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showPreviousDay() = shiftDay(-1)

    fun showNextDay() = shiftDay(1)

    fun showToday() {
        pickedDate.value = null
    }

    private fun shiftDay(days: Long) {
        val target = selectedDate.value.plusDays(days)
        pickedDate.value = if (target == LocalDate.now()) null else target
    }

    /** What the task sheet submits; [id] is null for a new task. Text is cleaned and blank sub-tasks dropped on save. */
    data class TaskForm(
        val id: Long?,
        val title: String,
        val date: LocalDate,
        val deadline: Long?,
        val priority: Int,
        val estimatedMinutes: Int?,
        val notes: String,
        val subTasks: List<SubTaskInput>,
    )

    /** Creates the task, or edits [original] and reconciles its sub-tasks, in one transaction. */
    fun saveTask(form: TaskForm, original: FocusTask?) {
        val title = cleanTitle(form.title) ?: return
        val subs = form.subTasks
            .mapNotNull { sub -> cleanTitle(sub.title)?.let { SubTaskInput(sub.id, it) } }
            .take(MAX_SUBTASKS_IN_UI)
        val notes = Sanitizer.multiLine(form.notes).take(ImportLimits.MAX_NOTES).ifEmpty { null }
        val base = original ?: FocusTask(title = title, date = form.date.toString(), createdAt = System.currentTimeMillis())
        val task = base.copy(
            title = title,
            date = form.date.toString(),
            deadline = form.deadline,
            priority = form.priority,
            estimatedMinutes = form.estimatedMinutes,
            notes = notes,
        )
        viewModelScope.launch {
            if (original == null) {
                dao.insertWithSubTasks(task, subs.mapIndexed { i, s -> SubTask(taskId = 0, title = s.title, sortOrder = i) })
            } else {
                dao.updateWithSubTasks(task, subs)
            }
        }
    }

    fun toggleComplete(task: FocusTask) {
        viewModelScope.launch { dao.setTaskCompleted(task.id, !task.isCompleted) }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { dao.deleteById(taskId) }
    }

    fun toggleSubTask(subTask: SubTask) {
        viewModelScope.launch { dao.setSubTaskCompleted(subTask, !subTask.isCompleted) }
    }

    fun addSubTask(task: FocusTask, title: String) {
        val clean = cleanTitle(title) ?: return
        viewModelScope.launch {
            db.withTransaction {
                subTaskDao.insert(SubTask(taskId = task.id, title = clean, sortOrder = subTaskDao.maxSortOrder(task.id) + 1))
                // A new open step means the task isn't done any more, matching "unchecking a subtask".
                if (task.isCompleted) dao.setCompletedFlag(task.id, false)
            }
        }
    }

    fun deleteSubTask(subTask: SubTask) {
        viewModelScope.launch { subTaskDao.deleteById(subTask.id) }
    }

    private fun cleanTitle(text: String): String? =
        Sanitizer.singleLine(text).take(ImportLimits.MAX_TITLE).takeIf { it.isNotEmpty() }

    companion object {
        const val MAX_SUBTASKS_IN_UI = 50
    }
}
