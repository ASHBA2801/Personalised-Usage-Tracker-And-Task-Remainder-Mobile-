package com.example.usagetracker.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.usagetracker.data.FocusTask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Display helpers shared by the task list and the import preview. */
object TaskFormatting {
    private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    private val SHORT_DAY = DateTimeFormatter.ofPattern("d MMM")
    private val TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val END_OF_DAY = LocalTime.of(23, 59)

    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> DAY.format(date)
    }

    fun fullDate(date: LocalDate): String = DAY.format(date)

    fun priorityLabel(priority: Int): String? = when (priority) {
        FocusTask.PRIORITY_HIGH -> "High"
        FocusTask.PRIORITY_MEDIUM -> "Medium"
        FocusTask.PRIORITY_LOW -> "Low"
        else -> null
    }

    /** "Due 20:00" when on [onDate], else "Due 7 Oct 20:00"; a date-only (23:59) deadline drops the time. */
    fun deadline(millis: Long, onDate: LocalDate?, zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(millis).atZone(zone)
        val date = at.toLocalDate()
        val time = at.toLocalTime().withSecond(0).withNano(0)
        val day = if (date == onDate) null else SHORT_DAY.format(date)
        val clock = if (time == END_OF_DAY) null else TIME.format(time)
        return "Due " + listOfNotNull(day, clock).joinToString(" ").ifEmpty { "today" }
    }

    fun estimate(minutes: Int): String =
        if (minutes < 60) "$minutes min" else "${minutes / 60} h" + (minutes % 60).let { if (it == 0) "" else " $it min" }
}

/** Small pill used for priority, deadline and tags; colors always come from the theme. */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun PriorityPill(priority: Int) {
    val label = TaskFormatting.priorityLabel(priority) ?: return
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (priority) {
        FocusTask.PRIORITY_HIGH -> scheme.errorContainer to scheme.onErrorContainer
        FocusTask.PRIORITY_MEDIUM -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    Pill(label, container, content)
}

@Composable
fun DeadlinePill(text: String, overdue: Boolean) {
    val scheme = MaterialTheme.colorScheme
    if (overdue) Pill(text, scheme.error, scheme.onError) else Pill(text, scheme.surfaceVariant, scheme.onSurfaceVariant)
}
