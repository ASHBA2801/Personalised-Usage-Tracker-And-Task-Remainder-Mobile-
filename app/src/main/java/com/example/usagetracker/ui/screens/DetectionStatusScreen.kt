package com.example.usagetracker.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.usagetracker.service.DetectionStatus
import kotlinx.coroutines.delay

/**
 * Live view of what the YouTube classifier currently thinks. Shows signal names only, never screen
 * content. The service publishes here only while this screen is open, so it costs nothing otherwise.
 * Open YouTube (split-screen or by switching back and forth) to watch it update.
 */
@Composable
fun DetectionStatusScreen() {
    DisposableEffect(Unit) {
        DetectionStatus.watching = true
        onDispose { DetectionStatus.watching = false }
    }
    val snapshot by DetectionStatus.state.collectAsStateWithLifecycle()
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowElapsed = SystemClock.elapsedRealtime()
            delay(500)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Detection status", style = MaterialTheme.typography.headlineMedium)
        val s = snapshot
        if (s == null) {
            Text(
                "No scan yet. Tracking and the Content Detection service must be on; open YouTube and come back here.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Last scan sees", style = MaterialTheme.typography.labelLarge)
                Text(s.tag?.value ?: "YouTube not in front", style = MaterialTheme.typography.headlineSmall)
                Text("Recorded as", style = MaterialTheme.typography.labelLarge)
                Text(s.committed?.value ?: "(no open session)", style = MaterialTheme.typography.titleMedium)
                Text("Last scan", style = MaterialTheme.typography.labelLarge)
                val ago = (nowElapsed - s.scannedAtElapsed).coerceAtLeast(0) / 1000.0
                Text("%.1f s ago (scan #%d)".format(ago, s.scanCount), style = MaterialTheme.typography.titleMedium)
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Signals matched", style = MaterialTheme.typography.labelLarge)
                if (s.signals.isEmpty()) Text("none", style = MaterialTheme.typography.bodyMedium)
                s.signals.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
