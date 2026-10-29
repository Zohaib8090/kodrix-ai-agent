package com.example.agent

import com.example.agent.Risk
import com.example.agent.memory.MemoryException
import com.example.agent.memory.MemoryStore
import com.example.agent.memory.MemoryTool
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MemoryStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = MemoryStore(File(tmp.root, "mem"))

    @Test
    fun createViewEditRoundTrip() {
        val s = store()
        s.create("/memories/user.md", "name: Zohaib\ncity: Karachi")
        assertTrue(s.view("/memories/user.md").contains("     2\tcity: Karachi"))
        s.strReplace("/memories/user.md", "Karachi", "Lahore")
        assertTrue(s.view("/memories/user.md").contains("Lahore"))
        s.insert("/memories/user.md", 0, "# profile")
        assertTrue(s.view("/memories/user.md").startsWith("     1\t# profile"))
        s.rename("/memories/user.md", "/memories/people/me.md")
        assertTrue(s.view("/memories").contains("/memories/people/me.md"))
        s.delete("/memories/people/me.md")
        assertFalse(File(tmp.root, "mem/people/me.md").exists())
    }

    @Test
    fun strReplaceRejectsMissingAndAmbiguousMatches() {
        val s = store()
        s.create("/memories/a.md", "x x")
        try { s.strReplace("/memories/a.md", "x", "y"); org.junit.Assert.fail() } catch (e: MemoryException) { assertTrue(e.message!!.contains("Multiple")) }
        try { s.strReplace("/memories/a.md", "zzz", "y"); org.junit.Assert.fail() } catch (e: MemoryException) { assertTrue(e.message!!.contains("did not appear")) }
    }

    @Test
    fun pathTraversalVariantsAreRejected() {
        val s = store()
        val evil = listOf(
            "/memories/../secret", "/memories/a/../../secret", "../etc/passwd", "/etc/passwd",
            "/memories/%2e%2e/secret", "/memories/%2E%2E%2Fsecret", "/memories/%252e%252e/secret",
            "/memories/..\\secret", "/memoriesevil/x", "memories/x"
        )
        for (p in evil) {
            try { s.create(p, "pwn"); org.junit.Assert.fail("accepted $p") } catch (e: MemoryException) { /* expected */ }
        }
        assertFalse(File(tmp.root, "secret").exists())
    }

    @Test
    fun symlinkEscapeIsRejected() {
        val s = store()
        val outside = tmp.newFolder("outside")
        java.nio.file.Files.createSymbolicLink(File(tmp.root, "mem/link").toPath(), outside.toPath())
        try { s.create("/memories/link/pwn.txt", "x"); org.junit.Assert.fail() } catch (e: MemoryException) { /* expected */ }
        assertFalse(File(outside, "pwn.txt").exists())
    }

    @Test
    fun rootCannotBeDeletedOrRenamed() {
        val s = store()
        try { s.delete("/memories"); org.junit.Assert.fail() } catch (e: MemoryException) {}
        try { s.rename("/memories", "/memories/x"); org.junit.Assert.fail() } catch (e: MemoryException) {}
    }

    @Test
    fun oversizedFilesAreRefused() {
        val s = MemoryStore(File(tmp.root, "small"), maxFileBytes = 10)
        try { s.create("/memories/big.md", "01234567890123"); org.junit.Assert.fail() } catch (e: MemoryException) {}
    }

    @Test
    fun toolReturnsErrorsInsteadOfThrowing() = runTest {
        val t = MemoryTool(store())
        val bad = t.run(JSONObject("""{"command":"create","path":"/memories/../x","file_text":"a"}"""))
        assertTrue(bad.isError)
        val missing = t.run(JSONObject("""{"command":"create"}"""))
        assertTrue(missing.isError)
        val ok = t.run(JSONObject("""{"command":"create","path":"/memories/a.md","file_text":"hello"}"""))
        assertFalse(ok.isError)
        assertEquals(true, t.run(JSONObject("""{"command":"nope"}""")).isError)
    }

    private fun risk(t: MemoryTool, json: String) = t.riskFor(JSONObject(json))

    @Test
    fun readingAndAddingAreFreeButDestructiveCallsAsk() {
        val s = store(); val t = MemoryTool(s)
        s.create("/memories/a.md", "hello world")
        assertEquals(Risk.SAFE, risk(t, """{"command":"view","path":"/memories/a.md"}"""))
        assertEquals(Risk.SAFE, risk(t, """{"command":"create","path":"/memories/new.md","file_text":"x"}"""))
        assertEquals(Risk.SAFE, risk(t, """{"command":"insert","path":"/memories/a.md","insert_line":0,"insert_text":"x"}"""))
        assertEquals(Risk.SAFE, risk(t, """{"command":"str_replace","path":"/memories/a.md","old_str":"hello","new_str":"hello there"}"""))
        assertEquals(Risk.CONFIRM, risk(t, """{"command":"create","path":"/memories/a.md","file_text":"overwrite"}"""))
        assertEquals(Risk.CONFIRM, risk(t, """{"command":"str_replace","path":"/memories/a.md","old_str":"hello world","new_str":""}"""))
        assertEquals(Risk.CONFIRM, risk(t, """{"command":"delete","path":"/memories/a.md"}"""))
    }

    @Test
    fun deleteGoesToRecycleBinAndCanBeRestored() {
        val s = store()
        s.create("/memories/notes/pref.md", "likes tea")
        s.delete("/memories/notes/pref.md")
        assertTrue(s.entries().isEmpty())
        assertEquals(1, s.trash().size)
        val back = s.restore(s.trash().single().path)
        assertEquals("/memories/notes/pref.md", back)
        assertEquals("likes tea", s.read("/memories/notes/pref.md"))
        assertTrue(s.trash().isEmpty())
    }

    @Test
    fun overwriteKeepsAnUndoCopyAndTheBinIsInvisibleToTheModel() {
        val s = store()
        s.create("/memories/a.md", "version one")
        s.create("/memories/a.md", "version two")
        assertEquals("version two", s.read("/memories/a.md"))
        assertEquals(1, s.trash().size)
        assertFalse(s.index().contains("trash"))
        assertFalse(s.view("/memories").contains("trash"))
        assertTrue(runCatching { s.create("/memories/.trash/x", "no") }.isFailure)
        assertTrue(runCatching { s.delete("/memories/.trash") }.isFailure)
    }
}

