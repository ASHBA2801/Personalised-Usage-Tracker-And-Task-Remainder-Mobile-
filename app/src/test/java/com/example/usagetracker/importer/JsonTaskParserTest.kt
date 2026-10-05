package com.example.usagetracker.importer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.usagetracker.data.FocusTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Runs under Robolectric so it exercises Android's own org.json, whose error messages differ from desktop. */
@RunWith(AndroidJUnit4::class)
class JsonTaskParserTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 10, 5)

    private fun validate(json: String, on: LocalDate = today) = ImportValidator(on, zone).validate(JsonTaskParser.parse(json))
    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun parseError(json: String): ImportIssue = try {
        JsonTaskParser.parse(json)
        fail("Expected ImportException")
        error("unreachable")
    } catch (e: ImportException) {
        e.issue
    }

    @Test
    fun bundledTemplate_parsesToThreeTasks() {
        val r = validate(File("src/main/assets/templates/tasks_template.json").readText())
        assertTrue(r.issues.toString(), r.issues.isEmpty())
        assertEquals(3, r.tasks.size)
        val (a, b, c) = r.tasks
        assertEquals("Finish chapter 3 of the networking course", a.title)
        assertEquals(LocalDate.of(2026, 10, 5), a.date)
        assertEquals(millis(2026, 10, 5, 20, 0), a.deadline)
        assertEquals(FocusTask.PRIORITY_HIGH, a.priority)
        assertEquals(90, a.estimatedMinutes)
        assertEquals(listOf("study", "networking"), a.tags)
        assertEquals("Focus on TCP congestion control.", a.notes)
        assertEquals(
            listOf("Watch lectures 3.1 to 3.4", "Take notes on congestion control", "Solve the 5 practice questions"),
            a.subTasks.map { it.title },
        )
        assertEquals(listOf(null, millis(2026, 10, 5, 18, 0), null), a.subTasks.map { it.deadline })

        assertEquals(LocalDate.of(2026, 10, 6), b.date)
        assertEquals(FocusTask.PRIORITY_LOW, b.priority)
        assertNull(b.deadline)
        assertTrue(b.subTasks.isEmpty())

        assertEquals(LocalDate.of(2026, 10, 7), c.date)
        assertEquals(millis(2026, 10, 7, 23, 59), c.deadline)
        assertEquals(FocusTask.PRIORITY_MEDIUM, c.priority)
        assertEquals(listOf("work"), c.tags)
        assertEquals(3, c.subTasks.size)
    }

    @Test
    fun bundledTemplate_afterItsDates_movesPastTasksToTodayWithWarnings() {
        val later = LocalDate.of(2026, 10, 7)
        val r = validate(File("src/main/assets/templates/tasks_template.json").readText(), on = later)
        assertEquals(listOf(later, later, later), r.tasks.map { it.date })
        assertEquals(listOf("Task 1", "Task 2"), r.issues.map { it.location })
    }

    @Test
    fun bareArray_andStringTask() {
        val r = validate("""[{"title": "A"}, "B"]""")
        assertEquals(listOf("A", "B"), r.tasks.map { it.title })
    }

    @Test
    fun everyAlias_andCaseInsensitiveKeys() {
        val r = validate(
            """
            {"TASKS": [
              {"Task": "t1", "scheduled_date": "2026-10-06", "due": "2026-10-06", "estimate_minutes": "30",
               "description": "n1", "done": true, "sub_tasks": ["s1"]},
              {"NAME": "t2", "day": "2026-10-07", "due_date": "2026-10-07 10:00", "duration_minutes": 15,
               "details": "n2", "is_completed": false, "steps": [{"Title": "s2", "Done": true}]},
              {"title": "t3", "due_at": "2026-10-08T09:30", "checklist": [{"name": "s3", "due": "2026-10-08"}], "Priority": "URGENT"}
            ]}
            """,
        )
        assertTrue(r.issues.toString(), r.issues.isEmpty())
        val (t1, t2, t3) = r.tasks
        assertEquals(listOf("t1", "t2", "t3"), r.tasks.map { it.title })
        assertEquals(LocalDate.of(2026, 10, 6), t1.date)
        assertEquals(millis(2026, 10, 6, 23, 59), t1.deadline)
        assertEquals(30, t1.estimatedMinutes)
        assertEquals("n1", t1.notes)
        assertTrue(t1.completed)
        assertEquals("s1", t1.subTasks.single().title)
        assertEquals(LocalDate.of(2026, 10, 7), t2.date)
        assertEquals(millis(2026, 10, 7, 10, 0), t2.deadline)
        assertEquals(15, t2.estimatedMinutes)
        assertEquals("n2", t2.notes)
        assertFalse(t2.completed)
        assertTrue(t2.subTasks.single().completed)
        assertEquals(millis(2026, 10, 8, 9, 30), t3.deadline)
        assertEquals(millis(2026, 10, 8, 23, 59), t3.subTasks.single().deadline)
        assertEquals(FocusTask.PRIORITY_HIGH, t3.priority)
    }

    @Test
    fun underscoreAndUnknownKeys_ignored_tagsAsCommaString() {
        val r = validate("""{"_comment": "x", "tasks": [{"title": "A", "_title": "ignored", "color": "red", "tags": "a, b ,c"}]}""")
        assertTrue(r.issues.isEmpty())
        assertEquals(listOf("a", "b", "c"), r.tasks.single().tags)
    }

    @Test
    fun utf8Bom_isHandled() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + """[{"title": "Café ☕"}]""".toByteArray()
        val r = validate(String(bytes, Charsets.UTF_8))
        assertEquals("Café ☕", r.tasks.single().title)
    }

    @Test
    fun missingTitle_isErrorWithLocation() {
        val r = validate("""[{"title": "ok"}, {"date": "2026-10-06"}, {"title": ""}]""")
        assertEquals(listOf("ok"), r.tasks.map { it.title })
        assertEquals(listOf("Task 2", "Task 3"), r.issues.filter { it.severity == Severity.ERROR }.map { it.location })
    }

    @Test
    fun badAndPastDates_warn() {
        val r = validate("""[{"title": "a", "date": "05-10-2026"}, {"title": "b", "date": "2020-01-01"}, {"title": "c", "deadline": "soon"}]""")
        assertEquals(listOf(today, today, today), r.tasks.map { it.date })
        assertEquals(listOf("Task 1", "Task 2", "Task 3"), r.issues.map { it.location })
        assertTrue(r.issues.all { it.severity == Severity.WARNING })
    }

    @Test
    fun unknownPriority_becomesNoneWithWarning() {
        val r = validate("""[{"title": "a", "priority": "asap"}]""")
        assertEquals(FocusTask.PRIORITY_NONE, r.tasks.single().priority)
        assertEquals(Severity.WARNING, r.issues.single().severity)
    }

    @Test
    fun nestedSubtasks_ignoredWithWarning() {
        val r = validate("""[{"title": "a", "subtasks": [{"title": "s", "subtasks": [{"title": "deep"}]}]}]""")
        assertEquals(listOf("s"), r.tasks.single().subTasks.map { it.title })
        assertEquals("Task 1, subtask 1", r.issues.single().location)
    }

    @Test
    fun overLimitCounts_capped() {
        val tasks = (1..501).joinToString(",") { """{"title": "t$it"}""" }
        assertEquals(500, validate("[$tasks]").tasks.size)
        val subs = (1..101).joinToString(",") { "\"s$it\"" }
        assertEquals(100, validate("""[{"title": "a", "subtasks": [$subs]}]""").tasks.single().subTasks.size)
    }

    @Test
    fun truncatedJson_reportsLineAndColumn_withoutEchoingContent() {
        val issue = parseError("{\n  \"tasks\": [\n    {\"title\": \"secret plan\"")
        assertEquals(Severity.ERROR, issue.severity)
        assertTrue(issue.location, issue.location.matches(Regex("Line 3, column \\d+")))
        assertTrue(issue.message.contains("line 3"))
        assertFalse(issue.message.contains("secret"))
    }

    @Test
    fun syntaxError_midFile_pointsAtLine() {
        val issue = parseError("[\n {\"title\": \"a\"},\n {\"title\" \"b\"}\n]")
        assertTrue(issue.location, issue.location.startsWith("Line 3,"))
    }

    @Test
    fun trailingGarbage_isError() {
        val issue = parseError("[{\"title\": \"a\"}]\n oops")
        assertTrue(issue.location, issue.location.startsWith("Line 2,"))
    }

    @Test
    fun objectWithoutTasks_isError() {
        assertEquals("File", parseError("""{"items": []}""").location)
        assertEquals("File", parseError("42").location)
    }
}
