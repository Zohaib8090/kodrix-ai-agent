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
    override suspend fun callAiModel(systemPrompt: String, userPrompt: String): Result<String> = withContext(Dispatchers.IO) {
        val model = config.defaultModel.ifEmpty { "gemini-2.5-flash" }
        val key = config.apiKey.trim()
        if (key.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API key is required"))
        }
        try {
            val base = config.baseUrl.ifEmpty { "https://generativelanguage.googleapis.com" }
            val url = if (base.endsWith("/")) "${base}v1beta/models/$model:generateContent?key=$key" else "$base/v1beta/models/$model:generateContent?key=$key"
            val body = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "$systemPrompt\n\n$userPrompt")
                            })
                        }
                        put("parts", parts)
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

            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val respStr = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(respStr)
                val candidates = json.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: ""
                Result.success(text)
            } else {
                Result.failure(Exception("Gemini error: HTTP ${response.code} $respStr"))
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
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${config.apiKey.trim()}"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
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
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }

    override suspend fun fetchAvailableModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (config.apiKey.isBlank()) return@withContext Result.failure(Exception("Gemini API key is required"))
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${config.apiKey.trim()}"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val respStr = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}: $respStr"))
            val modelsArr = JSONObject(respStr).optJSONArray("models")
            val models = mutableListOf<String>()
            if (modelsArr != null) {
                for (i in 0 until modelsArr.length()) {
                    val name = modelsArr.optJSONObject(i)?.optString("name")?.removePrefix("models/") ?: continue
                    if (name.isNotBlank()) models.add(name)
                }
            }
            Result.success(models.sorted())
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
        try {
            val request = buildAuthorizedRequest("https://api.openai.com/v1/models")
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                VerifyResult(providerId, true, "Verified: OpenAI GPT models ready")
            } else {
                VerifyResult(providerId, false, "Verification failed (HTTP ${response.code})")
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }
}

class AnthropicProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override suspend fun callAiModel(systemPrompt: String, userPrompt: String): Result<String> = withContext(Dispatchers.IO) {
        val model = config.defaultModel.ifEmpty { "claude-3-5-sonnet-20241022" }
        val key = config.apiKey.trim()
        if (key.isBlank()) {
            return@withContext Result.failure(Exception("Anthropic API key is required"))
        }
        try {
            val base = config.baseUrl.ifEmpty { "https://api.anthropic.com/v1" }
            val url = if (base.endsWith("/")) "${base}messages" else "$base/messages"
            val body = JSONObject().apply {
                put("model", model)
                put("system", systemPrompt)
                val msgs = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                }
                put("messages", msgs)

                if (config.thinkingEnabled) {
                    val budget = when (config.thinkingLevel.lowercase()) {
                        "low" -> 1024
                        "medium" -> 2048
                        "high" -> 4096
                        "ultra" -> 8192
                        "adaptive" -> 2048
                        else -> config.thinkingLevel.toIntOrNull() ?: config.thinkingBudgetTokens
                    }.coerceAtLeast(1024)
                    put("max_tokens", budget + 4096)
                    put("thinking", JSONObject().apply {
                        put("type", "enabled")
                        put("budget_tokens", budget)
                    })
                } else {
                    put("max_tokens", 4096)
                }
            }
            mergeCustomPayload(body)

            val request = Request.Builder()
                .url(url)
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val respStr = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(respStr)
                val contentArray = json.optJSONArray("content")
                // With thinking, Anthropic returns thinking block first, then text block
                var text = ""
                if (contentArray != null) {
                    for (i in 0 until contentArray.length()) {
                        val block = contentArray.optJSONObject(i)
                        if (block?.optString("type") == "text") {
                            text = block.optString("text", "")
                            break
                        }
                    }
                    if (text.isEmpty() && contentArray.length() > 0) {
                        text = contentArray.optJSONObject(contentArray.length() - 1)?.optString("text") ?: ""
                    }
                }
                Result.success(text)
            } else {
                Result.failure(Exception("Claude error: HTTP ${response.code} $respStr"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext VerifyResult(providerId, false, "Anthropic API key is required")
        }
        try {
            val body = JSONObject().apply {
                put("model", "claude-3-haiku-20240307")
                put("max_tokens", 1)
                val msgs = org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "ping")
                    })
                }
                put("messages", msgs)
            }
            val request = buildAuthorizedRequest("https://api.anthropic.com/v1/messages", "POST", body.toString())
                .newBuilder()
                .header("anthropic-version", "2023-06-01")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                VerifyResult(providerId, true, "Verified: Claude models active")
            } else {
                VerifyResult(providerId, false, "HTTP ${response.code}: ${response.message}")
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }
}

class DeepSeekProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext VerifyResult(providerId, false, "DeepSeek API key is required")
        }
        try {
            val request = buildAuthorizedRequest("https://api.deepseek.com/user/balance")
            val response = client.newCall(request).execute()
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
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }
}

class OpenRouterProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext VerifyResult(providerId, false, "OpenRouter API key is required")
        }
        try {
            val request = buildAuthorizedRequest("https://openrouter.ai/api/v1/auth/key")
            val response = client.newCall(request).execute()
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
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }
}

class GroqProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext VerifyResult(providerId, false, "Groq API key is required")
        }
        try {
            val request = buildAuthorizedRequest("https://api.groq.com/openai/v1/models")
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                VerifyResult(providerId, true, "Verified: Groq LPUs ultra-fast inference ready")
            } else {
                VerifyResult(providerId, false, "HTTP ${response.code}: Invalid Groq key")
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }
}

class MistralProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) return@withContext VerifyResult(providerId, false, "Mistral API key required")
        try {
            val request = buildAuthorizedRequest("https://api.mistral.ai/v1/models")
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                VerifyResult(providerId, true, "Verified: Mistral models operational")
            } else {
                VerifyResult(providerId, false, "HTTP ${response.code}: Verification failed")
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }
}

class TogetherAiProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) return@withContext VerifyResult(providerId, false, "Together.ai API key required")
        try {
            val request = buildAuthorizedRequest("https://api.together.xyz/v1/models")
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                VerifyResult(providerId, true, "Verified: Together AI open models operational")
            } else {
                VerifyResult(providerId, false, "HTTP ${response.code}: Verification failed")
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }
}

class FireworksProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) return@withContext VerifyResult(providerId, false, "Fireworks API key required")
        try {
            val request = buildAuthorizedRequest("https://api.fireworks.ai/inference/v1/models")
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                VerifyResult(providerId, true, "Verified: Fireworks models operational")
            } else {
                VerifyResult(providerId, false, "HTTP ${response.code}: Verification failed")
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }
}

class XaiProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) return@withContext VerifyResult(providerId, false, "xAI API key required")
        try {
            val request = buildAuthorizedRequest("https://api.x.ai/v1/models")
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                VerifyResult(providerId, true, "Verified: xAI Grok inference ready")
            } else {
                VerifyResult(providerId, false, "HTTP ${response.code}: Verification failed")
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }
}

class CerebrasProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) return@withContext VerifyResult(providerId, false, "Cerebras API key required")
        try {
            val request = buildAuthorizedRequest("https://api.cerebras.ai/v1/models")
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                VerifyResult(providerId, true, "Verified: Cerebras wafer-scale engine connected")
            } else {
                VerifyResult(providerId, false, "HTTP ${response.code}: Verification failed")
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }
}

class LocalTunnelProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        val base = config.baseUrl.ifEmpty { "http://10.0.2.2:11434" }
        try {
            // Check Ollama tags first
            val urlOllama = if (base.endsWith("/")) "${base}api/tags" else "$base/api/tags"
            val reqOllama = Request.Builder().url(urlOllama).get().build()
            val respOllama = client.newCall(reqOllama).execute()
            if (respOllama.isSuccessful) {
                return@withContext VerifyResult(providerId, true, "Verified: Ollama / Local server active ($base)")
            }

            // Check v1/models
            val urlModels = if (base.endsWith("/")) "${base}v1/models" else "$base/v1/models"
            val reqModels = Request.Builder().url(urlModels).get().build()
            val respModels = client.newCall(reqModels).execute()
            if (respModels.isSuccessful) {
                return@withContext VerifyResult(providerId, true, "Verified: Local server active ($base)")
            }

            VerifyResult(providerId, false, "Cannot reach $base (HTTP ${respModels.code})")
        } catch (e: Exception) {
            // Local endpoints may be offline during emulator testing; provide informative message
            VerifyResult(providerId, false, "Endpoint offline (${e.localizedMessage?.take(40)}). Make sure local server or Cloudflare tunnel is running.")
        }
    }
}

class OpenAiCompatibleProvider(config: ProviderConfig) : BaseAiProvider(config) {
    override suspend fun verify(): VerifyResult = withContext(Dispatchers.IO) {
        val base = config.baseUrl.ifEmpty { "https://api.openai.com/v1" }
        try {
            val url = if (base.endsWith("/")) "${base}models" else "$base/models"
            val request = buildAuthorizedRequest(url)
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                VerifyResult(providerId, true, "Verified: Custom OpenAI-compatible endpoint reachable")
            } else {
                VerifyResult(providerId, false, "Endpoint returned HTTP ${response.code}")
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
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
