package com.example.usagetracker.importer

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException

/**
 * Reads tasks from the first table in a .docx whose header row has a "Task" or "Title" column.
 * Streams only `word/document.xml`; all other document content and formatting is ignored.
 */
object DocxTaskParser {
    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    private enum class Column { TITLE, DATE, DEADLINE, PRIORITY, ESTIMATE, TAGS, NOTES, SUBTASKS, COMPLETED }

    /** Header text with everything but letters removed, lower-cased. */
    private val HEADERS = mapOf(
        "task" to Column.TITLE, "title" to Column.TITLE,
        "date" to Column.DATE, "scheduleddate" to Column.DATE, "day" to Column.DATE,
        "deadline" to Column.DEADLINE, "due" to Column.DEADLINE, "duedate" to Column.DEADLINE,
        "priority" to Column.PRIORITY,
        "estimatedminutes" to Column.ESTIMATE, "estimateminutes" to Column.ESTIMATE,
        "durationminutes" to Column.ESTIMATE, "estimate" to Column.ESTIMATE, "minutes" to Column.ESTIMATE,
        "tags" to Column.TAGS,
        "notes" to Column.NOTES, "description" to Column.NOTES, "details" to Column.NOTES,
        "subtasks" to Column.SUBTASKS, "subtask" to Column.SUBTASKS, "steps" to Column.SUBTASKS, "checklist" to Column.SUBTASKS,
        "completed" to Column.COMPLETED, "done" to Column.COMPLETED,
    )

    private val NON_LETTERS = Regex("[^a-z]")

    /** Leading bullet characters, or "1." / "2)" numbering followed by a space (so "3.5 hours" survives). */
    private val BULLET = Regex("^(?:[-*•‣◦▪▫●○·–—]+\\s*|\\d{1,3}[.)](?:\\s+|$))")
    private val CHECKBOX = Regex("^(?:\\[([xX ]?)]|([☐☑☒✓✔]))\\s*")

    private val OLE2_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())

    fun isLegacyDoc(bytes: ByteArray) = bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(OLE2_MAGIC)

    fun isZip(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
        bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /** @throws ImportException if the file isn't a usable .docx or has no task table. */
    fun parse(bytes: ByteArray): ParsedImport {
        if (isLegacyDoc(bytes)) throw fileError(LEGACY_DOC_MESSAGE)
        if (bytes.size > ImportLimits.MAX_DOCX_BYTES) throw fileError("Word files can be at most 5 MB.")
        if (!isZip(bytes)) throw fileError("This is not a valid Word (.docx) file.")
        val stream = try {
            ZipEntryReader.open(bytes, "word/document.xml", ImportLimits.MAX_DOCX_UNCOMPRESSED_BYTES, ImportLimits.MAX_COMPRESSION_RATIO)
        } catch (_: ZipEntryReader.ZipLimitException) {
            throw fileError("This Word file is too large or unusually compressed, so it was not opened.")
        } catch (_: IOException) {
            throw fileError("This Word file is damaged or not a valid .docx file.")
        } ?: throw fileError("This is not a Word document (word/document.xml is missing).")

        val rows = try {
            stream.use { readTaskTable(it) }
        } catch (_: ZipEntryReader.ZipLimitException) {
            throw fileError("This Word file is too large or unusually compressed, so it was not opened.")
        } catch (_: XmlPullParserException) {
            throw fileError("This Word file is damaged and could not be read.")
        } catch (_: IOException) {
            throw fileError("This Word file is damaged and could not be read.")
        } ?: throw fileError("No table with a \"Task\" or \"Title\" column was found. Use the Word template's table layout.")

        return toTasks(rows)
    }

    /** A row is its cells; a cell is its lines (paragraphs and line breaks). Row 0 is the header. */
    private fun readTaskTable(input: java.io.InputStream): List<IndexedRow>? {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(input, null)

        var tableDepth = 0
        var rowNumber = 0 // 1-based within the current top-level table
        var rows = mutableListOf<IndexedRow>()
        var cells: MutableList<List<String>>? = null
        var lines: MutableList<String>? = null
        val line = StringBuilder()
        var inText = false
        var span = 1
        var header: Map<Int, Column>? = null

        while (true) {
            val event = parser.nextToken()
            when (event) {
                XmlPullParser.END_DOCUMENT -> return null
                // Word never writes a DOCTYPE; refusing it rules out entity-expansion attacks.
                XmlPullParser.DOCDECL -> throw XmlPullParserException("DOCTYPE not allowed")
                XmlPullParser.START_TAG -> if (parser.namespace == W) when (parser.name) {
                    "tbl" -> {
                        tableDepth++
                        if (tableDepth == 1) {
                            rowNumber = 0
                            rows = mutableListOf()
                            header = null
                        }
                    }
                    "tr" -> if (tableDepth == 1) {
                        rowNumber++
                        cells = mutableListOf()
                    }
                    "tc" -> if (tableDepth == 1) {
                        lines = mutableListOf()
                        line.setLength(0)
                        span = 1
                    }
                    "gridSpan" -> if (tableDepth == 1) {
                        span = parser.getAttributeValue(W, "val")?.toIntOrNull()?.coerceIn(1, 64) ?: 1
                    }
                    "t" -> inText = tableDepth == 1 && lines != null
                    "br", "cr" -> if (tableDepth == 1 && lines != null) newLine(lines, line)
                    "tab" -> if (tableDepth == 1 && lines != null) line.append(' ')
                }
                XmlPullParser.TEXT, XmlPullParser.ENTITY_REF, XmlPullParser.CDSECT -> if (inText) {
                    line.append(parser.text.orEmpty())
                    if (line.length > MAX_CELL_CHARS) line.setLength(MAX_CELL_CHARS)
                }
                XmlPullParser.END_TAG -> if (parser.namespace == W) when (parser.name) {
                    "t" -> inText = false
                    "p" -> if (tableDepth == 1 && lines != null) newLine(lines, line)
                    "tc" -> if (tableDepth == 1) {
                        val l = lines ?: mutableListOf()
                        if (line.isNotEmpty()) newLine(l, line)
                        cells?.add(l)
                        // A merged (spanning) cell occupies several columns; pad so later cells stay aligned.
                        repeat(span - 1) { cells?.add(emptyList()) }
                        lines = null
                    }
                    "tr" -> if (tableDepth == 1) {
                        val row = cells.orEmpty()
                        cells = null
                        if (rowNumber == 1) {
                            header = headerColumns(row)
                        } else if (header != null) {
                            rows += IndexedRow(rowNumber, row)
                            // Rows past the cap are dropped by the validator anyway; stop growing memory.
                            if (rows.size > ImportLimits.MAX_TASKS) return listOf(IndexedRow(1, emptyList(), header)) + rows
                        }
                    }
                    "tbl" -> {
                        tableDepth--
                        if (tableDepth == 0 && header != null) return listOf(IndexedRow(1, emptyList(), header)) + rows
                    }
                }
            }
        }
    }

    private class IndexedRow(val number: Int, val cells: List<List<String>>, val header: Map<Int, Column>? = null)

    /** Column index → field, or null if this table has no task/title column. */
    private fun headerColumns(cells: List<List<String>>): Map<Int, Column>? {
        val map = HashMap<Int, Column>()
        cells.forEachIndexed { i, lines ->
            val key = lines.joinToString("").lowercase().replace(NON_LETTERS, "")
            val column = HEADERS[key] ?: return@forEachIndexed
            if (column !in map.values) map[i] = column
        }
        return map.takeIf { Column.TITLE in it.values }
    }

    private fun toTasks(rows: List<IndexedRow>): ParsedImport {
        val header = rows.first().header!!
        val issues = mutableListOf<ImportIssue>()
        val tasks = rows.drop(1).mapNotNull { row ->
            if (row.cells.all { cell -> cell.all { it.isBlank() } }) return@mapNotNull null
            val loc = "Table row ${row.number}"
            fun lines(c: Column) = header.entries.firstOrNull { it.value == c }?.let { row.cells.getOrNull(it.key) }.orEmpty()
            fun text(c: Column) = lines(c).joinToString(" ").trim().takeIf { it.isNotEmpty() }
            ImportedTask(
                location = loc,
                title = text(Column.TITLE),
                date = text(Column.DATE),
                deadline = text(Column.DEADLINE),
                priority = text(Column.PRIORITY),
                estimatedMinutes = text(Column.ESTIMATE),
                tags = lines(Column.TAGS).flatMap { it.split(',') },
                notes = lines(Column.NOTES).joinToString("\n").takeIf { it.isNotBlank() },
                completed = text(Column.COMPLETED)?.lowercase()?.let { it == "yes" || it == "true" || it == "x" || it == "done" } ?: false,
                subTasks = lines(Column.SUBTASKS).mapNotNull(::subTask),
            )
        }
        return ParsedImport(tasks, issues)
    }

    /** "- [x] Collect receipts" → completed subtask "Collect receipts". */
    internal fun subTask(line: String): ImportedSubTask? {
        var t = line.trim().replace(BULLET, "")
        var completed = false
        CHECKBOX.find(t)?.let { m ->
            val mark = m.groupValues[1].ifEmpty { m.groupValues[2] }
            completed = mark.isNotBlank() && mark != "☐"
            t = t.substring(m.range.last + 1)
        }
        t = t.trim()
        return if (t.isEmpty()) null else ImportedSubTask(title = t, completed = completed)
    }

    private fun newLine(lines: MutableList<String>, line: StringBuilder) {
        lines += line.toString()
        line.setLength(0)
    }

    private fun fileError(message: String) = ImportException(ImportIssue(Severity.ERROR, "File", message))

    const val LEGACY_DOC_MESSAGE =
        "This is an old Word (.doc) file. Open it in Word or Google Docs, save it as .docx, and import that file."

    /** Enough for the longest allowed notes plus slack; longer cell text is cut while reading. */
    private const val MAX_CELL_CHARS = 20_000
}
