package com.example.agent

import android.content.Context
import com.example.MyApplication
import com.example.agent.mcp.McpClient
import com.example.agent.mcp.McpTool
import com.example.agent.memory.MemoryStore
import com.example.agent.memory.MemoryTool
import com.example.agent.tools.DialTool
import com.example.agent.tools.DraftSmsTool
import com.example.agent.tools.GetTimeTool
import com.example.agent.tools.LinuxShellTool
import com.example.agent.tools.OpenAppTool
import com.example.agent.tools.OpenUrlTool
import com.example.agent.tools.SetAlarmTool
import com.example.data.model.AuthStyle
import com.example.data.model.ProviderConfig
import com.example.linux.LinuxEnvironment
import org.json.JSONObject
import java.io.File

class AgentSetupException(message: String) : Exception(message)

data class BuiltAgent(val loop: AgentLoop, val notes: List<String>)

object AgentFactory {

    const val SYSTEM_PROMPT = """You are Kodrix, a personal assistant running on the user's Android phone. The user speaks to you by voice, so keep replies short and natural, as you would say them aloud.

Rules:
- Use tools to act. Never claim you did something unless a tool result confirms it.
- If a tool fails, read the error, fix the arguments, and try once more. If it still fails, tell the user plainly what went wrong.
- Only call tools that exist. Do not invent tool names or arguments.
- For anything irreversible or risky, the system asks the user to confirm. If the user declines, stop and do not retry.
- Check your memory at the start of a task when it could help, and save durable facts and preferences the user tells you.
- Never reveal API keys or other secrets."""

    /** Picks the model tiers (cheapest first) from the providers the user already configured. */
    internal fun tiersFrom(providers: List<ProviderConfig>, strongModel: String): List<LlmClient> {
        val usable = providers
            .filter { it.isEnabled && it.apiKey.isNotBlank() && it.apiKey != "dummy_api_key" && it.baseUrl.isNotBlank() && it.authStyle == AuthStyle.BEARER }
            .sortedByDescending { it.isPrimary }
        if (usable.isEmpty()) return emptyList()
        val first = usable.first()
        val tiers = mutableListOf<LlmClient>(client(first, first.defaultModel))
        if (strongModel.isNotBlank()) {
            tiers += client(first, strongModel)
        } else if (usable.size > 1) {
            tiers += client(usable[1], usable[1].defaultModel)
        }
        return tiers
    }

    private fun client(p: ProviderConfig, model: String): LlmClient {
        val extra = p.customPayloadJson.takeIf { it.isNotBlank() }?.let {
            try { JSONObject(it) } catch (e: Exception) { null }
        }
        return OpenAiCompatLlm(p.baseUrl, p.apiKey, model.ifBlank { p.defaultModel }, extra)
    }

    suspend fun build(
        context: Context,
        confirm: suspend (toolName: String, argsJson: String) -> Boolean,
        onEvent: (AgentEvent) -> Unit,
    ): BuiltAgent {
        val app = context.applicationContext as MyApplication
        val config = AgentConfig(context)
        val tiers = tiersFrom(app.aiProviderRepository.getUsableProviders(), config.strongModel)
        if (tiers.isEmpty()) {
            throw AgentSetupException("No AI provider is ready. Add an API key under Settings, AI and Models.")
        }

        val notes = mutableListOf<String>()
        val registry = ToolRegistry()
        registry.register(GetTimeTool())
        registry.register(OpenAppTool(context))
        registry.register(SetAlarmTool(context))
        registry.register(OpenUrlTool(context))
        registry.register(DialTool(context))
        registry.register(DraftSmsTool(context))

        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "x86_64" } ?: ""
        val linux = LinuxEnvironment(
            context.filesDir, File(context.applicationInfo.nativeLibraryDir), abi,
        ) { path -> context.assets.open(path) }
        if (linux.isSupported) registry.register(LinuxShellTool(linux))

        val memory = MemoryStore(File(context.filesDir, "agent_memory"))
        registry.register(MemoryTool(memory))

        for (server in config.mcpServers) {
            try {
                val client = McpClient(server.url, server.bearerToken.ifBlank { null })
                registry.registerAll(McpTool.discover(client, server.name))
            } catch (e: Exception) {
                notes += "MCP server '${server.name}' unavailable: ${e.message}"
            }
        }

        val index = memory.index()
        val prompt = SYSTEM_PROMPT + if (index.isBlank()) "\n\nMemory is empty." else "\n\nMemory files:\n$index"
        return BuiltAgent(AgentLoop(tiers, registry, prompt, confirm, onEvent), notes)
    }
}
