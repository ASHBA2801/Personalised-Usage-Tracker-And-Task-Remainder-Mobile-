package com.example.usagetracker.service

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun node(label: String?, id: String? = "reel_recycler") =
        DiagNode(3, "FrameLayout", id, 0, 0, 1080, 2400, selected = true, checked = false, scrollable = true, visible = true, childCount = 2, label = label)

    @Test fun shortLabelsKeptLongOnesReplaced() {
        assertEquals("Shorts", DiagFormatter.sanitizeLabel("Shorts"))
        assertEquals("12345678901234567890", DiagFormatter.sanitizeLabel("12345678901234567890")) // exactly 20
        assertEquals("<long>", DiagFormatter.sanitizeLabel("123456789012345678901")) // 21
        assertEquals("<long>", DiagFormatter.sanitizeLabel("How I built a house in the woods - full video"))
        assertNull(DiagFormatter.sanitizeLabel(null))
        assertNull(DiagFormatter.sanitizeLabel("   "))
    }

    @Test fun recordContainsStructureButNoLongText() {
        val title = "My Secret Video Title That Is Long"
        val r = DiagRecord(
            0L, 7, ContentTag.OTHER, ContentTag.SHORTS, listOf("shorts:id:reel_recycler@d14"), "prod-depth",
            600, 17, "WCC:3", listOf(node(DiagFormatter.sanitizeLabel(title)), node("Shorts", id = null)),
        )
        val text = DiagFormatter.record(r, includeTree = true)
        assertFalse(text.contains("Secret"))
        assertTrue(text.contains("<long>"))
        assertTrue(text.contains("3 FrameLayout reel_recycler 0,0,1080,2400 S-RV n2"))
        assertTrue(text.contains("prod=other full=shorts"))
    }

    @Test fun unchangedTreeIsNotRepeated() {
        val r = DiagRecord(0L, 1, ContentTag.SHORTS, ContentTag.SHORTS, emptyList(), "-", 1, 3, "", listOf(node(null)))
        assertEquals(1, DiagFormatter.record(r, includeTree = false).lines().filter { it.isNotBlank() }.size)
    }

    @Test fun signatureIgnoresBounds() {
        assertEquals(
            DiagFormatter.signature(listOf(node(null))),
            DiagFormatter.signature(listOf(node(null).copy(top = 50, bottom = 2450))),
        )
    }

    @Test fun fileNeverExceedsTwiceTheCap() {
        val dir = File(tmp.root, "d")
        val w = DiagFileWriter(dir, maxFileBytes = 1_000)
        repeat(200) { w.append("x".repeat(90) + "\n") }
        val total = dir.listFiles()!!.sumOf { it.length() }
        assertTrue("total=$total", total <= 2_000)
        assertTrue(dir.listFiles()!!.any { it.readText().endsWith("\n") })
        w.clear()
        assertTrue(dir.listFiles()!!.isEmpty())
    }
}
