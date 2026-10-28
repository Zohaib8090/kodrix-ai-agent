package com.example.agent.mcp

import com.example.agent.Risk
import com.example.agent.Tool
import com.example.agent.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class McpException(message: String) : Exception(message)

data class McpToolInfo(val name: String, val description: String, val inputSchema: JSONObject)

/**
 * Minimal MCP client for the Streamable HTTP transport (spec 2025-03-26 and later).
 * Handles both plain JSON responses and text/event-stream responses, and the Mcp-Session-Id header.
 */
class McpClient(
    private val url: String,
    private val bearerToken: String? = null,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val jsonType = "application/json".toMediaType()
    private val ids = AtomicInteger(0)

    @Volatile
    private var sessionId: String? = null

    @Volatile
    private var initialized = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val result = rpc(
            "initialize",
            JSONObject()
                .put("protocolVersion", "2025-03-26")
                .put("capabilities", JSONObject())
                .put("clientInfo", JSONObject().put("name", "kodrix-agent").put("version", "1.0")),
        )
        if (result == null) throw McpException("initialize returned no result")
        notify("notifications/initialized")
        initialized = true
    }

    suspend fun listTools(): List<McpToolInfo> = withContext(Dispatchers.IO) {
        ensureInit()
        val out = mutableListOf<McpToolInfo>()
        var cursor: String? = null
        do {
            val params = JSONObject().apply { cursor?.let { put("cursor", it) } }
            val res = rpc("tools/list", params) ?: throw McpException("tools/list returned no result")
            val arr = res.optJSONArray("tools") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                out += McpToolInfo(
                    t.getString("name"),
                    t.optString("description"),
                    t.optJSONObject("inputSchema") ?: JSONObject().put("type", "object"),
                )
            }
            cursor = res.optString("nextCursor").takeIf { it.isNotBlank() }
        } while (cursor != null)
        out
    }

    suspend fun callTool(name: String, args: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        ensureInit()
        val res = try {
            rpc("tools/call", JSONObject().put("name", name).put("arguments", args))
        } catch (e: McpException) {
            return@withContext ToolResult("MCP error: ${e.message}", isError = true)
        } ?: return@withContext ToolResult("MCP returned no result", isError = true)
        val content = res.optJSONArray("content")
        val text = buildString {
            if (content != null) for (i in 0 until content.length()) {
                val c = content.getJSONObject(i)
                when (c.optString("type")) {
                    "text" -> append(c.optString("text")).append('\n')
                    else -> append("[${c.optString("type")} content omitted]\n")
                }
            }
        }.trim().ifEmpty { res.optJSONObject("structuredContent")?.toString() ?: "(no output)" }
        ToolResult(text, isError = res.optBoolean("isError", false))
    }

    private suspend fun ensureInit() {
        if (!initialized) initialize()
    }

    private fun builder(): Request.Builder = Request.Builder().url(url)
        .header("Accept", "application/json, text/event-stream")
        .apply {
            bearerToken?.let { header("Authorization", "Bearer $it") }
            sessionId?.let { header("Mcp-Session-Id", it) }
        }

    private fun notify(method: String) {
        val body = JSONObject().put("jsonrpc", "2.0").put("method", method).toString()
        try {
            http.newCall(builder().post(body.toRequestBody(jsonType)).build()).execute().close()
        } catch (_: IOException) {
            // Notifications are fire-and-forget.
        }
    }

    /** Sends one JSON-RPC request and returns its `result` object. */
    private fun rpc(method: String, params: JSONObject): JSONObject? {
        val id = ids.incrementAndGet()
        val body = JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params)
        val req = builder().post(body.toString().toRequestBody(jsonType)).build()
        try {
            http.newCall(req).execute().use { resp ->
                resp.header("Mcp-Session-Id")?.let { sessionId = it }
                val text = resp.body?.string().orEmpty()
                if (resp.code == 401) throw McpException("Unauthorized (401). Reconnect or refresh the token.")
                if (!resp.isSuccessful) throw McpException("HTTP ${resp.code}: ${text.take(200)}")
                val ct = resp.header("Content-Type").orEmpty()
                val parsed: JSONObject? = if (ct.contains("text/event-stream")) sseFor(text, id) else JSONObject(text)
                val msg = parsed ?: throw McpException("No response message for id $id")
                msg.optJSONObject("error")?.let { throw McpException(it.optString("message", it.toString())) }
                return msg.optJSONObject("result")
            }
        } catch (e: IOException) {
            throw McpException("Network error: ${e.message}")
        } catch (e: org.json.JSONException) {
            throw McpException("Invalid JSON from server: ${e.message}")
        }
    }

    private fun sseFor(body: String, id: Int): JSONObject? {
        for (event in body.split("\n\n")) {
            val data = event.lines().filter { it.startsWith("data:") }.joinToString("\n") { it.removePrefix("data:").trim() }
            if (data.isBlank()) continue
            val msg = try {
                JSONObject(data)
            } catch (_: Exception) {
                continue
            }
            if (msg.opt("id")?.toString() == id.toString()) return msg
        }
        return null
    }
}

/** Wraps one remote MCP tool as a local [Tool]. */
class McpTool(
    private val client: McpClient,
    private val info: McpToolInfo,
    prefix: String,
    override val risk: Risk,
) : Tool {
    override val name: String = sanitize("${prefix}__${info.name}")
    override val description: String = info.description.ifBlank { info.name }
    override val parameters: JSONObject = info.inputSchema
    override suspend fun run(args: JSONObject): ToolResult = client.callTool(info.name, args)

    companion object {
        /** Provider tool names must match [a-zA-Z0-9_-]{1,64}. */
        fun sanitize(raw: String): String = raw.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64)

        private val dangerous = Regex("delete|remove|unfollow|send|pay|transfer|purchase|buy|reset|wipe", RegexOption.IGNORE_CASE)
        fun defaultRisk(toolName: String): Risk = if (dangerous.containsMatchIn(toolName)) Risk.CONFIRM else Risk.SAFE

        suspend fun discover(
            client: McpClient,
            prefix: String,
            riskFor: (String) -> Risk = ::defaultRisk,
        ): List<Tool> = client.listTools().map { McpTool(client, it, prefix, riskFor(it.name)) }
    }
}
