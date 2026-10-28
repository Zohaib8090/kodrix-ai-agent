package com.example.agent

import com.example.agent.mcp.McpClient
import com.example.agent.mcp.McpTool
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LlmAndMcpTest {
    private lateinit var server: MockWebServer

    @Before fun up() { server = MockWebServer(); server.start() }
    @After fun down() { server.shutdown() }

    private val echo = object : Tool {
        override val name = "echo"; override val description = "d"
        override val parameters = JSONObject("""{"type":"object","properties":{}}""")
        override suspend fun run(args: JSONObject) = ToolResult("x")
    }

    private fun llm(extra: JSONObject? = null) =
        OpenAiCompatLlm(server.url("/v1").toString(), "KEY", "deepseek/deepseek-v4-flash", extra)

    @Test
    fun sendsToolsAuthAndProviderPinning() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"hello"}}]}"""))
        val extra = JSONObject("""{"provider":{"order":["DeepSeek"],"allow_fallbacks":true}}""")
        val r = llm(extra).chat(listOf(ChatMessage("user", "hi")), listOf(echo))
        assertEquals("hello", r.text)
        val req = server.takeRequest()
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer KEY", req.getHeader("Authorization"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("deepseek/deepseek-v4-flash", body.getString("model"))
        assertEquals("echo", body.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"))
        assertEquals("DeepSeek", body.getJSONObject("provider").getJSONArray("order").getString(0))
    }

    @Test
    fun parsesToolCalls() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":null,"tool_calls":[{"id":"abc","type":"function","function":{"name":"echo","arguments":"{\"a\":1}"}}]}}]}"""))
        val r = llm().chat(listOf(ChatMessage("user", "hi")), listOf(echo))
        assertEquals(1, r.toolCalls.size)
        assertEquals("abc", r.toolCalls[0].id); assertEquals("""{"a":1}""", r.toolCalls[0].argumentsJson)
    }

    @Test
    fun serializesAssistantToolCallsAndToolResults() {
        val body = llm().buildBody(
            listOf(
                ChatMessage("user", "hi"),
                ChatMessage("assistant", null, listOf(ToolCall("t1", "echo", "{}"))),
                ChatMessage("tool", "result", toolCallId = "t1"),
            ),
            listOf(echo),
        )
        val msgs = body.getJSONArray("messages")
        assertEquals("t1", msgs.getJSONObject(1).getJSONArray("tool_calls").getJSONObject(0).getString("id"))
        assertEquals("t1", msgs.getJSONObject(2).getString("tool_call_id"))
    }

    @Test
    fun emptyCompletionAndHttpErrorsBecomeLlmExceptions() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":""}}]}"""))
        try { llm().chat(listOf(ChatMessage("user", "hi")), emptyList()); org.junit.Assert.fail() } catch (e: LlmException) { assertTrue(e.message!!.contains("Empty")) }
        server.enqueue(MockResponse().setResponseCode(429).setBody("slow down"))
        try { llm().chat(listOf(ChatMessage("user", "hi")), emptyList()); org.junit.Assert.fail() } catch (e: LlmException) { assertTrue(e.retryable) }
        server.enqueue(MockResponse().setResponseCode(401).setBody("bad key"))
        try { llm().chat(listOf(ChatMessage("user", "hi")), emptyList()); org.junit.Assert.fail() } catch (e: LlmException) { assertFalse(e.retryable) }
        server.enqueue(MockResponse().setBody("""{"error":{"message":"no capacity"}}"""))
        try { llm().chat(listOf(ChatMessage("user", "hi")), emptyList()); org.junit.Assert.fail() } catch (e: LlmException) { assertTrue(e.message!!.contains("no capacity")) }
    }

    // ---- MCP over Streamable HTTP ----

    private fun mcpJson(id: Int, result: String) = MockResponse().setHeader("Content-Type", "application/json")
        .setBody("""{"jsonrpc":"2.0","id":$id,"result":$result}""")

    @Test
    fun mcpHandshakeListAndCallWithJsonResponses() = runTest {
        server.enqueue(mcpJson(1, """{"protocolVersion":"2025-03-26","capabilities":{},"serverInfo":{"name":"s","version":"1"}}""").setHeader("Mcp-Session-Id", "sess-1"))
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(mcpJson(2, """{"tools":[{"name":"play","description":"Play","inputSchema":{"type":"object","properties":{}}},{"name":"delete_playlist","description":"Del","inputSchema":{"type":"object"}}]}"""))
        server.enqueue(mcpJson(3, """{"content":[{"type":"text","text":"playing"}],"isError":false}"""))

        val client = McpClient(server.url("/mcp").toString(), "TOKEN")
        val tools = McpTool.discover(client, "spotify")
        assertEquals(listOf("spotify__play", "spotify__delete_playlist"), tools.map { it.name })
        assertEquals(Risk.SAFE, tools[0].risk); assertEquals(Risk.CONFIRM, tools[1].risk)
        assertEquals("playing", tools[0].run(JSONObject("{}")).text)

        val init = server.takeRequest(); assertEquals("Bearer TOKEN", init.getHeader("Authorization"))
        assertEquals("initialize", JSONObject(init.body.readUtf8()).getString("method"))
        server.takeRequest() // initialized notification
        val list = server.takeRequest(); assertEquals("sess-1", list.getHeader("Mcp-Session-Id"))
    }

    @Test
    fun mcpParsesServerSentEventResponsesAndToolErrors() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-03-26\"}}\n\n"))
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody("data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/message\",\"params\":{}}\n\nevent: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"No active device\"}],\"isError\":true}}\n\n"))
        val client = McpClient(server.url("/mcp").toString())
        val r = client.callTool("play", JSONObject("{}"))
        assertTrue(r.isError); assertEquals("No active device", r.text)
    }

    @Test
    fun mcp401IsReportedAsError() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val client = McpClient(server.url("/mcp").toString())
        try { client.initialize(); org.junit.Assert.fail() } catch (e: com.example.agent.mcp.McpException) { assertTrue(e.message!!.contains("401")) }
    }

    @Test
    fun toolNamesAreSanitisedForProviders() {
        assertEquals("a_b_c", McpTool.sanitize("a.b c"))
        assertEquals(64, McpTool.sanitize("x".repeat(100)).length)
    }
}
