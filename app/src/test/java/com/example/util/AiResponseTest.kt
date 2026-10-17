package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiResponseTest {

    private val reply = "Done.\nFILE: src/app.js\n```js\nconsole.log(1)\n```\nENDFILE\nCOMMAND: npm install\n"

    @Test
    fun `parses files commands and prose`() {
        assertEquals(listOf("src/app.js"), AiResponse.parseFiles(reply).map { it.path })
        assertEquals(listOf("npm install"), AiResponse.parseCommands(reply))
        assertEquals("Done.", AiResponse.conversationalText(reply, hasAction = true))
    }

    @Test
    fun `unterminated file blocks are not parsed`() {
        assertEquals(0, AiResponse.parseFiles("FILE: a.js\n```js\nhalf written").size)
    }

    @Test
    fun `safe paths are normalized and traversal is rejected`() {
        assertEquals("a/b.js", AiResponse.safeRelativePath("`a/b.js`"))
        assertEquals("a/b.js", AiResponse.safeRelativePath("./a\\b.js"))
        assertEquals("etc/passwd", AiResponse.safeRelativePath("/etc/passwd"))
        assertNull(AiResponse.safeRelativePath("../../x"))
        assertNull(AiResponse.safeRelativePath("a/../../x"))
        assertNull(AiResponse.safeRelativePath("   "))
        assertNull(AiResponse.safeRelativePath("a//b"))
    }

    @Test
    fun `streaming preview collapses file blocks into status lines`() {
        assertEquals("Hi\nWriting a.js (2 lines so far)", AiResponse.streamingPreview("Hi\nFILE: a.js\n```js\nx\ny"))
        assertEquals("Wrote a.js (1 lines)", AiResponse.streamingPreview("FILE: a.js\n```js\nx\n```\nENDFILE"))
        assertEquals("Will run: npm i", AiResponse.streamingPreview("COMMAND: npm i"))
    }

    @Test
    fun `streaming preview never flashes a partial subsystem tag`() {
        assertEquals("ok", AiResponse.streamingPreview("ok <REQUEST_LIN"))
        assertEquals("ok", AiResponse.streamingPreview("ok <REQUEST_LINUX_SUBSYSTEM>"))
        assertEquals("a < b", AiResponse.streamingPreview("a < b"))
    }

    @Test
    fun `linux request tag is detected and stripped`() {
        assertEquals("hi" to true, AiResponse.stripLinuxRequest("hi <REQUEST_LINUX_SUBSYSTEM>"))
        assertEquals("hi" to false, AiResponse.stripLinuxRequest("hi"))
    }
}
