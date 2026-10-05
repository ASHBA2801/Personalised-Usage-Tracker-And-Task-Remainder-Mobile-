package com.example.usagetracker.importer

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Parses the JSON task format: `{"tasks": [...]}` or a bare `[...]`. Keys are case-insensitive, unknown
 * keys and keys starting with `_` are ignored. Values are returned as raw text for [ImportValidator].
 */
object JsonTaskParser {
    private val TITLE = listOf("title", "task", "name")
    private val DATE = listOf("date", "scheduled_date", "day")
    private val DEADLINE = listOf("deadline", "due", "due_date", "due_at")
    private val PRIORITY = listOf("priority")
    private val ESTIMATE = listOf("estimated_minutes", "estimate_minutes", "duration_minutes")
    private val TAGS = listOf("tags")
    private val NOTES = listOf("notes", "description", "details")
    private val COMPLETED = listOf("completed", "done", "is_completed")
    private val SUBTASKS = listOf("subtasks", "sub_tasks", "steps", "checklist")

    /** Android's org.json reports "... at character N of <input>"; the desktop one "[character N line M]". */
    private val ANDROID_POSITION = Regex(" at character (\\d+)")
    private val DESKTOP_POSITION = Regex("at (\\d+) \\[character")

    /** @throws ImportException if the text isn't valid JSON or has no task list. */
    fun parse(input: String): ParsedImport {
        val text = input.removePrefix("﻿")
        val root = try {
            val tokener = JSONTokener(text)
            val value = tokener.nextValue()
            if (tokener.more() && tokener.nextClean() != 0.toChar()) {
                throw syntaxError(text, positionIn(tokener.toString()), "Unexpected text after the end of the JSON")
            }
            value
        } catch (e: JSONException) {
            throw syntaxError(text, offsetOf(e), "This file is not valid JSON")
        }

        val issues = mutableListOf<ImportIssue>()
        val array = when (root) {
            is JSONArray -> root
            is JSONObject -> lowerKeys(root)["tasks"] as? JSONArray
                ?: throw ImportException(ImportIssue(Severity.ERROR, "File", "No \"tasks\" list was found in this JSON file."))
            else -> throw ImportException(ImportIssue(Severity.ERROR, "File", "The JSON file must contain a list of tasks."))
        }

        val tasks = (0 until array.length()).mapNotNull { i ->
            val loc = "Task ${i + 1}"
            when (val item = array.opt(i)) {
                is JSONObject -> parseTask(lowerKeys(item), loc, issues)
                // A bare string is a task with just a title, as for subtasks.
                is String -> ImportedTask(location = loc, title = item)
                else -> {
                    issues += ImportIssue(Severity.ERROR, loc, "Task is not a JSON object, so it was skipped.")
                    null
                }
            }
        }
        return ParsedImport(tasks, issues)
    }

    private fun parseTask(obj: Map<String, Any>, loc: String, issues: MutableList<ImportIssue>): ImportedTask {
        val subtasks = when (val raw = obj.first(SUBTASKS)) {
            null -> emptyList()
            is JSONArray -> (0 until raw.length()).mapNotNull { i -> parseSubTask(raw.opt(i), "$loc, subtask ${i + 1}", issues) }
            else -> {
                issues += warning(loc, "Subtasks must be a list, so they were ignored.")
                emptyList()
            }
        }
        return ImportedTask(
            location = loc,
            title = text(obj.first(TITLE)),
            date = text(obj.first(DATE)),
            deadline = text(obj.first(DEADLINE)),
            priority = text(obj.first(PRIORITY)),
            estimatedMinutes = text(obj.first(ESTIMATE)),
            tags = tags(obj.first(TAGS)),
            notes = text(obj.first(NOTES)),
            completed = bool(obj.first(COMPLETED), loc, issues),
            subTasks = subtasks,
        )
    }

    private fun parseSubTask(item: Any?, loc: String, issues: MutableList<ImportIssue>): ImportedSubTask? = when (item) {
        is String -> ImportedSubTask(title = item)
        is JSONObject -> {
            val obj = lowerKeys(item)
            if (obj.first(SUBTASKS) != null) {
                issues += warning(loc, "Subtasks inside subtasks are not supported and were ignored.")
            }
            ImportedSubTask(
                title = text(obj.first(TITLE)),
                completed = bool(obj.first(COMPLETED), loc, issues),
                deadline = text(obj.first(DEADLINE)),
            )
        }
        else -> {
            issues += warning(loc, "Subtask is not text or an object, so it was skipped.")
            null
        }
    }

    /** Lower-cased keys, minus `_` keys and nulls. The first spelling of a key wins. */
    private fun lowerKeys(obj: JSONObject): Map<String, Any> {
        val map = HashMap<String, Any>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.startsWith("_")) continue
            val value = obj.opt(key)
            if (value == null || value == JSONObject.NULL) continue
            map.putIfAbsent(key.trim().lowercase(), value)
        }
        return map
    }

    private fun Map<String, Any>.first(names: List<String>): Any? = names.firstNotNullOfOrNull { this[it] }

    /** Strings and numbers become text; objects and lists are not text. */
    private fun text(value: Any?): String? = when (value) {
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> null
    }

    private fun tags(value: Any?): List<String> = when (value) {
        is String -> value.split(',')
        is JSONArray -> (0 until value.length()).mapNotNull { text(value.opt(it)) }
        else -> emptyList()
    }

    private fun bool(value: Any?, loc: String, issues: MutableList<ImportIssue>): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is String -> when (value.trim().lowercase()) {
            "true", "yes", "1" -> true
            "false", "no", "0", "" -> false
            else -> false.also { issues += warning(loc, "Completed must be true or false, so false was used.") }
        }
        is Number -> value.toInt() != 0
        else -> false.also { issues += warning(loc, "Completed must be true or false, so false was used.") }
    }

    private fun offsetOf(e: JSONException): Int? = positionIn(e.message ?: return null)

    private fun positionIn(msg: String): Int? {
        val match = ANDROID_POSITION.find(msg) ?: DESKTOP_POSITION.find(msg)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /** Builds the error from the offset alone; the exception message echoes the file and is never shown. */
    private fun syntaxError(text: String, offset: Int?, message: String): ImportException {
        if (offset == null) return ImportException(ImportIssue(Severity.ERROR, "File", "$message."))
        val end = offset.coerceIn(0, text.length)
        var line = 1
        var lineStart = 0
        for (i in 0 until end) {
            if (text[i] == '\n') {
                line++
                lineStart = i + 1
            }
        }
        val column = end - lineStart + 1
        val hint = if (end >= text.trimEnd().length) " The file seems to end too early (is something cut off?)." else ""
        return ImportException(
            ImportIssue(Severity.ERROR, "Line $line, column $column", "$message near line $line, column $column.$hint"),
        )
    }

    private fun warning(loc: String, message: String) = ImportIssue(Severity.WARNING, loc, message)
}
