package com.example.usagetracker.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.usagetracker.data.FocusTask
import com.example.usagetracker.data.SubTask
import com.example.usagetracker.data.TaskWithSubTasks
import com.example.usagetracker.importer.ImportResult
import com.example.usagetracker.notifications.Notifications
import java.time.LocalDate

/** Dialog target: a new task, or an existing one being edited. */
private sealed interface Editing {
    data object New : Editing
    data class Existing(val task: FocusTask) : Editing
}

/**
 * @param onImport opens the file picker flow.
 * @param importResult a just-finished import to announce with an Undo snackbar; [onImportResultShown] consumes it.
 */
@Composable
fun FocusTasksScreen(
    onImport: () -> Unit,
    onOpenTemplates: () -> Unit,
    importResult: ImportResult?,
    onImportResultShown: () -> Unit,
    onUndoImport: (String) -> Unit,
    viewModel: FocusTasksViewModel = viewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val today by viewModel.todayDate.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var expanded by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Android 13+: ask once on first open. The system stops showing the dialog after repeated denials.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.hasPermission(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(importResult) {
        val result = importResult ?: return@LaunchedEffect
        // Consume first, so a recomposition or rotation never shows (or undoes) it twice.
        onImportResultShown()
        val n = result.insertedCount
        val outcome = snackbar.showSnackbar(
            message = "Imported $n task${if (n == 1) "" else "s"}",
            actionLabel = "Undo",
            duration = SnackbarDuration.Long, // about 10 seconds
        )
        if (outcome == SnackbarResult.ActionPerformed) onUndoImport(result.batchId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Focus", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onImport) { Text("Import") }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Templates and AI prompt") },
                            onClick = {
                                menuOpen = false
                                onOpenTemplates()
                            },
                        )
                    }
                }
            }
            DayNavigator(
                date = selectedDate,
                today = today,
                onPrevious = viewModel::showPreviousDay,
                onNext = viewModel::showNextDay,
                onToday = viewModel::showToday,
            )
            val list = tasks
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            if (selectedDate == today) "Nothing on your list yet" else "Nothing planned for this day",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "Tap + to add your first task",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { editing = Editing.New }.padding(8.dp),
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(list, key = { it.task.id }) { item ->
                        val id = item.task.id
                        TaskRow(
                            item = item,
                            expanded = id in expanded,
                            onExpandToggle = { expanded = if (id in expanded) expanded - id else expanded + id },
                            onToggle = { viewModel.toggleComplete(item.task) },
                            onEdit = { editing = Editing.Existing(item.task) },
                            onDelete = { viewModel.deleteTask(id) },
                            onToggleSubTask = viewModel::toggleSubTask,
                            onAddSubTask = { viewModel.addSubTask(item.task, it) },
                            onDeleteSubTask = viewModel::deleteSubTask,
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = Editing.New },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "Add task") }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    when (val target = editing) {
        null -> Unit
        else -> TaskDialog(
            initial = (target as? Editing.Existing)?.task?.title.orEmpty(),
            isNew = target is Editing.New,
            onDismiss = { editing = null },
            onConfirm = { title ->
                when (target) {
                    Editing.New -> viewModel.addTask(title)
                    is Editing.Existing -> viewModel.updateTitle(target.task, title)
                }
                editing = null
            },
        )
    }
}

@Composable
private fun DayNavigator(date: LocalDate, today: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(TaskFormatting.dayLabel(date, today), style = MaterialTheme.typography.titleMedium)
            if (date in today.minusDays(1)..today.plusDays(1)) {
                Text(
                    TaskFormatting.fullDate(date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
        }
        TextButton(onClick = onToday, enabled = date != today) { Text("Today") }
    }
}

@Composable
private fun TaskRow(
    item: TaskWithSubTasks,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleSubTask: (SubTask) -> Unit,
    onAddSubTask: (String) -> Unit,
    onDeleteSubTask: (SubTask) -> Unit,
) {
    val task = item.task
    val subTasks = item.orderedSubTasks
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.clickable(onClick = onExpandToggle).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    modifier = Modifier.alpha(if (task.isCompleted) 0.6f else 1f),
                )
                TaskMeta(task, subTasks)
                if (task.carriedOverFromDate != null) {
                    Text(
                        "Carried over from ${task.carriedOverFromDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete task") }
        }
        if (expanded) {
            HorizontalDivider()
            TaskDetails(task, subTasks, onEdit, onToggleSubTask, onAddSubTask, onDeleteSubTask)
        }
    }
}

/** Priority, deadline and "done/total" on one wrapping line; nothing when the task has none. */
@Composable
private fun TaskMeta(task: FocusTask, subTasks: List<SubTask>) {
    val hasDeadline = task.deadline != null
    if (task.priority == FocusTask.PRIORITY_NONE && !hasDeadline && subTasks.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PriorityPill(task.priority)
        task.deadline?.let { deadline ->
            val overdue = !task.isCompleted && deadline < System.currentTimeMillis()
            DeadlinePill(TaskFormatting.deadline(deadline, LocalDate.parse(task.date)), overdue)
        }
        if (subTasks.isNotEmpty()) {
            Text(
                "${subTasks.count { it.isCompleted }}/${subTasks.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun TaskDetails(
    task: FocusTask,
    subTasks: List<SubTask>,
    onEdit: () -> Unit,
    onToggleSubTask: (SubTask) -> Unit,
    onAddSubTask: (String) -> Unit,
    onDeleteSubTask: (SubTask) -> Unit,
) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        subTasks.forEach { sub ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sub.isCompleted, onCheckedChange = { onToggleSubTask(sub) })
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sub.title,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else null,
                        modifier = Modifier.alpha(if (sub.isCompleted) 0.6f else 1f),
                    )
                    sub.deadline?.let {
                        Text(
                            TaskFormatting.deadline(it, LocalDate.parse(task.date)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!sub.isCompleted && it < System.currentTimeMillis()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                IconButton(onClick = { onDeleteSubTask(sub) }) { Icon(Icons.Default.Close, contentDescription = "Delete subtask") }
            }
        }
        AddSubTaskField(onAddSubTask)
        task.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
        val tags = task.tagList
        if (tags.isNotEmpty() || task.estimatedMinutes != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.forEach { Pill("#$it", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
                task.estimatedMinutes?.let {
                    Pill("≈ ${TaskFormatting.estimate(it)}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Edit title", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun AddSubTaskField(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) onAdd(text)
        text = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text("Add a subtask", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = submit, enabled = text.isNotBlank()) { Icon(Icons.Default.Add, contentDescription = "Add subtask") }
    }
}

@Composable
private fun TaskDialog(initial: String, isNew: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add task" else "Edit task") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Title") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(if (isNew) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
