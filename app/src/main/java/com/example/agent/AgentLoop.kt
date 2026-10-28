package com.example.agent

import org.json.JSONObject

sealed interface AgentEvent {
    data class ToolStart(val name: String, val args: String) : AgentEvent
    data class ToolEnd(val name: String, val result: ToolResult) : AgentEvent
    data class Escalated(val toLabel: String, val reason: String) : AgentEvent
}

data class AgentResult(
    val text: String,
    val messages: List<ChatMessage>,
    val steps: Int,
    val modelUsed: String,
    val ok: Boolean,
)

/**
 * Tool-calling loop with the guard rails cheap models need:
 *  - unknown tool names and invalid JSON arguments come back as errors the model can correct
 *  - identical repeated calls are cut off and trigger escalation
 *  - consecutive tool failures or provider failures escalate to the next (stronger) model tier
 *  - risky tools wait for explicit user confirmation
 *
 * [tiers] is ordered cheapest first. The loop starts on tiers[0] and only moves up, never down.
 */
class AgentLoop(
    private val tiers: List<LlmClient>,
    private val registry: ToolRegistry,
    private val systemPrompt: String,
    private val confirm: suspend (toolName: String, argsJson: String) -> Boolean = { _, _ -> false },
    private val onEvent: (AgentEvent) -> Unit = {},
    private val maxSteps: Int = 20,
    private val repeatLimit: Int = 3,
    private val failuresBeforeEscalation: Int = 3,
) {
    init {
        require(tiers.isNotEmpty()) { "At least one model tier is required" }
    }

    suspend fun run(userText: String, history: List<ChatMessage> = emptyList()): AgentResult {
        val messages = mutableListOf<ChatMessage>()
        messages += ChatMessage("system", systemPrompt)
        messages += history.filter { it.role != "system" }
        messages += ChatMessage("user", userText)

        var tier = 0
        var consecutiveToolFailures = 0
        var providerFailures = 0
        val callCounts = HashMap<String, Int>()
        val tools = registry.all

        fun escalate(reason: String): Boolean {
            if (tier >= tiers.lastIndex) return false
            tier++
            providerFailures = 0
            consecutiveToolFailures = 0
            onEvent(AgentEvent.Escalated(tiers[tier].label, reason))
            return true
        }

        var step = 0
        while (step < maxSteps) {
            step++
            val reply = try {
                tiers[tier].chat(messages, tools)
            } catch (e: LlmException) {
                providerFailures++
                if (providerFailures >= 2 || !e.retryable) {
                    if (!escalate("model failed: ${e.message}")) {
                        return AgentResult("The model failed: ${e.message}", messages, step, tiers[tier].label, false)
                    }
                }
                continue
            }
            providerFailures = 0

            if (reply.toolCalls.isEmpty()) {
                val text = reply.text.orEmpty()
                messages += ChatMessage("assistant", text)
                return AgentResult(text, messages, step, tiers[tier].label, true)
            }

            messages += ChatMessage("assistant", reply.text, reply.toolCalls)
            var loopDetected = false
            for (call in reply.toolCalls) {
                val sig = call.name + "|" + normalizeArgs(call.argumentsJson)
                val n = (callCounts[sig] ?: 0) + 1
                callCounts[sig] = n
                val result = if (n >= repeatLimit) {
                    loopDetected = true
                    ToolResult(
                        "You have called ${call.name} with these exact arguments $n times. " +
                            "Do not repeat it. Try a different approach or give your final answer.",
                        isError = true,
                    )
                } else {
                    execute(call)
                }
                consecutiveToolFailures = if (result.isError) consecutiveToolFailures + 1 else 0
                messages += ChatMessage("tool", result.text, toolCallId = call.id)
            }
            if (loopDetected) {
                escalate("repeated identical tool call")
                callCounts.clear()
            } else if (consecutiveToolFailures >= failuresBeforeEscalation) {
                escalate("$consecutiveToolFailures tool errors in a row")
            }
        }
        return AgentResult(
            "I stopped after $maxSteps steps without finishing.",
            messages, step, tiers[tier].label, false,
        )
    }

    private suspend fun execute(call: ToolCall): ToolResult {
        val tool = registry[call.name]
            ?: return ToolResult(
                "Unknown tool '${call.name}'. Available tools: ${registry.names.joinToString(", ")}.",
                isError = true,
            )
        val args = try {
            JSONObject(call.argumentsJson)
        } catch (e: Exception) {
            return ToolResult("Arguments for ${call.name} were not valid JSON: ${e.message}", isError = true)
        }
        if (tool.risk == Risk.CONFIRM) {
            val approved = try {
                confirm(call.name, call.argumentsJson)
            } catch (e: Exception) {
                false
            }
            if (!approved) return ToolResult("The user declined this action. Do not retry it.", isError = true)
        }
        onEvent(AgentEvent.ToolStart(call.name, call.argumentsJson))
        val result = try {
            tool.run(args)
        } catch (e: Exception) {
            ToolResult("Tool ${call.name} crashed: ${e.message}", isError = true)
        }
        onEvent(AgentEvent.ToolEnd(call.name, result))
        return result
    }

    private fun normalizeArgs(raw: String): String = try {
        // Key order independent so {"a":1,"b":2} and {"b":2,"a":1} count as the same call.
        val o = JSONObject(raw)
        o.keys().asSequence().sorted().joinToString(",") { "$it=${o.get(it)}" }
    } catch (e: Exception) {
        raw.trim()
    }
}
