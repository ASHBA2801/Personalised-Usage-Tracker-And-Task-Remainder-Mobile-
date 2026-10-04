package com.example.usagetracker.ui.screens

import android.content.Intent
import android.provider.Settings
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
import com.example.usagetracker.notifications.Notifications
import com.example.usagetracker.permissions.PermissionChecker
import com.example.usagetracker.tracking.TrackerPrefs
import com.example.usagetracker.tracking.TrackerScheduler

@Composable
fun SettingsScreen(onOpenCategories: () -> Unit) {
    val context = LocalContext.current
    var usageGranted by remember { mutableStateOf(PermissionChecker.hasUsageAccess(context)) }
    var detectionGranted by remember { mutableStateOf(PermissionChecker.isContentDetectionEnabled(context)) }

    var trackingEnabled by remember { mutableStateOf(TrackerPrefs(context).enabled) }

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
        Text("Tracking", style = MaterialTheme.typography.headlineMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Track app usage", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (usageGranted) {
                            "Checks which apps you used about every 15 minutes. Turning this off stops the background job completely."
                        } else {
                            "Grant Usage Access below before you can turn tracking on."
                        },
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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Data retention", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Usage data older than 3 months is automatically deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        // TEMPORARY: remove once Phase 10b's real notifications are working.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Debug", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { Notifications.showTest(context) }) { Text("Send test notification") }
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
            description = "Accessibility service that detects YouTube content. Find \"UsageTracker Content Detection\" in the list and turn it on.",
            granted = detectionGranted,
            onGrant = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )
    }
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
