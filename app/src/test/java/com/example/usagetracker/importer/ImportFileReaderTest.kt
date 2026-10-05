package com.example.usagetracker.importer

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImportFileReaderTest {
    private fun error(bytes: ByteArray, name: String): ImportIssue = try {
        ImportFileReader.read(bytes.inputStream(), name)
        fail("Expected ImportException")
        error("unreachable")
    } catch (e: ImportException) {
        e.issue
    }

    @Test
    fun detectsFormatByContentNotName() {
        val docx = File("src/main/assets/templates/tasks_template.docx").readBytes()
        assertEquals(3, ImportFileReader.read(docx.inputStream(), "tasks.txt").tasks.size)
        assertEquals(1, ImportFileReader.read("""[{"title":"a"}]""".byteInputStream(), "notes.txt").tasks.size)
        assertEquals(1, ImportFileReader.read("""  {"tasks":[{"title":"a"}]}""".byteInputStream(), null).tasks.size)
    }

    @Test
    fun legacyDoc_byContentOrName() {
        assertTrue(error(File("src/test/resources/docx/legacy.doc").readBytes(), "x.bin").message.contains(".docx"))
        assertTrue(error("garbage".toByteArray(), "Old.DOC").message.contains(".docx"))
    }

    @Test
    fun jsonOver2Mb_rejected() {
        val big = "[" + " ".repeat(2 * 1024 * 1024) + "]"
        assertTrue(error(big.toByteArray(), "big.json").message.contains("2 MB"))
    }

    @Test
    fun unknownContent_rejected() {
        assertTrue(error("hello world".toByteArray(), "a.json").message.contains(".json"))
    }
}
