package com.example.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class McpServerConfig(val name: String, val url: String, val bearerToken: String = "")

/** Small SharedPreferences-backed settings for the system agent. */
class AgentConfig(context: Context) {
    private val prefs = context.getSharedPreferences("kodrix_agent", Context.MODE_PRIVATE)

    /** Optional stronger model (same provider/key as the first usable provider) used when the cheap one struggles. */
    var strongModel: String
        get() = prefs.getString("strong_model", "") ?: ""
        set(v) = prefs.edit().putString("strong_model", v.trim()).apply()

    /** BCP-47 tag for speech recognition, e.g. "en-US" or "ur-PK". Blank means device default. */
    var voiceLanguage: String
        get() = prefs.getString("voice_lang", "") ?: ""
        set(v) = prefs.edit().putString("voice_lang", v.trim()).apply()

    var speakReplies: Boolean
        get() = prefs.getBoolean("speak_replies", true)
        set(v) = prefs.edit().putBoolean("speak_replies", v).apply()

    var mcpServers: List<McpServerConfig>
        get() = parseServers(prefs.getString("mcp_servers", "[]") ?: "[]")
        set(v) = prefs.edit().putString("mcp_servers", serializeServers(v)).apply()

    companion object {
        fun parseServers(raw: String): List<McpServerConfig> = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url")
                if (url.isBlank()) null else McpServerConfig(o.optString("name").ifBlank { "server$i" }, url, o.optString("token"))
            }
        } catch (e: Exception) {
            emptyList()
        }

        fun serializeServers(list: List<McpServerConfig>): String = JSONArray().also { arr ->
            list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url).put("token", it.bearerToken)) }
        }.toString()
    }
}
