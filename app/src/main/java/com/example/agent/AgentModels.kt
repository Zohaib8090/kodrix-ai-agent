package com.example.agent

import org.json.JSONObject

/** A tool invocation requested by the model. [argumentsJson] is the raw string the model produced. */
data class ToolCall(val id: String, val name: String, val argumentsJson: String)

/** One message in the conversation, in the OpenAI-compatible chat shape. */
data class ChatMessage(
    val role: String, // system | user | assistant | tool
    val content: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
)

/** What a model returned for one request: text, tool calls, or both. */
data class LlmReply(val text: String?, val toolCalls: List<ToolCall>)

/** SAFE tools run immediately. CONFIRM tools wait for the user to approve each call. */
enum class Risk { SAFE, CONFIRM }

data class ToolResult(val text: String, val isError: Boolean = false)

/** Something the agent can do. Implementations must never throw for bad input; return an error result. */
interface Tool {
    val name: String
    val description: String

    /** JSON Schema object describing the arguments. */
    val parameters: JSONObject
    val risk: Risk get() = Risk.SAFE
    suspend fun run(args: JSONObject): ToolResult
}

class ToolRegistry {
    private val tools = linkedMapOf<String, Tool>()

    fun register(tool: Tool): ToolRegistry {
        tools[tool.name] = tool
        return this
    }

    fun registerAll(list: Iterable<Tool>): ToolRegistry {
        list.forEach { register(it) }
        return this
    }

    operator fun get(name: String): Tool? = tools[name]
    val names: List<String> get() = tools.keys.toList()
    val all: List<Tool> get() = tools.values.toList()
}

/** Thrown by [LlmClient] for transport errors, HTTP errors and empty completions. */
class LlmException(message: String, val retryable: Boolean = true) : Exception(message)

interface LlmClient {
    val label: String
    suspend fun chat(messages: List<ChatMessage>, tools: List<Tool>): LlmReply
}
