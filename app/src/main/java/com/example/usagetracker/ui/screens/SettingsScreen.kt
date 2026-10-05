package com.example.usagetracker.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.usagetracker.service.DiagFileWriter
import com.example.usagetracker.service.DiagFormatter
import com.example.usagetracker.service.DiagPrefs
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.room.withTransaction
import com.example.usagetracker.data.AppDatabase
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.usagetracker.permissions.PermissionChecker
import com.example.usagetracker.endofday.EndOfDayScheduler
import com.example.usagetracker.settings.EndOfDayPrefs
import com.example.usagetracker.tracking.TrackerPrefs
import com.example.usagetracker.tracking.TrackerScheduler

@Composable
fun SettingsScreen(onOpenCategories: () -> Unit, onOpenPrivacy: () -> Unit, onOpenDetectionStatus: () -> Unit) {
    val context = LocalContext.current
    var usageGranted by remember { mutableStateOf(PermissionChecker.hasUsageAccess(context)) }
    var detectionGranted by remember { mutableStateOf(PermissionChecker.isContentDetectionEnabled(context)) }

    var trackingEnabled by remember { mutableStateOf(TrackerPrefs(context).enabled) }
    var confirmDelete by remember { mutableStateOf(false) }
    val diagPrefs = remember { DiagPrefs(context) }
    var diagUnlocked by remember { mutableStateOf(diagPrefs.unlocked) }
    var diagEnabled by remember { mutableStateOf(diagPrefs.enabled) }
    var headingTaps by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var endOfDayHour by remember { mutableStateOf(EndOfDayPrefs(context).endOfDayHour) }
    var endOfDayMinute by remember { mutableStateOf(EndOfDayPrefs(context).endOfDayMinute) }

    // Granting happens in system Settings, so re-check whenever we come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        usageGranted = PermissionChecker.hasUsageAccess(context)
        detectionGranted = PermissionChecker.isContentDetectionEnabled(context)
        // The worker switches itself off if permission was revoked, so re-read the stored state.
        trackingEnabled = TrackerPrefs(context).enabled
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Hidden: tap the heading 5 times to reveal the diagnostics section.
        Text(
            "Tracking",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (!diagUnlocked && ++headingTaps >= 5) {
                    diagPrefs.unlocked = true
                    diagUnlocked = true
                    Toast.makeText(context, "Diagnostics unlocked", Toast.LENGTH_SHORT).show()
                }
            },
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tracking", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = trackingStatus(usageGranted, detectionGranted, trackingEnabled),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = usageGranted && trackingEnabled,
                        enabled = usageGranted,
                        onCheckedChange = { on ->
                            if (on) TrackerScheduler.enable(context) else TrackerScheduler.disable(context)
                            trackingEnabled = on
                        },
                    )
                }
                if (!usageGranted) {
                    Text("Grant Usage Access below before you can turn tracking on.", style = MaterialTheme.typography.bodyMedium)
                } else if (!trackingEnabled && detectionGranted) {
                    // Apps can't switch off another app's accessibility permission, so point the user at it.
                    // Nothing is recorded meanwhile: the service checks this switch before doing anything.
                    Text(
                        "Also turn off 'Content Detection' under Accessibility settings to fully stop tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                        Text("Accessibility settings")
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Categories", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Decide which apps (and YouTube content types) count as Useful or Low-value.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenCategories) { Text("Edit categories") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("End of day", style = MaterialTheme.typography.titleMedium)
                Text(
                    "When the day's focus tasks are checked for carry-over.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "End of day: ${EndOfDayPrefs.format(endOfDayHour, endOfDayMinute)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                EndOfDayPrefs(context).setEndOfDay(hour, minute)
                                EndOfDayScheduler.schedule(context)
                                endOfDayHour = hour
                                endOfDayMinute = minute
                            },
                            endOfDayHour,
                            endOfDayMinute,
                            DateFormat.is24HourFormat(context),
                        ).show()
                    },
                ) { Text("Change time") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Data retention", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Usage data older than 3 months is automatically deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Privacy", style = MaterialTheme.typography.titleMedium)
                Text("What this app stores, and what it never does.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenPrivacy) { Text("Privacy") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Delete all data", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Permanently removes all usage history and focus tasks. Category rules are kept.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete all data") }
            }
        }
        if (diagUnlocked) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Shorts diagnostics", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "While on and YouTube is open, each scan writes the screen structure (no text) " +
                                    "to a small local file and to Logcat tag ShortsDiag. Turn off when done.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = diagEnabled,
                            onCheckedChange = { diagPrefs.enabled = it; diagEnabled = it },
                        )
                    }
                    Text("Add a marker to the file (then switch to YouTube):", style = MaterialTheme.typography.bodyMedium)
                    listOf(
                        "capture1-shorts-tab" to "1: Shorts tab",
                        "capture2-long-video" to "2: Long video",
                        "capture3-home-feed" to "3: Home feed",
                        "capture4-shorts-from-search-or-channel" to "4: Shorts via search/channel",
                    ).forEach { (name, label) ->
                        OutlinedButton(onClick = {
                            DiagFileWriter.get(context).append(DiagFormatter.marker(System.currentTimeMillis(), name))
                            Toast.makeText(context, "Marker: $name", Toast.LENGTH_SHORT).show()
                        }) { Text(label) }
                    }
                    Button(onClick = onOpenDetectionStatus) { Text("Detection status") }
                    TextButton(onClick = {
                        DiagFileWriter.get(context).clear()
                        Toast.makeText(context, "Diagnostics file cleared", Toast.LENGTH_SHORT).show()
                    }) { Text("Clear diagnostics file") }
                }
            }
        }
        Text("Permissions", style = MaterialTheme.typography.headlineMedium)
        PermissionCard(
            title = "Usage Access",
            description = "Lets the app see how long you spend in other apps.",
            granted = usageGranted,
            onGrant = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        )
        PermissionCard(
            title = "Content Detection",
            description = "Accessibility service that detects YouTube content. Find \"Focus Tracker Content Detection\" in the list and turn it on.",
            granted = detectionGranted,
            onGrant = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = { Text("This will permanently delete all usage history and focus tasks. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            val db = AppDatabase.getInstance(context)
                            db.withTransaction {
                                db.usageSessionDao().deleteAll()
                                db.focusTaskDao().deleteAll()
                            }
                            Toast.makeText(context, "All data deleted.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** e.g. "App usage: ON · Content detection: requires Accessibility permission". */
private fun trackingStatus(usageGranted: Boolean, detectionGranted: Boolean, trackingEnabled: Boolean): String {
    val usage = when {
        !usageGranted -> "requires Usage Access permission"
        trackingEnabled -> "ON"
        else -> "OFF"
    }
    val detection = when {
        !detectionGranted -> "requires Accessibility permission"
        trackingEnabled -> "ON"
        else -> "paused"
    }
    return "App usage: $usage · Content detection: $detection"
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (granted) "Granted" else "Not granted",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onGrant) { Text(if (granted) "Manage" else "Grant") }
        }
    }
}
