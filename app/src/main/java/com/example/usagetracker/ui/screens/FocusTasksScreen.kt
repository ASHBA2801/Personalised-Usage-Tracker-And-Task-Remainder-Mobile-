package com.example.usagetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.usagetracker.data.FocusTask

/** Dialog target: a new task, or an existing one being edited. */
private sealed interface Editing {
    data object New : Editing
    data class Existing(val task: FocusTask) : Editing
}

@Composable
fun FocusTasksScreen(viewModel: FocusTasksViewModel = viewModel()) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Today's focus", style = MaterialTheme.typography.headlineMedium)
            val list = tasks
            when {
                list == null -> Unit
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tasks for today — add one below", style = MaterialTheme.typography.bodyLarge)
                }
                else -> LazyColumn(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
                ) {
                    items(list, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggle = { viewModel.toggleComplete(task.id) },
                            onEdit = { editing = Editing.Existing(task) },
                            onDelete = { viewModel.deleteTask(task.id) },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = Editing.New },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "Add task") }
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
                    is Editing.Existing -> viewModel.updateTitle(target.task.id, title)
                }
                editing = null
            },
        )
    }
}

@Composable
private fun TaskRow(task: FocusTask, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 8.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    modifier = Modifier.alpha(if (task.isCompleted) 0.6f else 1f),
                )
                if (task.carriedOverFromDate != null) {
                    Text(
                        "Carried over from ${task.carriedOverFromDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete task") }
        }
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
