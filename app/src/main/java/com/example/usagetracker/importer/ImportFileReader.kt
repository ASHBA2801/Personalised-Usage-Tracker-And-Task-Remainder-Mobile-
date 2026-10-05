package com.example.usagetracker.importer

import java.io.InputStream

/** Reads a picked file once into memory (capped), detects JSON vs DOCX by content and name, and parses it. */
object ImportFileReader {
    private const val UNSUPPORTED = "This file type isn't supported. Pick a .json file or a Word .docx file."

    /** @throws ImportException with a user-facing message if the file can't be used. */
    fun read(input: InputStream, displayName: String?): ParsedImport {
        val bytes = readCapped(input, ImportLimits.MAX_DOCX_BYTES)
        val name = displayName.orEmpty().lowercase()
        return when {
            DocxTaskParser.isLegacyDoc(bytes) || (name.endsWith(".doc") && !DocxTaskParser.isZip(bytes)) ->
                throw fileError(DocxTaskParser.LEGACY_DOC_MESSAGE)
            DocxTaskParser.isZip(bytes) -> DocxTaskParser.parse(bytes)
            bytes.size > ImportLimits.MAX_JSON_BYTES -> throw fileError(
                if (bytes.size > ImportLimits.MAX_DOCX_BYTES) "This file is too large to import." else "JSON files can be at most 2 MB.",
            )
            looksLikeJson(bytes) -> JsonTaskParser.parse(String(bytes, Charsets.UTF_8))
            else -> throw fileError(UNSUPPORTED)
        }
    }

    /** Reads at most [max] + 1 bytes, so callers can tell "too large" apart without reading the rest. */
    private fun readCapped(input: InputStream, max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (total <= max) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), max + 1 - total).toInt())
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    /** First non-blank character (after an optional BOM) opens an object or array. */
    private fun looksLikeJson(bytes: ByteArray): Boolean {
        var i = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        while (i < bytes.size && bytes[i].toInt().toChar().isWhitespace()) i++
        return i < bytes.size && (bytes[i] == '{'.code.toByte() || bytes[i] == '['.code.toByte())
    }

    private fun fileError(message: String) = ImportException(ImportIssue(Severity.ERROR, "File", message))
}
