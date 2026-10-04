package com.example.usagetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.usagetracker.tracking.TrackerPrefs

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var trackingEnabled by remember { mutableStateOf(TrackerPrefs(context).enabled) }
    var everTracked by remember { mutableStateOf(TrackerPrefs(context).lastPolledTimestamp != null) }

    // Tracking can be switched in Settings (or by the worker on permission loss); re-read on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val prefs = TrackerPrefs(context)
        trackingEnabled = prefs.enabled
        everTracked = prefs.lastPolledTimestamp != null
        viewModel.refresh()
    }

    val report = state.report
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PeriodToggle(state.period, viewModel::selectPeriod) }

        when {
            report == null -> Unit
            report.isEmpty -> item {
                EmptyState(state.period, trackingEnabled, everTracked, onOpenSettings)
            }
            else -> {
                if (!trackingEnabled) item { TrackingOffBanner(onOpenSettings) }
                item { SummaryCard(report) }
                item { Text("By app", style = MaterialTheme.typography.titleMedium) }
                items(report.apps, key = { it.packageName }) { AppRow(it, report.totalMs) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodToggle(selected: Period, onSelect: (Period) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Period.entries.forEachIndexed { i, p ->
            SegmentedButton(
                selected = p == selected,
                onClick = { onSelect(p) },
                shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
            ) { Text(p.label) }
        }
    }
}

@Composable
private fun SummaryCard(report: UsageReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Total screen time", style = MaterialTheme.typography.labelLarge)
            Text(formatDuration(report.totalMs), style = MaterialTheme.typography.displaySmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Bucket("Useful", report.usefulMs, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Bucket("Low-value", report.lowValueMs, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                Bucket("Uncategorized", report.uncategorizedMs, MaterialTheme.colorScheme.outline, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Bucket(label: String, ms: Long, color: Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(formatDuration(ms), style = MaterialTheme.typography.headlineSmall, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AppRow(app: AppUsage, periodTotalMs: Long) {
    val fraction = if (periodTotalMs > 0) app.totalMs.toFloat() / periodTotalMs else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(app.appName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                "${formatDuration(app.totalMs)} · ${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        app.youTube?.let { yt ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SubTime("Shorts", yt.shortsMs)
                SubTime("Video", yt.videoMs)
                SubTime("Other", yt.otherMs)
            }
        }
    }
}

@Composable
private fun SubTime(label: String, ms: Long) {
    Text("$label ${formatDuration(ms)}", style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun TrackingOffBanner(onOpenSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tracking is off. New usage isn't being recorded.", modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
    }
}

@Composable
private fun EmptyState(period: Period, trackingEnabled: Boolean, everTracked: Boolean, onOpenSettings: () -> Unit) {
    val (title, body, showSettings) = when {
        !trackingEnabled -> Triple(
            "Tracking is off",
            "Turn on \"Track app usage\" in Settings to start recording.",
            true,
        )
        !everTracked -> Triple(
            "No usage recorded yet",
            "Tracking is on. The first check runs within about 15 minutes.",
            false,
        )
        else -> Triple(
            "No usage recorded ${if (period == Period.TODAY) "today" else "this week"}",
            "Usage shows up here after the next background check.",
            false,
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (showSettings) Button(onClick = onOpenSettings) { Text("Open Settings") }
    }
}

/** "2h 05m", "14m", or "<1m" for anything under a minute; "0m" for none. */
internal fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        ms <= 0 -> "0m"
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        else -> "%dh %02dm".format(minutes / 60, minutes % 60)
    }
}
