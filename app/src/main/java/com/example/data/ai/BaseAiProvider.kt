package com.example.data.ai

import com.example.data.model.AppContext
import com.example.data.model.AuthStyle
import com.example.data.model.CodeArtifact
import com.example.data.model.PlatformType
import com.example.data.model.ProviderConfig
import com.example.data.model.SourceFile
import com.example.data.model.VerifyResult
import com.example.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

abstract class BaseAiProvider(
    protected val config: ProviderConfig,
    protected val client: OkHttpClient = ApiClient.aiHttpClient
) : AiProvider {

    override val providerId: String get() = config.id
    override val name: String get() = config.name
    override val supportsVision: Boolean get() = config.supportsVision

    protected val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** Used when the provider has no base URL configured. */
    protected open val defaultBaseUrl: String = "https://api.openai.com/v1"

    /** Root of the OpenAI-compatible API ("<root>/chat/completions", "<root>/models"). */
    protected open fun openAiApiBase(): String = baseUrlOr(defaultBaseUrl)

    protected fun buildAuthorizedRequest(url: String, method: String = "GET", bodyJson: String? = null): Request {
        val apiKey = config.apiKey.trim()
        val finalUrl = if (config.authStyle == AuthStyle.QUERY_PARAM && apiKey.isNotBlank()) {
            url.toHttpUrlOrNull()?.newBuilder()?.setQueryParameter("key", apiKey)?.build()?.toString() ?: url
        } else {
            url
        }
        val builder = Request.Builder().url(finalUrl)
        val requestBody = bodyJson?.toRequestBody(jsonMediaType)

        when (config.authStyle) {
            AuthStyle.BEARER -> {
                if (apiKey.isNotBlank()) {
                    builder.header("Authorization", "Bearer $apiKey")
                }
            }
            AuthStyle.X_API_KEY -> {
                if (apiKey.isNotBlank()) {
                    builder.header("x-api-key", apiKey)
                }
            }
            AuthStyle.QUERY_PARAM, AuthStyle.NONE -> {
                // QUERY_PARAM is added to the URL above; NONE sends no credentials
            }
        }

        if (method == "POST") {
            builder.post(requestBody ?: "".toRequestBody(jsonMediaType))
        } else {
            builder.get()
        }

        return builder.build()
    }

    /**
     * GETs [url] with this provider's credentials and turns the status code into a VerifyResult.
     * Most OpenAI-style providers can be verified this way through their `/models` endpoint.
     */
    protected fun verifyByGet(url: String, okMessage: String, failMessage: (Int) -> String): VerifyResult {
        return try {
            client.newCall(buildAuthorizedRequest(url)).execute().use { response ->
                if (response.isSuccessful) {
                    VerifyResult(providerId, true, okMessage)
                } else {
                    VerifyResult(providerId, false, failMessage(response.code))
                }
            }
        } catch (e: Exception) {
            VerifyResult(providerId, false, "Connection error: ${e.localizedMessage}")
        }
    }

    /** Base URL without a trailing slash, falling back to [default] when none is configured. */
    protected fun baseUrlOr(default: String): String = config.baseUrl.trim().ifEmpty { default }.trimEnd('/')

    override suspend fun generateCode(prompt: String, context: AppContext): Result<CodeArtifact> = withContext(Dispatchers.IO) {
        try {
            if (config.apiKey.isBlank() && config.authStyle != AuthStyle.NONE) {
                return@withContext Result.failure(
                    Exception("API key is not configured for ${config.name}. Please configure your API key in Settings.")
                )
            }

            val aiResult = callAiModel(
                systemPrompt = buildSystemPrompt(context),
                messages = listOf(
                    ChatTurn.user(
                        "Generate application source code for: $prompt. App Name: ${context.appName}. " +
                            "Platform: ${context.platform}. Features: ${context.features.joinToString()}"
                    )
                )
            )

            if (aiResult.isSuccess) {
                val raw = aiResult.getOrThrow()
                val parsed = parseCodeFromResponse(raw, context)
                if (parsed.files.isEmpty()) {
                    val incomplete = AiResponseParser.parseIncompleteFilePaths(raw)
                    val reason = if (incomplete.isNotEmpty()) {
                        "The AI response was cut off before any file was complete (${incomplete.joinToString()})."
                    } else {
                        "The AI responded, but no code files could be parsed from the response."
                    }
                    Result.failure(Exception(reason))
                } else {
                    Result.success(parsed)
                }
            } else {
                Result.failure(aiResult.exceptionOrNull() ?: Exception("AI model call failed for ${config.name}."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fixError(
        log: String,
        sourceFiles: List<SourceFile>,
        screenshot: ByteArray?
    ): Result<CodeArtifact> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildString {
                append("The build failed with the following log:\n\n")
                append(log.takeLast(8000))
                append("\n\nProject files:\n")
                append(ProjectContextBuilder.describeFiles(sourceFiles, maxChars = 40_000))
                append("\n\nReturn every file you change in full using the FILE / ENDFILE format. ")
                append("Do not return files you did not change.")
            }

            val response = callAiModel(
                systemPrompt = "You are an automated Android & Web self-healing compiler assistant. " +
                    "Find the root cause of the error and respond with corrected code files.\n\n" + FILE_FORMAT_INSTRUCTIONS,
                messages = listOf(ChatTurn.user(prompt))
            )

            val raw = response.getOrElse { return@withContext Result.failure(it) }
            val fixedFiles = AiResponseParser.parseFiles(raw)
            if (fixedFiles.isEmpty()) {
                return@withContext Result.failure(Exception("The AI did not return any corrected files."))
            }
            val explanation = AiResponseParser.extractConversationalText(raw)
                .ifBlank { "Updated ${fixedFiles.size} file(s) to fix the build error." }
            Result.success(CodeArtifact(fixedFiles, explanation))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    protected fun mergeCustomPayload(body: JSONObject) {
        if (config.customPayloadJson.isNotBlank()) {
            try {
                val customObj = JSONObject(config.customPayloadJson.trim())
                val keys = customObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    body.put(key, customObj.get(key))
                }
            } catch (_: Exception) {}
        }
    }

    /** Sends [messages] (oldest first, ending with the user) and returns the model's text reply. */
    protected open suspend fun callAiModel(systemPrompt: String, messages: List<ChatTurn>): Result<String> {
        // Default OpenAI-compatible chat completion
        val model = config.defaultModel.ifEmpty { "gpt-4o" }
        val url = "${openAiApiBase()}/chat/completions"

        val body = JSONObject().apply {
            put("model", model)
            val messageArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                ChatTurn.normalize(messages).forEach { turn ->
                    put(JSONObject().apply {
                        put("role", if (turn.role == ChatTurn.Role.USER) "user" else "assistant")
                        put("content", turn.content)
                    })
                }
            }
            put("messages", messageArray)
            put("temperature", 0.3)

            // Thinking / Reasoning parameters for OpenAI, DeepSeek, Groq, OpenRouter, etc.
            if (config.thinkingEnabled) {
                when (config.thinkingLevel.lowercase()) {
                    "low", "medium", "high" -> put("reasoning_effort", config.thinkingLevel.lowercase())
                    "ultra" -> put("reasoning_effort", "high")
                    "adaptive" -> put("reasoning_effort", "medium")
                    else -> {
                        val asInt = config.thinkingLevel.toIntOrNull()
                        if (asInt != null) {
                            put("max_completion_tokens", asInt)
                        } else {
                            put("reasoning_effort", config.thinkingLevel)
                        }
                    }
                }
            }
        }
        mergeCustomPayload(body)

        val request = buildAuthorizedRequest(url, "POST", body.toString())
        return client.newCall(request).execute().use { response ->
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@use Result.failure(Exception("AI API error: HTTP ${response.code} ${respBody.take(MAX_ERROR_BODY_CHARS)}"))
            }
            try {
                val choice = JSONObject(respBody).optJSONArray("choices")?.optJSONObject(0)
                val content = messageText(choice?.optJSONObject("message"))
                if (content.isBlank() && choice?.optString("finish_reason") == "length") {
                    Result.failure(Exception("The model ran out of output tokens before answering. Try a shorter request or a model with a larger output limit."))
                } else {
                    Result.success(content)
                }
            } catch (e: Exception) {
                Result.failure(Exception("Invalid JSON from API: ${respBody.take(MAX_ERROR_BODY_CHARS)}", e))
            }
        }
    }

    /** Reads `message.content`, which may be null (reasoning or tool-only replies) or an array of parts. */
    private fun messageText(message: JSONObject?): String {
        if (message == null || message.isNull("content")) return ""
        return when (val content = message.opt("content")) {
            is String -> content
            is JSONArray -> (0 until content.length())
                .mapNotNull { content.optJSONObject(it)?.optString("text")?.takeIf { text -> text.isNotEmpty() } }
                .joinToString("")
            else -> ""
        }
    }

    override suspend fun chat(systemPrompt: String, userPrompt: String): Result<String> =
        chat(systemPrompt, listOf(ChatTurn.user(userPrompt)))

    override suspend fun chat(systemPrompt: String, history: List<ChatTurn>): Result<String> = withContext(Dispatchers.IO) {
        try {
            callAiModel(systemPrompt, history)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchAvailableModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val url = "${openAiApiBase()}/models"
            client.newCall(buildAuthorizedRequest(url, "GET")).execute().use { response ->
                val respBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${respBody.take(MAX_ERROR_BODY_CHARS)}"))
                }

                val json = try { JSONObject(respBody) } catch (e: Exception) {
                    return@withContext Result.failure(Exception("Invalid JSON response: ${respBody.take(MAX_ERROR_BODY_CHARS)}"))
                }

                // Standard OpenAI format: { "data": [ { "id": "gpt-4o" }, ... ] }
                val dataArray = json.optJSONArray("data")
                if (dataArray != null) {
                    val models = mutableListOf<String>()
                    for (i in 0 until dataArray.length()) {
                        val id = dataArray.optJSONObject(i)?.optString("id") ?: continue
                        if (id.isNotBlank()) models.add(id)
                    }
                    return@withContext Result.success(models.sorted())
                }

                // Gemini / Ollama format: { "models": [ { "name": "models/gemini-1.5-pro" }, ... ] }
                val modelsArray = json.optJSONArray("models")
                if (modelsArray != null) {
                    val models = mutableListOf<String>()
                    for (i in 0 until modelsArray.length()) {
                        val obj = modelsArray.optJSONObject(i) ?: continue
                        val name = obj.optString("name").removePrefix("models/")
                        if (name.isNotBlank()) models.add(name)
                    }
                    return@withContext Result.success(models.sorted())
                }

                Result.failure(Exception("No models found in response"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildSystemPrompt(context: AppContext): String {
        val baseRole = if (context.platform == PlatformType.ANDROID) {
            "You are an expert Android developer specializing in Kotlin, Jetpack Compose, Material 3, and Room."
        } else {
            "You are a modern full-stack web developer specializing in ${context.framework?.displayName ?: "React and Vite"}."
        }
        return listOf(
            baseRole,
            "CRITICAL FORMAT REQUIREMENT:",
            FILE_FORMAT_INSTRUCTIONS,
            "",
            "Always generate complete, working code in this response; do not stop to ask questions.",
            "For Web apps, always include an index.html file so it can be previewed live."
        ).joinToString("\n")
    }

    private fun parseCodeFromResponse(raw: String, context: AppContext): CodeArtifact {
        val parsedFiles = AiResponseParser.parseFiles(raw).toMutableList()

        // Fallback for Web: extract HTML block if output is directly an HTML page
        if (parsedFiles.isEmpty() && context.platform == PlatformType.WEB) {
            val packageJson = """{"name":"${context.appName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}","scripts":{"dev":"vite","build":"vite build"}}"""
            val htmlMatch = Regex("""```html\r?\n([\s\S]*?)```""", RegexOption.IGNORE_CASE).find(raw)
            if (htmlMatch != null) {
                parsedFiles.add(SourceFile("index.html", htmlMatch.groupValues[1].trim()))
                parsedFiles.add(SourceFile("package.json", packageJson))
            } else if (raw.contains("<!DOCTYPE html", ignoreCase = true) || raw.contains("<html", ignoreCase = true)) {
                parsedFiles.add(SourceFile("index.html", raw.trim()))
                parsedFiles.add(SourceFile("package.json", packageJson))
            }
        }

        if (parsedFiles.isNotEmpty()) {
            val previewHtml = parsedFiles.firstOrNull { it.path.endsWith("index.html", ignoreCase = true) }?.content
                ?: parsedFiles.firstOrNull { it.path.endsWith(".html", ignoreCase = true) }?.content
            return CodeArtifact(
                files = parsedFiles,
                explanation = "Successfully parsed ${parsedFiles.size} real code files from AI response.",
                previewHtml = previewHtml,
                incompleteFiles = AiResponseParser.parseIncompleteFilePaths(raw)
            )
        }

        return CodeArtifact(
            files = emptyList(),
            explanation = "No valid code files could be parsed from AI response."
        )
    }

    companion object {
        /** Error bodies can be whole HTML pages; keep messages shown in the chat readable. */
        const val MAX_ERROR_BODY_CHARS = 500

        val FILE_FORMAT_INSTRUCTIONS = """
            You MUST return all code files using the following exact format for each file:

            FILE: path/to/file
            ```[language]
            [complete file content]
            ```
            ENDFILE
        """.trimIndent()
    }
}
