package com.example.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiParserTest {

    private fun AnsiParser.text(line: String) = parseLine(line).joinToString("") { it.text }

    @Test
    fun `applies foreground colors and resets`() {
        val spans = AnsiParser().parseLine("\u001B[31mred\u001B[0m plain \u001B[1;38;5;196mbold\u001B[0m")
        assertEquals(listOf("red", " plain ", "bold"), spans.map { it.text })
        assertEquals(AnsiParser.PALETTE[1], spans[0].style.foreground)
        assertNull(spans[1].style.foreground)
        assertTrue(spans[2].style.bold)
        assertEquals(AnsiParser.xterm256(196), spans[2].style.foreground)
    }

    @Test
    fun `supports truecolor and carries style across lines`() {
        val parser = AnsiParser()
        parser.parseLine("\u001B[38;2;1;2;3mfirst")
        val next = parser.parseLine("second")
        assertEquals(0xFF010203.toInt(), next[0].style.foreground)
    }

    @Test
    fun `drops cursor, erase and title sequences`() {
        val parser = AnsiParser()
        assertEquals("hello", parser.text("\u001B]0;title\u0007\u001B[2K\u001B[1Ahello\u001B["))
    }

    @Test
    fun `carriage return overwrites earlier text like a progress bar`() {
        assertEquals("100%", AnsiParser().text("10%\r50%\r100%"))
        assertEquals("done", AnsiParser().text("done\r"))
    }

    @Test
    fun `backspace and tab are handled`() {
        assertEquals("ab", AnsiParser().text("abc\b"))
        assertEquals("a    b", AnsiParser().text("a\tb"))
    }
}
