package com.example

import com.example.data.ai.AiResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseParserTest {

    @Test
    fun `parses files in the FILE ENDFILE format`() {
        val raw = """
            I added a counter.

            FILE: index.html
            ```html
            <h1>Hi</h1>
            ```
            ENDFILE

            FILE: src/app.js
            ```javascript
            let count = 0;
            ```
            ENDFILE
        """.trimIndent()

        val files = AiResponseParser.parseFiles(raw)

        assertEquals(listOf("index.html", "src/app.js"), files.map { it.path })
        assertEquals("<h1>Hi</h1>\n", files[0].content)
        assertEquals("let count = 0;\n", files[1].content)
    }

    @Test
    fun `keeps code fences that are part of the file content`() {
        val raw = """
            FILE: README.md
            ```markdown
            # Usage
            ```bash
            npm run dev
            ```
            Done.
            ```
            ENDFILE
        """.trimIndent()

        val files = AiResponseParser.parseFiles(raw)

        assertEquals(1, files.size)
        assertEquals("# Usage\n```bash\nnpm run dev\n```\nDone.\n", files[0].content)
    }

    @Test
    fun `parses files without ENDFILE and cleans decorated paths`() {
        val raw = "FILE: `src/App.tsx`\n```tsx\nexport default 1\n```\n\nFILE: **index.css**\n```css\nbody{}\n```"

        val files = AiResponseParser.parseFiles(raw)

        assertEquals(listOf("src/App.tsx", "index.css"), files.map { it.path })
    }

    @Test
    fun `a file cut off mid-way is reported and never returned`() {
        val raw = "FILE: a.js\n```js\nconst a = 1\n```\nENDFILE\nFILE: b.js\n```js\nfunction half() {\n  return"

        assertEquals(listOf("a.js"), AiResponseParser.parseFiles(raw).map { it.path })
        assertEquals(listOf("b.js"), AiResponseParser.parseIncompleteFilePaths(raw))
    }

    @Test
    fun `the last version of a repeated path wins`() {
        val raw = "FILE: a.txt\n```\none\n```\nENDFILE\nFILE: a.txt\n```\ntwo\n```\nENDFILE"

        val files = AiResponseParser.parseFiles(raw)

        assertEquals(1, files.size)
        assertEquals("two\n", files[0].content)
    }

    @Test
    fun `falls back to heading style blocks`() {
        val raw = "### src/main.js\n```js\nconsole.log(1)\n```"

        assertEquals(listOf("src/main.js"), AiResponseParser.parseFiles(raw).map { it.path })
    }

    @Test
    fun `only line-start COMMAND lines outside files are commands`() {
        val raw = """
            Installing axios and adding a CI workflow. Then run: npm test yourself.
            COMMAND: npm install axios
            - COMMAND: `git status`

            FILE: .github/workflows/ci.yml
            ```yaml
            steps:
              - run: npm ci
              - name: Build
                run: npm run build
            COMMAND: rm -rf /
            ```
            ENDFILE
        """.trimIndent()

        assertEquals(listOf("npm install axios", "git status"), AiResponseParser.parseCommands(raw))
    }

    @Test
    fun `conversational text drops files, commands and the linux tag`() {
        val raw = "Sure, updating it now. ${AiResponseParser.LINUX_SUBSYSTEM_TAG}\n\nFILE: a.js\n```js\nx\n```\nENDFILE\n\nCOMMAND: npm i\n\n\n\nAll done."

        assertTrue(AiResponseParser.requestsLinuxSubsystem(raw))
        assertEquals("Sure, updating it now. \n\nAll done.", AiResponseParser.extractConversationalText(raw))
    }

    @Test
    fun `plain chat has no files or commands`() {
        val raw = "Hello! What would you like to change?"

        assertTrue(AiResponseParser.parseFiles(raw).isEmpty())
        assertTrue(AiResponseParser.parseCommands(raw).isEmpty())
        assertFalse(AiResponseParser.requestsLinuxSubsystem(raw))
        assertEquals(raw, AiResponseParser.extractConversationalText(raw))
    }
}
