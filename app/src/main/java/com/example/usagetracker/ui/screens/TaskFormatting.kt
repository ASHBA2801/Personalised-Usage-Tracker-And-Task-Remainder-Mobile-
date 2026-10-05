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
import java.util.Locale

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

    /**
     * "Due 20:00" when on [onDate], else "Due 7 Oct 20:00". A date-only (23:59) deadline drops the time,
     * so one on [onDate] reads "Due end of day".
     */
    fun deadline(millis: Long, onDate: LocalDate?, zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(millis).atZone(zone)
        val date = at.toLocalDate()
        val time = at.toLocalTime().withSecond(0).withNano(0)
        val day = if (date == onDate) null else SHORT_DAY.format(date)
        val clock = if (time == END_OF_DAY) null else TIME.format(time)
        return "Due " + listOfNotNull(day, clock).joinToString(" ").ifEmpty { "end of day" }
    }

    /** The three one-tap deadlines in the task sheet. */
    enum class QuickDeadline(val label: String, val dayOffset: Long, val hour: Int) {
        TODAY_6PM("Today 6 PM", 0, 18),
        TONIGHT_9PM("Tonight 9 PM", 0, 21),
        TOMORROW_9AM("Tomorrow 9 AM", 1, 9),
    }

    fun quickDeadline(quick: QuickDeadline, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        deadlineMillis(today.plusDays(quick.dayOffset), LocalTime.of(quick.hour, 0), zone)

    /** Epoch millis of [date] at [time] in [zone]; the inverse of reading the value back in that zone. */
    fun deadlineMillis(date: LocalDate, time: LocalTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(time.withSecond(0).withNano(0)).atZone(zone).toInstant().toEpochMilli()

    /**
     * "Today 6:00 PM", "Tomorrow 9:00 AM", "12 Oct, 6:00 PM" (with the year when it isn't this year).
     * [is24Hour] follows the system clock setting. A date-only (23:59) deadline shows just the day.
     */
    fun deadlineLabel(
        millis: Long,
        nowMillis: Long,
        is24Hour: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val at = Instant.ofEpochMilli(millis).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val date = at.toLocalDate()
        val day = when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy", locale).format(date)
        }
        val time = at.toLocalTime().withSecond(0).withNano(0)
        if (time == END_OF_DAY) return day
        val clock = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale).format(time)
        return if (date == today || date == today.plusDays(1) || date == today.minusDays(1)) "$day $clock" else "$day, $clock"
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
