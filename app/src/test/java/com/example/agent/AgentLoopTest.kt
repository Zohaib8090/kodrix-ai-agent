package com.example.agent

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class ScriptedLlm(override val label: String, private val script: MutableList<() -> LlmReply>) : LlmClient {
    var calls = 0
    val seen = mutableListOf<List<ChatMessage>>()
    override suspend fun chat(messages: List<ChatMessage>, tools: List<Tool>): LlmReply {
        calls++
        seen += messages.toList()
        if (script.isEmpty()) throw LlmException("script exhausted", retryable = false)
        return script.removeAt(0)()
    }
}

private fun call(name: String, args: String = "{}", id: String = "c${System.nanoTime()}") =
    LlmReply(null, listOf(ToolCall(id, name, args)))

private fun text(t: String) = LlmReply(t, emptyList())

private class EchoTool(override val risk: Risk = Risk.SAFE, val fail: Boolean = false, n: String = "echo") : Tool {
    override val name = n
    override val description = "echo"
    override val parameters = JSONObject("""{"type":"object","properties":{"x":{"type":"string"}}}""")
    var runs = 0
    override suspend fun run(args: JSONObject): ToolResult {
        runs++
        return if (fail) ToolResult("boom", true) else ToolResult("echo:" + args.optString("x"))
    }
}

class AgentLoopTest {

    @Test
    fun answersDirectlyWithoutTools() = runTest {
        val llm = ScriptedLlm("cheap", mutableListOf({ text("hi") }))
        val r = AgentLoop(listOf(llm), ToolRegistry(), "sys").run("hello")
        assertTrue(r.ok); assertEquals("hi", r.text); assertEquals(1, r.steps)
    }

    @Test
    fun runsToolThenAnswers() = runTest {
        val tool = EchoTool()
        val llm = ScriptedLlm("cheap", mutableListOf({ call("echo", """{"x":"a"}""", "id1") }, { text("done") }))
        val r = AgentLoop(listOf(llm), ToolRegistry().register(tool), "sys").run("go")
        assertEquals("done", r.text); assertEquals(1, tool.runs)
        val toolMsg = llm.seen[1].last()
        assertEquals("tool", toolMsg.role); assertEquals("id1", toolMsg.toolCallId); assertEquals("echo:a", toolMsg.content)
    }

    @Test
    fun hallucinatedToolNameIsReportedBackWithRealNames() = runTest {
        val llm = ScriptedLlm("cheap", mutableListOf({ call("play_song_magic") }, { text("ok") }))
        val r = AgentLoop(listOf(llm), ToolRegistry().register(EchoTool()), "sys").run("go")
        val feedback = llm.seen[1].last().content!!
        assertTrue(feedback, feedback.contains("Unknown tool 'play_song_magic'") && feedback.contains("echo"))
        assertTrue(r.ok)
    }

    @Test
    fun invalidJsonArgumentsAreReportedNotCrashed() = runTest {
        val tool = EchoTool()
        val llm = ScriptedLlm("cheap", mutableListOf({ call("echo", "{not json") }, { text("ok") }))
        AgentLoop(listOf(llm), ToolRegistry().register(tool), "sys").run("go")
        assertEquals(0, tool.runs)
        assertTrue(llm.seen[1].last().content!!.contains("not valid JSON"))
    }

    @Test
    fun confirmToolIsBlockedWhenUserDeclines() = runTest {
        val tool = EchoTool(risk = Risk.CONFIRM)
        val llm = ScriptedLlm("cheap", mutableListOf({ call("echo") }, { text("ok") }))
        AgentLoop(listOf(llm), ToolRegistry().register(tool), "sys", confirm = { _, _ -> false }).run("go")
        assertEquals(0, tool.runs)
        assertTrue(llm.seen[1].last().content!!.contains("declined"))
    }

    @Test
    fun confirmToolRunsWhenApproved() = runTest {
        val tool = EchoTool(risk = Risk.CONFIRM)
        val llm = ScriptedLlm("cheap", mutableListOf({ call("echo") }, { text("ok") }))
        AgentLoop(listOf(llm), ToolRegistry().register(tool), "sys", confirm = { _, _ -> true }).run("go")
        assertEquals(1, tool.runs)
    }

    @Test
    fun identicalRepeatedCallsAreCutOffAndEscalate() = runTest {
        val tool = EchoTool()
        val cheap = ScriptedLlm("cheap", MutableList(5) { { call("echo", """{"x":"1"}""") } })
        val strong = ScriptedLlm("strong", mutableListOf({ text("fixed it") }))
        val events = mutableListOf<AgentEvent>()
        val r = AgentLoop(listOf(cheap, strong), ToolRegistry().register(tool), "sys", onEvent = { events += it }).run("go")
        assertEquals(2, tool.runs)           // third identical call is blocked
        assertEquals("strong", r.modelUsed)
        assertEquals("fixed it", r.text)
        assertTrue(events.any { it is AgentEvent.Escalated })
    }

    @Test
    fun argumentOrderDoesNotHideARepeat() = runTest {
        val tool = EchoTool()
        val cheap = ScriptedLlm(
            "cheap",
            mutableListOf({ call("echo", """{"x":"1","y":"2"}""") }, { call("echo", """{"y":"2","x":"1"}""") }, { call("echo", """{"x":"1","y":"2"}""") }, { text("end") })
        )
        AgentLoop(listOf(cheap), ToolRegistry().register(tool), "sys").run("go")
        assertEquals(2, tool.runs)
    }

    @Test
    fun consecutiveToolFailuresEscalate() = runTest {
        val bad = EchoTool(fail = true)
        val cheap = ScriptedLlm("cheap", mutableListOf({ call("echo", """{"x":"1"}""") }, { call("echo", """{"x":"2"}""") }, { call("echo", """{"x":"3"}""") }))
        val strong = ScriptedLlm("strong", mutableListOf({ text("recovered") }))
        val r = AgentLoop(listOf(cheap, strong), ToolRegistry().register(bad), "sys").run("go")
        assertEquals("strong", r.modelUsed); assertEquals("recovered", r.text)
    }

    @Test
    fun providerFailureEscalatesThenSucceeds() = runTest {
        val cheap = ScriptedLlm("cheap", mutableListOf({ throw LlmException("Empty completion") }, { throw LlmException("Empty completion") }))
        val strong = ScriptedLlm("strong", mutableListOf({ text("fine") }))
        val r = AgentLoop(listOf(cheap, strong), ToolRegistry(), "sys").run("go")
        assertEquals("fine", r.text); assertEquals("strong", r.modelUsed)
    }

    @Test
    fun failureOnLastTierReturnsNotOk() = runTest {
        val only = ScriptedLlm("only", mutableListOf({ throw LlmException("down", retryable = false) }))
        val r = AgentLoop(listOf(only), ToolRegistry(), "sys").run("go")
        assertFalse(r.ok); assertTrue(r.text.contains("down"))
    }

    @Test
    fun stepLimitStopsRunawayLoops() = runTest {
        val tool = EchoTool()
        var n = 0
        val llm = ScriptedLlm("cheap", MutableList(50) { { call("echo", """{"x":"${n++}"}""") } })
        val r = AgentLoop(listOf(llm), ToolRegistry().register(tool), "sys", maxSteps = 5).run("go")
        assertFalse(r.ok); assertEquals(5, r.steps)
    }
}
