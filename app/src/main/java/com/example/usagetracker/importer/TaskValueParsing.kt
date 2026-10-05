package com.example.usagetracker.importer

import com.example.usagetracker.data.FocusTask
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/** Date, deadline and priority formats shared by the JSON and Word importers. */
object TaskValueParsing {
    private val DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    private val DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)

    /** A date-only deadline means the end of that day. */
    private val END_OF_DAY: LocalTime = LocalTime.of(23, 59)

    /** Strict `yyyy-MM-dd`, or null. */
    fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text.trim(), DATE)
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * `yyyy-MM-dd` (23:59 that day), `yyyy-MM-dd HH:mm`, or ISO-8601 with `T` and an optional offset
     * (converted to [zone]). Returns epoch millis, or null if unrecognized.
     */
    fun parseDeadline(text: String, zone: ZoneId): Long? {
        val t = text.trim()
        parseDate(t)?.let { return it.atTime(END_OF_DAY).atZone(zone).toInstant().toEpochMilli() }
        val local = try {
            LocalDateTime.parse(t, DATE_TIME)
        } catch (_: DateTimeParseException) {
            null
        } ?: if ('T' in t || 't' in t) parseIso(t.uppercase(), zone) else null
        return local?.atZone(zone)?.toInstant()?.toEpochMilli()
    }

    private fun parseIso(t: String, zone: ZoneId): LocalDateTime? = try {
        OffsetDateTime.parse(t).atZoneSameInstant(zone).toLocalDateTime()
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(t)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** Null means unrecognized; blank and "none" mean no priority. */
    fun parsePriority(text: String): Int? = when (text.trim().lowercase()) {
        "high", "urgent" -> FocusTask.PRIORITY_HIGH
        "medium" -> FocusTask.PRIORITY_MEDIUM
        "low" -> FocusTask.PRIORITY_LOW
        "", "none" -> FocusTask.PRIORITY_NONE
        else -> null
    }

    /** Positive whole number of minutes, or null. Accepts "90" and "90.0". */
    fun parseMinutes(text: String): Int? {
        val t = text.trim()
        val n = t.toIntOrNull() ?: t.toDoubleOrNull()?.takeIf { it == Math.floor(it) && it <= Int.MAX_VALUE }?.toInt()
        return n?.takeIf { it > 0 }
    }
}
