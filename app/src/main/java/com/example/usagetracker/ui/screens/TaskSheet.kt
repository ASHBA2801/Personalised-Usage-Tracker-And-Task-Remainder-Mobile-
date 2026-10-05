package com.example.usagetracker.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.usagetracker.data.FocusTask
import com.example.usagetracker.data.SubTaskInput
import com.example.usagetracker.data.TaskWithSubTasks
import com.example.usagetracker.importer.ImportLimits
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Test tags for the instrumented/Robolectric UI test. */
object TaskSheetTags {
    const val TITLE = "task_title"
    const val SAVE = "task_save"
    const val ADD_DEADLINE = "task_add_deadline"
    const val ADD_SUBTASK = "task_add_subtask"
    const val NOTES = "task_notes"
    fun subTask(index: Int) = "task_subtask_$index"
}

/** One editable sub-task row. [key] is a stable local identity for the list; [id] is the DB row (0 = new). */
data class SubDraft(val key: Int, val id: Long, val title: String) : Serializable

/** The fields of the sheet as a comparable value, so "unsaved changes" is just inequality with the start. */
private data class Snapshot(
    val title: String,
    val date: LocalDate,
    val deadline: Long?,
    val priority: Int,
    val minutes: String,
    val notes: String,
    val subs: List<Pair<Long, String>>,
)

private fun List<SubDraft>.normalized() = filter { it.title.isNotBlank() }.map { it.id to it.title.trim() }

/**
 * Add/edit sheet. [original] null means a new task on [defaultDate]. [onSave] gets the raw form; the
 * ViewModel cleans and persists it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSheet(
    original: TaskWithSubTasks?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (FocusTasksViewModel.TaskForm) -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val is24Hour = remember { DateFormat.is24HourFormat(context) }
    val max = FocusTasksViewModel.MAX_SUBTASKS_IN_UI

    val initial = remember(original) {
        val t = original?.task
        Snapshot(
            title = t?.title.orEmpty(),
            date = t?.let { LocalDate.parse(it.date) } ?: defaultDate,
            deadline = t?.deadline,
            priority = t?.priority ?: FocusTask.PRIORITY_NONE,
            minutes = t?.estimatedMinutes?.toString().orEmpty(),
            notes = t?.notes.orEmpty(),
            subs = original?.orderedSubTasks?.map { it.id to it.title }.orEmpty(),
        )
    }

    var title by rememberSaveable { mutableStateOf(initial.title) }
    var dateEpochDay by rememberSaveable { mutableStateOf(initial.date.toEpochDay()) }
    var deadline by rememberSaveable { mutableStateOf(initial.deadline) }
    var priority by rememberSaveable { mutableIntStateOf(initial.priority) }
    var minutes by rememberSaveable { mutableStateOf(initial.minutes) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var nextKey by rememberSaveable { mutableIntStateOf(initial.subs.size) }
    val subs = rememberSaveable {
        mutableStateListOf<SubDraft>().apply { initial.subs.forEachIndexed { i, (id, t) -> add(SubDraft(i, id, t)) } }
    }
    var focusKey by rememberSaveable { mutableStateOf<Int?>(null) }

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // 0 = closed, 1 = picking the deadline day, 2 = picking its time.
    var deadlineStep by rememberSaveable { mutableIntStateOf(0) }
    var pendingDeadlineDay by rememberSaveable { mutableStateOf(0L) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(dateEpochDay)
    val current = Snapshot(title.trim(), date, deadline, priority, minutes, notes.trim(), subs.normalized())
    val dirty = current != initial.copy(title = initial.title.trim(), notes = initial.notes.trim())
    val dirtyNow by rememberUpdatedState(dirty)

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && dirtyNow) {
                showDiscard = true
                false
            } else {
                true
            }
        },
    )
    val requestClose = { if (dirty) showDiscard = true else onDismiss() }

    ModalBottomSheet(onDismissRequest = requestClose, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = requestClose) { Text("Cancel") }
                Text(
                    if (original == null) "Add task" else "Edit task",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Button(
                    onClick = {
                        onSave(
                            FocusTasksViewModel.TaskForm(
                                id = original?.task?.id,
                                title = title,
                                date = date,
                                deadline = deadline,
                                priority = priority,
                                estimatedMinutes = minutes.toIntOrNull()?.takeIf { it > 0 },
                                notes = notes,
                                subTasks = subs.map { SubTaskInput(it.id, it.title) },
                            ),
                        )
                        onDismiss()
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.testTag(TaskSheetTags.SAVE),
                ) { Text("Save") }
            }

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(ImportLimits.MAX_TITLE) },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag(TaskSheetTags.TITLE),
                )

                Section("Date") {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(TaskFormatting.fullDate(date), modifier = Modifier.padding(start = 8.dp))
                    }
                }

                Section("Deadline (optional)") {
                    val nowMs = System.currentTimeMillis()
                    val dl = deadline
                    if (dl == null) {
                        OutlinedButton(
                            onClick = {
                                pendingDeadlineDay = date.toEpochDay()
                                deadlineStep = 1
                            },
                            modifier = Modifier.testTag(TaskSheetTags.ADD_DEADLINE),
                        ) { Text("Add deadline") }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    pendingDeadlineDay = Instant.ofEpochMilli(dl).atZone(zone).toLocalDate().toEpochDay()
                                    deadlineStep = 1
                                },
                                modifier = Modifier.testTag(TaskSheetTags.ADD_DEADLINE),
                            ) { Text(TaskFormatting.deadlineLabel(dl, nowMs, is24Hour, zone)) }
                            IconButton(onClick = { deadline = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove deadline")
                            }
                        }
                        if (dl < nowMs) {
                            Text(
                                "That time has already passed. You can still save it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TaskFormatting.QuickDeadline.entries.forEach { quick ->
                            AssistChip(
                                onClick = { deadline = TaskFormatting.quickDeadline(quick, LocalDate.now(zone), zone) },
                                label = { Text(quick.label) },
                            )
                        }
                    }
                }

                Section("Priority") {
                    val labels = listOf("None", "Low", "Medium", "High")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        labels.forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = priority == i,
                                onClick = { priority = i },
                                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                }

                OutlinedTextField(
                    value = minutes,
                    onValueChange = { v -> minutes = v.filter(Char::isDigit).take(4) },
                    label = { Text("Estimated minutes (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(ImportLimits.MAX_NOTES) },
                    label = { Text("Notes (optional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag(TaskSheetTags.NOTES),
                )

                Section("Sub-tasks") {
                    subs.forEachIndexed { index, sub ->
                        androidx.compose.runtime.key(sub.key) {
                            val isLast = index == subs.lastIndex
                            val focus = remember { FocusRequester() }
                            LaunchedEffect(focusKey) {
                                if (focusKey == sub.key) {
                                    focus.requestFocus()
                                    focusKey = null
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = sub.title,
                                    onValueChange = { v ->
                                        val at = subs.indexOfFirst { it.key == sub.key }
                                        if (at >= 0) subs[at] = sub.copy(title = v.take(ImportLimits.MAX_TITLE))
                                    },
                                    placeholder = { Text("Sub-task ${index + 1}") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = if (isLast) {
                                        KeyboardActions(onNext = {
                                            if (sub.title.isNotBlank() && subs.size < max) {
                                                subs.add(SubDraft(nextKey, 0, ""))
                                                focusKey = nextKey++
                                            }
                                        })
                                    } else {
                                        KeyboardActions.Default
                                    },
                                    modifier = Modifier.weight(1f).focusRequester(focus).testTag(TaskSheetTags.subTask(index)),
                                )
                                IconButton(onClick = { subs.removeAll { it.key == sub.key } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete sub-task ${index + 1}")
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            subs.add(SubDraft(nextKey, 0, ""))
                            focusKey = nextKey++
                        },
                        enabled = subs.size < max,
                        modifier = Modifier.testTag(TaskSheetTags.ADD_SUBTASK),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            if (subs.size < max) "Add sub-task" else "Limit of $max sub-tasks",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DayPickerDialog(initial = date, onDismiss = { showDatePicker = false }, onPick = {
            dateEpochDay = it.toEpochDay()
            showDatePicker = false
        })
    }
    when (deadlineStep) {
        1 -> DayPickerDialog(
            initial = LocalDate.ofEpochDay(pendingDeadlineDay),
            onDismiss = { deadlineStep = 0 },
            onPick = {
                pendingDeadlineDay = it.toEpochDay()
                deadlineStep = 2
            },
        )
        2 -> {
            val existing = deadline?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            TimePickerDialog(
                initial = existing?.takeIf { it != LocalTime.of(23, 59) } ?: LocalTime.of(18, 0),
                is24Hour = is24Hour,
                onDismiss = { deadlineStep = 0 },
                onPick = { time ->
                    deadline = TaskFormatting.deadlineMillis(LocalDate.ofEpochDay(pendingDeadlineDay), time, zone)
                    deadlineStep = 0
                },
            )
        }
    }
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to this task haven't been saved.") },
            confirmButton = { TextButton(onClick = { showDiscard = false; onDismiss() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Material3 date pickers speak UTC midnight, so convert both ways without the device zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: LocalTime, is24Hour: Boolean, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Deadline time") },
        text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { TimePicker(state = state) } },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
