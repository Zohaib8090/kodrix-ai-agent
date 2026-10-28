package com.example.agent

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

/**
 * Native tool-calling client for any OpenAI-compatible /chat/completions endpoint
 * (OpenRouter, OpenAI, DeepSeek, local servers...).
 *
 * [extraPayload] is merged into every request body, e.g. for OpenRouter provider pinning:
 * {"provider":{"order":["DeepSeek"],"allow_fallbacks":true}}
 */
class OpenAiCompatLlm(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val extraPayload: JSONObject? = null,
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val temperature: Double = 0.2,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) : LlmClient {

    override val label: String get() = model
    private val json = "application/json; charset=utf-8".toMediaType()

    override suspend fun chat(messages: List<ChatMessage>, tools: List<Tool>): LlmReply =
        withContext(Dispatchers.IO) {
            val body = buildBody(messages, tools)
            val req = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/chat/completions")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .apply { extraHeaders.forEach { (k, v) -> header(k, v) } }
                .post(body.toString().toRequestBody(json))
                .build()
            val text: String
            try {
                http.newCall(req).execute().use { resp ->
                    text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val retryable = resp.code == 429 || resp.code >= 500
                        throw LlmException("HTTP ${resp.code}: ${text.take(300)}", retryable)
                    }
                }
            } catch (e: IOException) {
                throw LlmException("Network error: ${e.message}")
            }
            parse(text)
        }

    internal fun buildBody(messages: List<ChatMessage>, tools: List<Tool>): JSONObject {
        val body = JSONObject()
        body.put("model", model)
        body.put("temperature", temperature)
        body.put("messages", JSONArray().also { arr -> messages.forEach { arr.put(messageJson(it)) } })
        if (tools.isNotEmpty()) {
            body.put("tools", JSONArray().also { arr ->
                tools.forEach { t ->
                    arr.put(
                        JSONObject()
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject()
                                    .put("name", t.name)
                                    .put("description", t.description)
                                    .put("parameters", t.parameters)
                            )
                    )
                }
            })
            body.put("tool_choice", "auto")
        }
        extraPayload?.let { extra -> extra.keys().forEach { k -> body.put(k, extra.get(k)) } }
        return body
    }

    private fun messageJson(m: ChatMessage): JSONObject {
        val o = JSONObject().put("role", m.role)
        when (m.role) {
            "assistant" -> {
                o.put("content", m.content ?: JSONObject.NULL)
                if (m.toolCalls.isNotEmpty()) {
                    o.put("tool_calls", JSONArray().also { arr ->
                        m.toolCalls.forEach { c ->
                            arr.put(
                                JSONObject().put("id", c.id).put("type", "function").put(
                                    "function",
                                    JSONObject().put("name", c.name).put("arguments", c.argumentsJson)
                                )
                            )
                        }
                    })
                }
            }
            "tool" -> {
                o.put("tool_call_id", m.toolCallId)
                o.put("content", m.content ?: "")
            }
            else -> o.put("content", m.content ?: "")
        }
        return o
    }

    internal fun parse(raw: String): LlmReply {
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw LlmException("Response was not JSON: ${raw.take(200)}")
        }
        root.optJSONObject("error")?.let {
            throw LlmException("Provider error: ${it.optString("message", it.toString()).take(300)}")
        }
        val msg = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: throw LlmException("Response had no choices[0].message")
        val content = msg.optString("content", "").takeIf { it.isNotBlank() && it != "null" }
        val calls = mutableListOf<ToolCall>()
        msg.optJSONArray("tool_calls")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val fn = c.optJSONObject("function") ?: continue
                val name = fn.optString("name")
                if (name.isBlank()) continue
                calls += ToolCall(
                    id = c.optString("id").ifBlank { "call_$i" },
                    name = name,
                    argumentsJson = fn.optString("arguments", "{}").ifBlank { "{}" },
                )
            }
        }
        if (content == null && calls.isEmpty()) {
            // Some cheap hosts return empty completions; surface it so the loop can retry/escalate.
            throw LlmException("Empty completion (no text and no tool calls)")
        }
        return LlmReply(content, calls)
    }
}
