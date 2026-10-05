package com.example.usagetracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.usagetracker.importer.ImportIssue
import com.example.usagetracker.importer.Severity
import com.example.usagetracker.importer.ValidTask
import java.time.LocalDate

@Composable
fun ImportPreviewScreen(viewModel: ImportViewModel, onBack: () -> Unit, onImported: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val skipDuplicates by viewModel.skipDuplicates.collectAsStateWithLifecycle()
    val back = {
        viewModel.cancel()
        onBack()
    }
    BackHandler(onBack = back)
    LaunchedEffect(state) {
        if (state == ImportUiState.Done) {
            viewModel.cancel()
            onImported()
        }
    }

    when (val s = state) {
        ImportUiState.Loading, ImportUiState.Saving, ImportUiState.Done -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(if (s == ImportUiState.Loading) "Reading file…" else "Importing…")
            }
        }
        ImportUiState.Idle -> Message("Nothing to preview", "Pick a file with Import on the Focus screen.", back)
        is ImportUiState.Failed -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Can't import this file", style = MaterialTheme.typography.headlineMedium)
            IssueCard(s.issue)
            Text("Nothing was changed.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = back) { Text("Back") }
        }
        is ImportUiState.Ready -> Preview(s, skipDuplicates, viewModel::setSkipDuplicates, back, viewModel::confirm)
    }
}

@Composable
private fun Message(title: String, body: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun Preview(
    state: ImportUiState.Ready,
    skipDuplicates: Boolean,
    onSkipDuplicatesChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    val result = state.result
    val tasks = result.tasks
    val nothingValid = tasks.isEmpty()
    var issuesOpen by rememberSaveable { mutableStateOf(nothingValid) }
    val skipped = if (skipDuplicates) state.duplicates.size else 0
    val toImport = tasks.size - skipped
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Import preview", style = MaterialTheme.typography.headlineMedium) }
            item {
                Text(
                    "${plural(tasks.size, "task")} · ${plural(result.subTaskCount, "subtask")} · " +
                        "${plural(result.warningCount, "warning")} · ${plural(result.errorCount, "error")}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (nothingValid) {
                item {
                    Text(
                        "No valid tasks were found, so nothing can be imported.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Skip duplicates", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (state.duplicates.isEmpty()) "No task matches one already in your list."
                                else "${plural(state.duplicates.size, "task")} already in your list (same title and day).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = skipDuplicates, onCheckedChange = onSkipDuplicatesChange)
                    }
                }
            }
            if (result.issues.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { issuesOpen = !issuesOpen }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (issuesOpen) "Hide issues" else "Show ${plural(result.issues.size, "issue")}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(if (issuesOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                }
                if (issuesOpen) {
                    // Errors first; each list is already in file order.
                    val sorted = result.issues.sortedBy { if (it.severity == Severity.ERROR) 0 else 1 }
                    itemsIndexed(sorted) { _, issue -> IssueCard(issue) }
                }
            }
            itemsIndexed(tasks) { i, task ->
                PreviewTask(task, today, duplicateSkipped = skipDuplicates && i in state.duplicates)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = onBack) { Text(if (nothingValid) "Back" else "Cancel") }
            if (!nothingValid) {
                Button(onClick = onConfirm, enabled = toImport > 0) {
                    Text(
                        when {
                            toImport == 0 -> "Nothing new to import"
                            result.errorCount > 0 -> "Import valid tasks only"
                            else -> "Import ${plural(toImport, "task")}"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewTask(task: ValidTask, today: LocalDate, duplicateSkipped: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = if (duplicateSkipped) TextDecoration.LineThrough else null,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(TaskFormatting.dayLabel(task.date, today), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                PriorityPill(task.priority)
                task.deadline?.let { DeadlinePill(TaskFormatting.deadline(it, task.date), overdue = false) }
                if (task.completed) Pill("Done", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (duplicateSkipped) {
                Text(
                    "Already in your list, will be skipped",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            task.subTasks.forEach { sub ->
                Text(
                    (if (sub.completed) "☑ " else "☐ ") + sub.title +
                        (sub.deadline?.let { " · " + TaskFormatting.deadline(it, task.date) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun IssueCard(issue: ImportIssue) {
    val error = issue.severity == Severity.ERROR
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (error) scheme.errorContainer else scheme.surfaceVariant,
            contentColor = if (error) scheme.onErrorContainer else scheme.onSurfaceVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                (if (error) "Error · " else "Warning · ") + issue.location,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(issue.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"
