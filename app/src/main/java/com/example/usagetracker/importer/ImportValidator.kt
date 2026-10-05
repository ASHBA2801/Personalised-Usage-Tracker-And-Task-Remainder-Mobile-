package com.example.usagetracker.importer

import com.example.usagetracker.importer.ImportLimits.MAX_NOTES
import com.example.usagetracker.importer.ImportLimits.MAX_SUBTASKS
import com.example.usagetracker.importer.ImportLimits.MAX_TAG
import com.example.usagetracker.importer.ImportLimits.MAX_TAGS
import com.example.usagetracker.importer.ImportLimits.MAX_TASKS
import com.example.usagetracker.importer.ImportLimits.MAX_TITLE
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns parser output into tasks that are safe to store. File content is untrusted data: every value
 * is trimmed, stripped of control characters and capped; nothing in it is ever interpreted.
 */
class ImportValidator(
    private val today: LocalDate,
    private val zone: ZoneId,
) {
    fun validate(parsed: ParsedImport): ValidatedImport {
        val issues = parsed.issues.toMutableList()
        val raw = parsed.tasks
        if (raw.size > MAX_TASKS) {
            issues += warning(
                raw[MAX_TASKS].location,
                "Only $MAX_TASKS tasks can be imported at once; the remaining ${raw.size - MAX_TASKS} were ignored.",
            )
        }
        val tasks = raw.take(MAX_TASKS).mapNotNull { validateTask(it, issues) }
        if (raw.isEmpty() && issues.none { it.severity == Severity.ERROR }) {
            issues += ImportIssue(Severity.ERROR, "File", "No tasks were found in this file.")
        }
        return ValidatedImport(tasks, issues)
    }

    private fun validateTask(t: ImportedTask, issues: MutableList<ImportIssue>): ValidTask? {
        val loc = t.location
        val title = cleanTitle(t.title, loc, "Title", issues)
        if (title == null) {
            issues += ImportIssue(Severity.ERROR, loc, "Task has no title, so it was skipped.")
            return null
        }

        var date = today
        t.date?.takeIf { it.isNotBlank() }?.let { text ->
            val parsed = TaskValueParsing.parseDate(text)
            when {
                parsed == null -> issues += warning(loc, "Date \"${quote(text)}\" is not YYYY-MM-DD, so today is used.")
                parsed.isBefore(today) -> issues += warning(loc, "Date $parsed is in the past, so it was moved to today.")
                else -> date = parsed
            }
        }

        val priority = t.priority?.let { text ->
            TaskValueParsing.parsePriority(text) ?: run {
                issues += warning(loc, "Priority \"${quote(text)}\" is not high, medium or low, so it was left empty.")
                0
            }
        } ?: 0

        val estimate = t.estimatedMinutes?.takeIf { it.isNotBlank() }?.let { text ->
            TaskValueParsing.parseMinutes(text) ?: run {
                issues += warning(loc, "Estimated minutes \"${quote(text)}\" is not a positive whole number, so it was left empty.")
                null
            }
        }

        val rawSubs = t.subTasks
        if (rawSubs.size > MAX_SUBTASKS) {
            issues += warning(loc, "Only $MAX_SUBTASKS subtasks are allowed; the remaining ${rawSubs.size - MAX_SUBTASKS} were ignored.")
        }
        val subTasks = rawSubs.take(MAX_SUBTASKS).mapIndexedNotNull { i, s ->
            val subLoc = "$loc, subtask ${i + 1}"
            val subTitle = cleanTitle(s.title, subLoc, "Subtask title", issues)
            if (subTitle == null) {
                issues += warning(subLoc, "Subtask has no title, so it was skipped.")
                null
            } else {
                ValidSubTask(subTitle, s.completed, deadline(s.deadline, subLoc, issues))
            }
        }

        return ValidTask(
            location = loc,
            title = title,
            date = date,
            deadline = deadline(t.deadline, loc, issues),
            priority = priority,
            estimatedMinutes = estimate,
            tags = cleanTags(t.tags, loc, issues),
            notes = cleanNotes(t.notes, loc, issues),
            completed = t.completed,
            subTasks = subTasks,
        )
    }

    private fun deadline(text: String?, loc: String, issues: MutableList<ImportIssue>): Long? {
        if (text.isNullOrBlank()) return null
        return TaskValueParsing.parseDeadline(text, zone) ?: run {
            issues += warning(loc, "Deadline \"${quote(text)}\" is not YYYY-MM-DD or YYYY-MM-DD HH:mm, so it was left empty.")
            null
        }
    }

    private fun cleanTitle(text: String?, loc: String, label: String, issues: MutableList<ImportIssue>): String? {
        val clean = singleLine(text ?: return null)
        if (clean.isEmpty()) return null
        return truncate(clean, MAX_TITLE) { issues += warning(loc, "$label was longer than $MAX_TITLE characters and was shortened.") }
    }

    private fun cleanNotes(text: String?, loc: String, issues: MutableList<ImportIssue>): String? {
        val clean = Sanitizer.multiLine(text ?: return null)
        if (clean.isEmpty()) return null
        return truncate(clean, MAX_NOTES) { issues += warning(loc, "Notes were longer than $MAX_NOTES characters and were shortened.") }
    }

    private fun cleanTags(tags: List<String>, loc: String, issues: MutableList<ImportIssue>): List<String> {
        var shortened = false
        // Commas separate stored tags, so a comma inside one tag becomes a space.
        val clean = tags.asSequence()
            .map { singleLine(it.replace(',', ' ')) }
            .filter { it.isNotEmpty() }
            .map { truncate(it, MAX_TAG) { shortened = true } }
            .distinctBy { it.lowercase() }
            .toList()
        if (shortened) issues += warning(loc, "Tags longer than $MAX_TAG characters were shortened.")
        if (clean.size > MAX_TAGS) issues += warning(loc, "Only $MAX_TAGS tags are allowed; the rest were ignored.")
        return clean.take(MAX_TAGS)
    }

    private fun singleLine(text: String) = Sanitizer.singleLine(text)

    private fun truncate(text: String, max: Int, onTruncate: () -> Unit): String {
        if (text.length <= max) return text
        onTruncate()
        // Don't split a surrogate pair (e.g. an emoji) in half.
        val end = if (Character.isHighSurrogate(text[max - 1])) max - 1 else max
        return text.substring(0, end).trimEnd()
    }

    /** Short, single-line echo of a bad value for an issue message. */
    private fun quote(text: String): String = singleLine(text).let { if (it.length > 40) it.take(40) + "…" else it }

    private fun warning(loc: String, message: String) = ImportIssue(Severity.WARNING, loc, message)
}

/** Removes control and bidi-override characters so imported text can't hide or reorder what's displayed. */
object Sanitizer {
    private val WHITESPACE = Regex("\\s+")
    private val SPACES = Regex("[ \\t]+")
    private val BLANK_LINES = Regex("\n{3,}")

    private fun isUnsafe(c: Char): Boolean =
        (Character.isISOControl(c) && c != '\n' && c != '\t') ||
            c in '‪'..'‮' || c in '⁦'..'⁩' || c == ' ' || c == ' ' || c == '﻿'

    private fun strip(text: String): String = buildString(text.length) {
        for (c in text.replace("\r\n", "\n").replace('\r', '\n')) if (!isUnsafe(c)) append(c)
    }

    /** Trimmed, with every whitespace run (including newlines) collapsed to one space. */
    fun singleLine(text: String): String = strip(text).replace(WHITESPACE, " ").trim()

    /** Keeps line breaks (at most one blank line in a row), trims each line and the whole text. */
    fun multiLine(text: String): String =
        strip(text).lines().joinToString("\n") { it.replace(SPACES, " ").trim() }.replace(BLANK_LINES, "\n\n").trim()
}
