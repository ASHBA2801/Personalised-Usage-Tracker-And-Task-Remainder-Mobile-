package com.example.usagetracker.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

object ProgressTags {
    const val DAILY_CARD = "daily_progress_card"
}

/**
 * "3 of 7 tasks done" with an animated bar and percentage. Draws nothing for a day without tasks; callers
 * decide what to show instead. [allDoneMessage] replaces nothing, it is added once everything is ticked.
 */
@Composable
fun DailyProgressCard(
    progress: Progress,
    heading: String?,
    allDoneMessage: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (!progress.hasItems) return
    val animated by animateFloatAsState(progress.fraction, label = "daily progress")
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            heading?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${progress.done} of ${progress.total} task${if (progress.total == 1) "" else "s"} done",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text("${progress.percent}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
            if (progress.isComplete) {
                Text(allDoneMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    val tagged = modifier.fillMaxWidth().testTag(ProgressTags.DAILY_CARD)
    if (onClick != null) Card(onClick = onClick, modifier = tagged) { content() } else Card(modifier = tagged) { content() }
}
