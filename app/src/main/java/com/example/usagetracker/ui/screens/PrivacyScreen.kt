package com.example.usagetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Static explanation of what the app does with data. Keep in sync with what the app really does. */
@Composable
fun PrivacyScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Privacy", style = MaterialTheme.typography.headlineMedium)
        Section(
            "What is collected",
            "Which apps you use and for how long. For YouTube only, whether you were watching Shorts, " +
                "a regular video, or something else. Also the focus tasks you type in.",
        )
        Section(
            "What is not collected",
            "No screen content beyond that YouTube check, no personal files, no keystrokes, no messages.",
        )
        Section(
            "Where it is stored",
            "Only on this device, in a local database. It is excluded from Android backups. " +
                "Imported files are read once on this device and are not stored; only the tasks parsed from them are saved locally.",
        )
        Section(
            "What leaves the device",
            "Nothing. The app has no internet permission at all, so it cannot send data anywhere.",
        )
        Section("How long it is kept", "Usage data older than 3 months is deleted automatically.")
        Section(
            "Delete everything now",
            "Settings → \"Delete all data\" removes all usage history and focus tasks immediately.",
        )
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}
