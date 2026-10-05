package com.example.usagetracker.importer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.usagetracker.data.FocusTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Runs under Robolectric for Android's XmlPullParser implementation. */
@RunWith(AndroidJUnit4::class)
class DocxTaskParserTest {
    private val today = LocalDate.of(2026, 10, 5)
    private val zone = ZoneId.of("Europe/Berlin")

    private fun validate(bytes: ByteArray) = ImportValidator(today, zone).validate(DocxTaskParser.parse(bytes))

    private fun parseError(bytes: ByteArray): ImportIssue = try {
        DocxTaskParser.parse(bytes)
        fail("Expected ImportException")
        error("unreachable")
    } catch (e: ImportException) {
        e.issue
    }

    // --- fixture builders ---------------------------------------------------------------------

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.setLevel(Deflater.BEST_COMPRESSION)
            entries.forEach { (name, data) ->
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")

    /** A cell holds one paragraph per line. */
    private fun cell(vararg lines: String, span: Int = 1) = buildString {
        append("<w:tc>")
        if (span > 1) append("<w:tcPr><w:gridSpan w:val=\"$span\"/></w:tcPr>")
        if (lines.isEmpty()) append("<w:p/>")
        lines.forEach { append("<w:p><w:r><w:t xml:space=\"preserve\">${esc(it)}</w:t></w:r></w:p>") }
        append("</w:tc>")
    }

    private fun row(vararg cells: String) = "<w:tr>${cells.joinToString("")}</w:tr>"
    private fun textRow(vararg values: String) = row(*values.map { cell(it) }.toTypedArray())
    private fun table(vararg rows: String) = "<w:tbl>${rows.joinToString("")}</w:tbl>"

    private fun documentXml(body: String, prolog: String = "") =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>$prolog""" +
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>"""

    private fun docx(body: String) = zip("[Content_Types].xml" to "<Types/>".toByteArray(), "word/document.xml" to documentXml(body).toByteArray())

    private fun resource(name: String) = File("src/test/resources/docx/$name").readBytes()

    // --- tests ----------------------------------------------------------------------------------

    @Test
    fun bundledTemplate_matchesJsonTemplate() {
        val word = validate(File("src/main/assets/templates/tasks_template.docx").readBytes())
        val json = ImportValidator(today, zone).validate(JsonTaskParser.parse(File("src/main/assets/templates/tasks_template.json").readText()))
        assertTrue(word.issues.toString(), word.issues.isEmpty())
        // Word has no per-subtask deadline column, so compare everything else.
        fun ValidTask.comparable() = copy(location = "", subTasks = subTasks.map { it.copy(deadline = null) })
        assertEquals(json.tasks.map { it.comparable() }, word.tasks.map { it.comparable() })
        assertEquals(listOf("Table row 2", "Table row 3", "Table row 4"), word.tasks.map { it.location })
    }

    @Test
    fun normalTable() {
        val r = validate(
            docx(
                "<w:p><w:r><w:t>Intro</w:t></w:r></w:p>" +
                    table(
                        textRow("Task", "Date", "Deadline", "Priority", "Estimated Minutes", "Tags", "Notes", "Sub-tasks"),
                        row(
                            cell("Write report"), cell("2026-10-06"), cell("2026-10-06 17:00"), cell("Medium"), cell("45"),
                            cell("work, writing"), cell("Line one", "Line two"), cell("Outline", "Draft"),
                        ),
                    ),
            ),
        )
        assertTrue(r.issues.toString(), r.issues.isEmpty())
        val t = r.tasks.single()
        assertEquals("Write report", t.title)
        assertEquals(LocalDate.of(2026, 10, 6), t.date)
        assertEquals(FocusTask.PRIORITY_MEDIUM, t.priority)
        assertEquals(45, t.estimatedMinutes)
        assertEquals(listOf("work", "writing"), t.tags)
        assertEquals("Line one\nLine two", t.notes)
        assertEquals(listOf("Outline", "Draft"), t.subTasks.map { it.title })
    }

    @Test
    fun reorderedColumns_bullets_checkboxes_emptyRows_fromCommittedFixture() {
        val r = validate(resource("reordered_columns.docx"))
        assertEquals(listOf("Reordered task", "Second task"), r.tasks.map { it.title })
        val first = r.tasks[0]
        assertEquals(FocusTask.PRIORITY_HIGH, first.priority)
        assertEquals(LocalDate.of(2026, 10, 6), first.date)
        assertEquals(listOf("a", "b"), first.tags)
        assertEquals(listOf("First step", "Second step", "Third step", "Fourth step"), first.subTasks.map { it.title })
        assertEquals(listOf(true, false, false, true), first.subTasks.map { it.completed })
        assertEquals(listOf("only step"), r.tasks[1].subTasks.map { it.title })
        assertEquals(FocusTask.PRIORITY_HIGH, r.tasks[1].priority)
        // Row 5 has a date and priority but no title: an error pointing at that row. Row 3 is blank and skipped.
        val error = r.issues.single { it.severity == Severity.ERROR }
        assertEquals("Table row 5", error.location)
    }

    @Test
    fun subtaskLineCleanup() {
        fun s(line: String) = DocxTaskParser.subTask(line)
        assertEquals(ImportedSubTask("Done thing", completed = true), s("  - [x] Done thing"))
        assertEquals(ImportedSubTask("Open thing", completed = false), s("• [ ] Open thing"))
        assertEquals(ImportedSubTask("Numbered", completed = false), s("12. Numbered"))
        assertEquals(ImportedSubTask("Paren", completed = true), s("3) [X] Paren"))
        assertEquals(ImportedSubTask("Glyph", completed = true), s("☒ Glyph"))
        assertEquals(ImportedSubTask("3.5 hours of study"), s("3.5 hours of study"))
        assertEquals(null, s(" - "))
        assertEquals(null, s("[x]"))
    }

    @Test
    fun lineBreaksInsideAParagraph_splitSubtasks() {
        val subs = "<w:tc><w:p><w:r><w:t>One</w:t><w:br/><w:t>Two</w:t></w:r></w:p></w:tc>"
        val r = validate(docx(table(textRow("Title", "Subtasks"), row(cell("T"), subs))))
        assertEquals(listOf("One", "Two"), r.tasks.single().subTasks.map { it.title })
    }

    @Test
    fun firstMatchingTableWins_nestedTablesAndOtherTablesIgnored() {
        val nested = "<w:tc>${table(textRow("Task"), textRow("nested, ignored"))}<w:p><w:r><w:t>Outer</w:t></w:r></w:p></w:tc>"
        val r = validate(
            docx(
                table(textRow("Name of thing", "Value"), textRow("x", "y")) +
                    table(textRow("Task", "Notes"), row(nested, cell("n"))) +
                    table(textRow("Task"), textRow("Second table")),
            ),
        )
        assertEquals(listOf("Outer"), r.tasks.map { it.title })
    }

    @Test
    fun spanningCell_keepsLaterColumnsAligned() {
        val r = validate(docx(table(textRow("Task", "Notes", "Priority"), row(cell("Wide", span = 2), cell("low")))))
        assertEquals(FocusTask.PRIORITY_LOW, r.tasks.single().priority)
    }

    @Test
    fun noTable_isError() {
        assertTrue(parseError(docx("<w:p><w:r><w:t>Just text</w:t></w:r></w:p>")).message.contains("table"))
        assertTrue(parseError(docx(table(textRow("Name", "Value"), textRow("a", "b")))).message.contains("table"))
    }

    @Test
    fun legacyDoc_isRejectedWithResaveMessage() {
        val issue = parseError(resource("legacy.doc"))
        assertTrue(issue.message.contains(".docx"))
        assertTrue(DocxTaskParser.isLegacyDoc(resource("legacy.doc")))
    }

    @Test
    fun notAZip_orMissingDocument_isError() {
        assertEquals("File", parseError("hello".toByteArray()).location)
        assertEquals("File", parseError(zip("word/other.xml" to "<x/>".toByteArray())).location)
        // Truncated archive.
        val good = docx(table(textRow("Task"), textRow("a")))
        assertEquals("File", parseError(good.copyOf(good.size / 2)).location)
    }

    @Test
    fun oversizedFile_isRejected() {
        val big = docx(table(textRow("Task"), textRow("a"))) + ByteArray(5 * 1024 * 1024)
        assertTrue(parseError(big).message.contains("5 MB"))
    }

    @Test
    fun zipBombDocument_isRejected() {
        // ~30 MB of whitespace compresses to a few tens of KB.
        val padding = ByteArray(30 * 1024 * 1024) { ' '.code.toByte() }
        val bomb = zip("word/document.xml" to documentXml("").toByteArray() + padding)
        assertTrue(bomb.size < 200_000)
        assertTrue(parseError(bomb).message.contains("compressed"))
    }

    @Test
    fun highRatioUnderSizeCap_isRejected() {
        // 15 MB (under the 20 MB cap) but compresses >1000:1.
        val xml = documentXml("<w:p><w:r><w:t>" + " ".repeat(15 * 1024 * 1024) + "</w:t></w:r></w:p>")
        assertTrue(parseError(zip("word/document.xml" to xml.toByteArray())).message.contains("compressed"))
    }

    @Test
    fun bombInAnotherEntry_isNeverInflated() {
        val other = ByteArray(40 * 1024 * 1024)
        val bytes = zip("word/media/huge.bin" to other, "word/document.xml" to documentXml(table(textRow("Task"), textRow("ok"))).toByteArray())
        val start = System.nanoTime()
        assertEquals(listOf("ok"), validate(bytes).tasks.map { it.title })
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2_000)
    }

    @Test
    fun doctype_isRejected() {
        val xml = documentXml(
            table(textRow("Task"), textRow("&lol;")),
            prolog = """<!DOCTYPE w:document [<!ENTITY lol "lololololol">]>""",
        )
        assertEquals("File", parseError(zip("word/document.xml" to xml.toByteArray())).location)
    }

    @Test
    fun overRowLimit_capped() {
        val rows = (1..520).map { textRow("t$it") }.toTypedArray()
        val r = validate(docx(table(textRow("Task"), *rows)))
        assertEquals(500, r.tasks.size)
        assertEquals(1, r.warningCount)
    }

    @Test
    fun xmlEntitiesInText_decoded() {
        val r = validate(docx(table(textRow("Task"), textRow("Fish & chips <today>"))))
        assertEquals("Fish & chips <today>", r.tasks.single().title)
        assertFalse(r.issues.any { it.severity == Severity.ERROR })
    }
}
