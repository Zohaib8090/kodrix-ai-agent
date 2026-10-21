package com.example

import com.example.data.ai.ChatTurn
import com.example.data.ai.ProjectContextBuilder
import com.example.data.model.SourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextTest {

    @Test
    fun `normalize starts with the user and merges repeated roles`() {
        val turns = listOf(
            ChatTurn.assistant("Welcome"),
            ChatTurn.user("Hi"),
            ChatTurn.user("Make it blue"),
            ChatTurn.assistant(" "),
            ChatTurn.assistant("Done"),
        )

        assertEquals(
            listOf(ChatTurn.user("Hi\n\nMake it blue"), ChatTurn.assistant("Done")),
            ChatTurn.normalize(turns)
        )
    }

    @Test
    fun `recent keeps the newest turns within the budget`() {
        val turns = (1..10).flatMap { listOf(ChatTurn.user("u$it"), ChatTurn.assistant("a$it")) } + ChatTurn.user("latest")

        val recent = ChatTurn.recent(turns, maxTurns = 3, maxChars = 1000)

        assertEquals(listOf(ChatTurn.user("u10"), ChatTurn.assistant("a10"), ChatTurn.user("latest")), recent)
    }

    @Test
    fun `recent always keeps the newest message even when it is over budget`() {
        val recent = ChatTurn.recent(listOf(ChatTurn.user("old"), ChatTurn.user("x".repeat(50))), maxTurns = 5, maxChars = 10)

        assertEquals(listOf(ChatTurn.user("x".repeat(50))), recent)
    }

    @Test
    fun `project context includes whole files and lists the ones that do not fit`() {
        val files = listOf(
            SourceFile("big.js", "b".repeat(300)),
            SourceFile("index.html", "<html></html>"),
            SourceFile("package-lock.json", "{}"),
            SourceFile("style.css", "body{}"),
        )

        val context = ProjectContextBuilder.describeFiles(files, maxChars = 100)

        assertTrue(context.contains("=== index.html ===\n<html></html>\n"))
        assertTrue(context.contains("=== style.css ===\nbody{}\n"))
        assertTrue(context.contains("- big.js (300 B) [${ProjectContextBuilder.OMITTED_NOTE}]"))
        assertTrue(context.contains("- package-lock.json (2 B) [${ProjectContextBuilder.OMITTED_NOTE}]"))
        // Never a partial file
        assertFalse(context.contains("bbbb"))
    }

    @Test
    fun `the open file is included first`() {
        val files = listOf(SourceFile("a.js", "aaaa"), SourceFile("open.js", "o".repeat(50)))

        val context = ProjectContextBuilder.describeFiles(files, maxChars = 52, priorityPath = "open.js")

        assertTrue(context.contains("=== open.js ==="))
        assertFalse(context.contains("=== a.js ==="))
    }
}
