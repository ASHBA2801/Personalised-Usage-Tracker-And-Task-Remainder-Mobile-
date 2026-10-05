package com.example.usagetracker.importer

import com.example.usagetracker.data.FocusTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ImportValidatorTest {
    private val today = LocalDate.of(2026, 10, 5)
    private val zone = ZoneId.of("Asia/Kolkata")
    private val validator = ImportValidator(today, zone)

    private fun validate(vararg tasks: ImportedTask) = validator.validate(ParsedImport(tasks.toList(), emptyList()))
    private fun one(task: ImportedTask) = validate(task)
    private fun task(title: String? = "T", loc: String = "Task 1") = ImportedTask(location = loc, title = title)
    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun missingOrBlankTitle_isErrorAndSkipped() {
        val r = validate(task(null, "Task 1"), task("  \n\t ", "Task 2"), task("ok", "Task 3"))
        assertEquals(listOf("ok"), r.tasks.map { it.title })
        assertEquals(listOf("Task 1", "Task 2"), r.issues.filter { it.severity == Severity.ERROR }.map { it.location })
    }

    @Test
    fun title_trimmedControlCharsStrippedNewlinesCollapsed() {
        val r = one(task("  Buy\u0000 milk\n\nand\r\n  bread‮\u0007  "))
        assertEquals("Buy milk and bread", r.tasks.single().title)
        assertTrue(r.issues.isEmpty())
    }

    @Test
    fun longTitle_truncatedWithWarning() {
        val r = one(task("a".repeat(250)))
        assertEquals(200, r.tasks.single().title.length)
        assertEquals(Severity.WARNING, r.issues.single().severity)
    }

    @Test
    fun truncation_doesNotSplitEmoji() {
        val r = one(task("a".repeat(199) + "😀😀"))
        assertEquals("a".repeat(199), r.tasks.single().title)
    }

    @Test
    fun subtaskTitle_capAndBlankSkippedWithWarning() {
        val r = one(task().copy(subTasks = listOf(ImportedSubTask("s".repeat(300)), ImportedSubTask(" "), ImportedSubTask("ok"))))
        val subs = r.tasks.single().subTasks
        assertEquals(listOf(200, 2), subs.map { it.title.length })
        assertEquals(listOf("Task 1, subtask 1", "Task 1, subtask 2"), r.issues.map { it.location })
        assertTrue(r.issues.all { it.severity == Severity.WARNING })
    }

    @Test
    fun notes_keepLineBreaksAndCapAt2000() {
        val r = one(task().copy(notes = "  line 1  \r\n\n\n\nline\u0001 2 "))
        assertEquals("line 1\n\nline 2", r.tasks.single().notes)
        val long = one(task().copy(notes = "n".repeat(2500)))
        assertEquals(2000, long.tasks.single().notes!!.length)
        assertEquals(1, long.warningCount)
    }

    @Test
    fun tags_cleanedDedupedCapped() {
        val r = one(task().copy(tags = listOf(" a ", "A", "", "x".repeat(40), "b,c") + (1..12).map { "t$it" }))
        val tags = r.tasks.single().tags
        assertEquals(10, tags.size)
        assertEquals(listOf("a", "x".repeat(30), "b c"), tags.take(3))
        assertEquals(2, r.warningCount)
    }

    @Test
    fun overTaskLimit_keepsFirst500WithWarning() {
        val r = validate(*Array(510) { task("t$it", "Task ${it + 1}") })
        assertEquals(500, r.tasks.size)
        assertEquals("Task 501", r.issues.single().location)
    }

    @Test
    fun overSubtaskLimit_keepsFirst100WithWarning() {
        val r = one(task().copy(subTasks = List(130) { ImportedSubTask("s$it") }))
        assertEquals(100, r.tasks.single().subTasks.size)
        assertEquals(1, r.warningCount)
    }

    @Test
    fun dates_missingMeansToday_pastMovedToToday_futureKept_badWarned() {
        val r = validate(
            task("a", "Task 1"),
            task("b", "Task 2").copy(date = "2026-01-01"),
            task("c", "Task 3").copy(date = "2026-12-24"),
            task("d", "Task 4").copy(date = "24/12/2026"),
            task("e", "Task 5").copy(date = "2026-02-30"),
        )
        assertEquals(listOf(today, today, LocalDate.of(2026, 12, 24), today, today), r.tasks.map { it.date })
        assertEquals(listOf("Task 2", "Task 4", "Task 5"), r.issues.map { it.location })
        assertTrue(r.issues[0].message.contains("past"))
    }

    @Test
    fun deadlines_allFormats() {
        fun d(text: String) = one(task().copy(deadline = text)).tasks.single().deadline
        assertEquals(millis(2026, 10, 5, 23, 59), d("2026-10-05"))
        assertEquals(millis(2026, 10, 5, 20, 0), d("2026-10-05 20:00"))
        assertEquals(millis(2026, 10, 5, 20, 0), d("2026-10-05T20:00"))
        assertEquals(millis(2026, 10, 5, 20, 0), d("2026-10-05T20:00:00"))
        // 14:30 UTC is 20:00 in India.
        assertEquals(millis(2026, 10, 5, 20, 0), d("2026-10-05T14:30:00Z"))
        assertEquals(millis(2026, 10, 5, 20, 0), d("2026-10-05T16:30+02:00"))
    }

    @Test
    fun badDeadline_droppedWithWarning() {
        val r = one(task().copy(deadline = "tomorrow-ish", subTasks = listOf(ImportedSubTask("s", deadline = "nope"))))
        assertNull(r.tasks.single().deadline)
        assertNull(r.tasks.single().subTasks.single().deadline)
        assertEquals(listOf("Task 1", "Task 1, subtask 1"), r.issues.map { it.location }.sorted())
    }

    @Test
    fun priority_valuesAndUnknown() {
        fun p(text: String) = one(task().copy(priority = text))
        assertEquals(FocusTask.PRIORITY_HIGH, p("HIGH").tasks.single().priority)
        assertEquals(FocusTask.PRIORITY_HIGH, p("Urgent").tasks.single().priority)
        assertEquals(FocusTask.PRIORITY_MEDIUM, p("medium").tasks.single().priority)
        assertEquals(FocusTask.PRIORITY_LOW, p(" low ").tasks.single().priority)
        val unknown = p("critical!!")
        assertEquals(FocusTask.PRIORITY_NONE, unknown.tasks.single().priority)
        assertEquals(1, unknown.warningCount)
    }

    @Test
    fun estimate_positiveIntegerOnly() {
        fun e(text: String) = one(task().copy(estimatedMinutes = text))
        assertEquals(90, e("90").tasks.single().estimatedMinutes)
        assertEquals(90, e("90.0").tasks.single().estimatedMinutes)
        listOf("0", "-5", "12.5", "lots").forEach {
            val r = e(it)
            assertNull(r.tasks.single().estimatedMinutes)
            assertEquals(1, r.warningCount)
        }
    }

    @Test
    fun emptyFile_isError() {
        val r = validate()
        assertEquals(Severity.ERROR, r.issues.single().severity)
    }
}
