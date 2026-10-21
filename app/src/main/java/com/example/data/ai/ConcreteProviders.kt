@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")
package com.example.data.ai

import com.example.data.model.ProviderConfig
import com.example.data.model.VerifyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GeminiProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override val defaultBaseUrl = "https://generativelanguage.googleapis.com"

    /** "<base>/v1beta", unless the configured base URL already names an API version. */
    private fun apiRoot(): String {
        val base = baseUrlOr(defaultBaseUrl)
        return if (base.endsWith("/v1beta") || base.endsWith("/v1")) base else "$base/v1beta"
    }

    // The key goes in a header rather than "?key=" so it never shows up in logged URLs.
    private fun geminiRequest(url: String): Request.Builder =
        Request.Builder().url(url).header("x-goog-api-key", config.apiKey.trim())

    override suspend fun callAiModel(systemPrompt: String, messages: List<ChatTurn>): Result<String> = withContext(Dispatchers.IO) {
        val model = config.defaultModel.ifEmpty { "gemini-2.5-flash" }.removePrefix("models/")
        if (config.apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API key is required"))
        }
        try {
            val url = "${apiRoot()}/models/$model:generateContent"
            val body = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
                val contents = JSONArray()
                ChatTurn.normalize(messages).forEach { turn ->
                    contents.put(JSONObject().apply {
                        put("role", if (turn.role == ChatTurn.Role.USER) "user" else "model")
                        put("parts", JSONArray().put(JSONObject().put("text", turn.content)))
                    })
                }
                put("contents", contents)

                if (config.thinkingEnabled) {
                    val budget = when (config.thinkingLevel.lowercase()) {
                        "low" -> 1024
                        "medium" -> 2048
                        "high" -> 4096
                        "ultra" -> 8192
                        "adaptive" -> -1
                        else -> config.thinkingLevel.toIntOrNull() ?: config.thinkingBudgetTokens
                    }
                    val genConfig = JSONObject()
                    if (budget > 0) {
                        genConfig.put("thinkingConfig", JSONObject().apply {
                            put("thinkingBudget", budget)
                        })
                    }
                    put("generationConfig", genConfig)
                }
            }
            mergeCustomPayload(body)

            val request = geminiRequest(url)
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("Gemini error: HTTP ${response.code} ${respStr.take(MAX_ERROR_BODY_CHARS)}"))
                }
                val json = JSONObject(respStr)
                val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
                if (candidate == null) {
                    val blockReason = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                    return@use Result.failure(Exception("Gemini returned no answer${if (blockReason.isNotBlank()) " (blocked: $blockReason)" else ""}."))
                }
                // Join every text part; thought summaries (thought = true) are not part of the answer.
                val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                val text = buildString {
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i) ?: continue
                            if (!part.optBoolean("thought", false)) append(part.optString("text", ""))
                        }
                    }
                }
                if (text.isBlank() && candidate.optString("finishReason") == "MAX_TOKENS") {
                    Result.failure(Exception("Gemini ran out of output tokens before answering."))
                } else {
                    Result.success(text)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext VerifyResult(providerId, false, "Gemini API key is required")
        }
        try {
            val request = geminiRequest("${apiRoot()}/models").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    VerifyResult(providerId, true, "Verified: Gemini API active & operational")
                } else {
                    val err = response.body?.string() ?: ""
                    val msg = if (response.code == 400 && err.contains("API_KEY_INVALID")) {
                        "Invalid API key (API_KEY_INVALID)"
                    } else {
                        "HTTP ${response.code}: $err".take(80)
                    }
                    VerifyResult(providerId, false, msg)
                }
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }

    override suspend fun fetchAvailableModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (config.apiKey.isBlank()) return@withContext Result.failure(Exception("Gemini API key is required"))
            val request = geminiRequest("${apiRoot()}/models?pageSize=1000").get().build()
            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${respStr.take(MAX_ERROR_BODY_CHARS)}"))
                }
                val modelsArr = JSONObject(respStr).optJSONArray("models")
                val models = mutableListOf<String>()
                if (modelsArr != null) {
                    for (i in 0 until modelsArr.length()) {
                        val name = modelsArr.optJSONObject(i)?.optString("name")?.removePrefix("models/") ?: continue
                        if (name.isNotBlank()) models.add(name)
                    }
                }
                Result.success(models.sorted())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class OpenAiProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext VerifyResult(providerId, false, "OpenAI API key is required")
        }
        verifyByGet("${openAiApiBase()}/models", "Verified: OpenAI GPT models ready") { code ->
            "Verification failed (HTTP $code)"
        }
    }
}

class AnthropicProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override val defaultBaseUrl = "https://api.anthropic.com/v1"

    private fun anthropicRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("x-api-key", config.apiKey.trim())
            .header("anthropic-version", ANTHROPIC_VERSION)

    override suspend fun callAiModel(systemPrompt: String, messages: List<ChatTurn>): Result<String> = withContext(Dispatchers.IO) {
        val model = config.defaultModel.ifEmpty { DEFAULT_MODEL }
        if (config.apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Anthropic API key is required"))
        }
        try {
            val base = baseUrlOr(defaultBaseUrl)
            val useServerFallback = base.startsWith("https://api.anthropic.com") && model in SERVER_FALLBACK_MODELS
            val body = JSONObject().apply {
                put("model", model)
                put("system", systemPrompt)
                val msgs = JSONArray()
                ChatTurn.normalize(messages).forEach { turn ->
                    msgs.put(JSONObject().apply {
                        put("role", if (turn.role == ChatTurn.Role.USER) "user" else "assistant")
                        put("content", turn.content)
                    })
                }
                put("messages", msgs)
                put("max_tokens", MAX_OUTPUT_TOKENS)

                if (config.thinkingEnabled) applyThinking(this, model)
                // If a safety classifier declines the request, retry server-side on the model
                // Anthropic recommends for that case instead of returning the refusal.
                if (useServerFallback) put("fallbacks", "default")
            }
            mergeCustomPayload(body)

            val request = anthropicRequest("$base/messages")
                .header("content-type", "application/json")
                .apply { if (useServerFallback) header("anthropic-beta", SERVER_FALLBACK_BETA) }
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("Claude error: HTTP ${response.code} ${respStr.take(MAX_ERROR_BODY_CHARS)}"))
                }
                val json = JSONObject(respStr)
                val stopReason = json.optString("stop_reason")
                if (stopReason == "refusal") {
                    return@use Result.failure(Exception("Claude declined this request. Try rephrasing it."))
                }
                // Thinking blocks come before the answer; the answer may span several text blocks.
                val contentArray = json.optJSONArray("content")
                val text = buildString {
                    if (contentArray != null) {
                        for (i in 0 until contentArray.length()) {
                            val block = contentArray.optJSONObject(i) ?: continue
                            if (block.optString("type") == "text") append(block.optString("text", ""))
                        }
                    }
                }
                if (text.isBlank() && stopReason == "max_tokens") {
                    Result.failure(Exception("Claude ran out of output tokens before answering."))
                } else {
                    Result.success(text)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Current Claude models use adaptive thinking with an effort level; a fixed `budget_tokens`
     * is rejected with HTTP 400. Older models (Claude 3.x, 4.0-4.5) still need a token budget,
     * and a numeric thinking level always means an explicit budget.
     */
    private fun applyThinking(body: JSONObject, model: String) {
        val level = config.thinkingLevel.lowercase()
        val explicitBudget = level.toIntOrNull()
        if (explicitBudget != null || isBudgetThinkingModel(model)) {
            val budget = (explicitBudget ?: when (level) {
                "low" -> 1024
                "high" -> 4096
                "ultra" -> 8192
                else -> 2048
            }).coerceAtLeast(1024)
            body.put("max_tokens", maxOf(MAX_OUTPUT_TOKENS, budget + 4096))
            body.put("thinking", JSONObject().apply {
                put("type", "enabled")
                put("budget_tokens", budget)
            })
        } else {
            body.put("thinking", JSONObject().put("type", "adaptive"))
            val effort = when (level) {
                "low", "medium", "high", "xhigh", "max" -> level
                "ultra" -> "max"
                else -> null // "adaptive": let the model use its default effort
            }
            if (effort != null) body.put("output_config", JSONObject().put("effort", effort))
        }
    }

    private fun isBudgetThinkingModel(model: String): Boolean =
        model.startsWith("claude-3") || Regex("""-4-[0-5](\b|$)|-4-\d{8}""").containsMatchIn(model)

    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext VerifyResult(providerId, false, "Anthropic API key is required")
        }
        try {
            // Listing models checks the key without generating (and paying for) any tokens.
            val request = anthropicRequest("${baseUrlOr(defaultBaseUrl)}/models?limit=1").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    VerifyResult(providerId, true, "Verified: Claude models active")
                } else {
                    VerifyResult(providerId, false, "HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }

    override suspend fun fetchAvailableModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (config.apiKey.isBlank()) return@withContext Result.failure(Exception("Anthropic API key is required"))
            val models = mutableListOf<String>()
            var afterId: String? = null
            // The Models API is paginated; a handful of pages covers every model.
            repeat(5) {
                val url = "${baseUrlOr(defaultBaseUrl)}/models?limit=1000" + (afterId?.let { "&after_id=$it" } ?: "")
                client.newCall(anthropicRequest(url).get().build()).execute().use { response ->
                    val respStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}: ${respStr.take(MAX_ERROR_BODY_CHARS)}"))
                    }
                    val json = JSONObject(respStr)
                    val data = json.optJSONArray("data") ?: JSONArray()
                    for (i in 0 until data.length()) {
                        data.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let { models.add(it) }
                    }
                    afterId = json.optString("last_id").takeIf { json.optBoolean("has_more", false) && it.isNotBlank() }
                }
                if (afterId == null) return@withContext Result.success(models)
            }
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        // Non-streaming requests: large enough for multi-file projects, small enough to avoid HTTP timeouts.
        private const val MAX_OUTPUT_TOKENS = 16000
        private const val SERVER_FALLBACK_BETA = "server-side-fallback-2026-07-01"
        private val SERVER_FALLBACK_MODELS = setOf("claude-opus-5-5", "claude-opus-5", "claude-fable-5-1", "claude-sonnet-5-5")
    }
}

class DeepSeekProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override val defaultBaseUrl = "https://api.deepseek.com"

    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext VerifyResult(providerId, false, "DeepSeek API key is required")
        }
        try {
            val request = buildAuthorizedRequest("https://api.deepseek.com/user/balance")
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    var balanceInfo = "DeepSeek API active"
                    try {
                        val json = JSONObject(bodyStr)
                        val isAvailable = json.optBoolean("is_available", true)
                        val balances = json.optJSONArray("balance_infos")
                        if (balances != null && balances.length() > 0) {
                            val first = balances.getJSONObject(0)
                            val total = first.optString("total_balance", "")
                            val currency = first.optString("currency", "USD")
                            balanceInfo = "Verified: Balance $total $currency (Available: $isAvailable)"
                        }
                    } catch (_: Exception) {}
                    VerifyResult(providerId, true, balanceInfo)
                } else {
                    VerifyResult(providerId, false, "HTTP ${response.code}: Check API key")
                }
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }
}

class OpenRouterProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override val defaultBaseUrl = "https://openrouter.ai/api/v1"

    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext VerifyResult(providerId, false, "OpenRouter API key is required")
        }
        try {
            val request = buildAuthorizedRequest("https://openrouter.ai/api/v1/auth/key")
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    var msg = "Verified: OpenRouter active"
                    try {
                        val json = JSONObject(bodyStr).optJSONObject("data")
                        val usage = json?.optDouble("usage", 0.0) ?: 0.0
                        val limit = json?.optDouble("limit", 0.0) ?: 0.0
                        msg = "Verified: Usage $$usage / Limit: $$limit"
                    } catch (_: Exception) {}
                    VerifyResult(providerId, true, msg)
                } else {
                    VerifyResult(providerId, false, "HTTP ${response.code}: Invalid key")
                }
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }
}

class GroqProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override val defaultBaseUrl = "https://api.groq.com/openai/v1"

    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext VerifyResult(providerId, false, "Groq API key is required")
        }
        verifyByGet("${openAiApiBase()}/models", "Verified: Groq LPUs ultra-fast inference ready") { code ->
            "HTTP $code: Invalid Groq key"
        }
    }
}

class MistralProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override val defaultBaseUrl = "https://api.mistral.ai/v1"

    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) return@withContext VerifyResult(providerId, false, "Mistral API key required")
        verifyByGet("${openAiApiBase()}/models", "Verified: Mistral models operational") { code ->
            "HTTP $code: Verification failed"
        }
    }
}

class TogetherAiProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override val defaultBaseUrl = "https://api.together.xyz/v1"

    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) return@withContext VerifyResult(providerId, false, "Together.ai API key required")
        verifyByGet("${openAiApiBase()}/models", "Verified: Together AI open models operational") { code ->
            "HTTP $code: Verification failed"
        }
    }
}

class FireworksProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override val defaultBaseUrl = "https://api.fireworks.ai/inference/v1"

    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) return@withContext VerifyResult(providerId, false, "Fireworks API key required")
        verifyByGet("${openAiApiBase()}/models", "Verified: Fireworks models operational") { code ->
            "HTTP $code: Verification failed"
        }
    }
}

class XaiProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override val defaultBaseUrl = "https://api.x.ai/v1"

    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) return@withContext VerifyResult(providerId, false, "xAI API key required")
        verifyByGet("${openAiApiBase()}/models", "Verified: xAI Grok inference ready") { code ->
            "HTTP $code: Verification failed"
        }
    }
}

class CerebrasProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override val defaultBaseUrl = "https://api.cerebras.ai/v1"

    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) return@withContext VerifyResult(providerId, false, "Cerebras API key required")
        verifyByGet("${openAiApiBase()}/models", "Verified: Cerebras wafer-scale engine connected") { code ->
            "HTTP $code: Verification failed"
        }
    }
}

class LocalTunnelProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override val defaultBaseUrl = "http://10.0.2.2:11434"

    // Ollama and most local servers expose the OpenAI-compatible API under /v1.
    override fun openAiApiBase(): String {
        val base = baseUrlOr(defaultBaseUrl)
        return if (base.endsWith("/v1")) base else "$base/v1"
    }

    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        val base = baseUrlOr(defaultBaseUrl)
        try {
            // Check Ollama tags first
            val ollamaOk = client.newCall(buildAuthorizedRequest("${base.removeSuffix("/v1")}/api/tags")).execute().use { it.isSuccessful }
            if (ollamaOk) {
                return@withContext VerifyResult(providerId, true, "Verified: Ollama / Local server active ($base)")
            }

            // Check v1/models
            client.newCall(buildAuthorizedRequest("${openAiApiBase()}/models")).execute().use { response ->
                if (response.isSuccessful) {
                    VerifyResult(providerId, true, "Verified: Local server active ($base)")
                } else {
                    VerifyResult(providerId, false, "Cannot reach $base (HTTP ${response.code})")
                }
            }
        } catch (e: Exception) {
            // Local endpoints may be offline during emulator testing; provide informative message
            VerifyResult(providerId, false, "Endpoint offline (${e.localizedMessage?.take(40)}). Make sure local server or Cloudflare tunnel is running.")
        }
    }
}

class OpenAiCompatibleProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        verifyByGet("${openAiApiBase()}/models", "Verified: Custom OpenAI-compatible endpoint reachable") { code ->
            "Endpoint returned HTTP $code"
        }
    }
}

object AiProviderFactory {
    fun create(config: ProviderConfig): AiProvider {
        return when (val id = config.id) {
            "gemini" -> GeminiProvider(config)
            id if id.startsWith("custom_gemini") -> GeminiProvider(config)
            "openai" -> OpenAiProvider(config)
            "anthropic" -> AnthropicProvider(config)
            id if id.startsWith("custom_anthropic") -> AnthropicProvider(config)
            "deepseek" -> DeepSeekProvider(config)
            "openrouter" -> OpenRouterProvider(config)
            "groq" -> GroqProvider(config)
            "mistral" -> MistralProvider(config)
            "together" -> TogetherAiProvider(config)
            "fireworks" -> FireworksProvider(config)
            "xai" -> XaiProvider(config)
            "cerebras" -> CerebrasProvider(config)
            "local_tunnel" -> LocalTunnelProvider(config)
            else -> OpenAiCompatibleProvider(config)
        }
    }
}
