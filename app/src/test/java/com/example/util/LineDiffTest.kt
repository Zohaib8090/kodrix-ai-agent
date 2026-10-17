package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineDiffTest {

    @Test
    fun `reports added and removed lines with line numbers`() {
        val diff = LineDiff.diff("a\nb\nc\n", "a\nB\nc\nd\n")
        assertEquals(listOf("a", "b", "B", "c", "d"), diff.map { it.text })
        assertEquals(DiffStats(added = 2, removed = 1), LineDiff.stats(diff))
        assertEquals(2, diff.first { it.text == "b" }.oldLineNumber)
        assertEquals(2, diff.first { it.text == "B" }.newLineNumber)
    }

    @Test
    fun `new file is all additions`() {
        val diff = LineDiff.diff(null, "x\ny")
        assertEquals(DiffStats(added = 2, removed = 0), LineDiff.stats(diff))
    }

    @Test
    fun `line ending differences are not changes`() {
        assertEquals(DiffStats(0, 0), LineDiff.stats(LineDiff.diff("a\r\nb\r\n", "a\nb\n")))
    }

    @Test
    fun `identical files collapse to nothing`() {
        assertTrue(LineDiff.collapse(LineDiff.diff("a\nb", "a\nb")).isEmpty())
    }

    @Test
    fun `collapse keeps three lines of context around a change`() {
        val old = (1..30).joinToString("\n")
        val new = old.replace("15", "fifteen")
        val collapsed = LineDiff.collapse(LineDiff.diff(old, new))
        assertEquals("...", collapsed.first().text)
        assertEquals("...", collapsed.last().text)
        // gap marker, 3 context, removed, added, 3 context, gap marker
        assertEquals(1 + 3 + 2 + 3 + 1, collapsed.size)
    }

    @Test
    fun `very large rewrites fall back instead of exhausting memory`() {
        val a = (1..3000).joinToString("\n") { "old$it" }
        val b = (1..3000).joinToString("\n") { "new$it" }
        assertEquals(DiffStats(3000, 3000), LineDiff.stats(LineDiff.diff(a, b)))
    }
}
