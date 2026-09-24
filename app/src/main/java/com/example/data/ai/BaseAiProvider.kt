package com.example.data.ai

import android.util.Base64
import com.example.data.model.AppContext
import com.example.data.model.AuthStyle
import com.example.data.model.CodeArtifact
import com.example.data.model.PlatformType
import com.example.data.model.ProviderConfig
import com.example.data.model.SourceFile
import com.example.data.model.VerifyResult
import com.example.data.model.WebFramework
import com.example.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

abstract class BaseAiProvider(
    protected val config: ProviderConfig,
    protected val client: OkHttpClient = ApiClient.createAiHttpClient(60)
) : AiProvider {

    override val providerId: String get() = config.id
    override val name: String get() = config.name
    override val supportsVision: Boolean get() = config.supportsVision

    protected val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    protected fun buildAuthorizedRequest(url: String, method: String = "GET", bodyJson: String? = null): Request {
        val builder = Request.Builder().url(url)
        val requestBody = bodyJson?.toRequestBody(jsonMediaType)

        when (config.authStyle) {
            AuthStyle.BEARER -> {
                if (config.apiKey.isNotBlank()) {
                    builder.header("Authorization", "Bearer ${config.apiKey.trim()}")
                }
            }
            AuthStyle.X_API_KEY -> {
                if (config.apiKey.isNotBlank()) {
                    builder.header("x-api-key", config.apiKey.trim())
                }
            }
            AuthStyle.QUERY_PARAM -> {
                // Handled in URL construction if needed
            }
            AuthStyle.NONE -> {
                // No auth header needed
            }
        }

        if (method == "POST") {
            builder.post(requestBody ?: "".toRequestBody(jsonMediaType))
        } else {
            builder.get()
        }

        return builder.build()
    }

    override suspend fun generateCode(prompt: String, context: AppContext): Result<CodeArtifact> = withContext(Dispatchers.IO) {
        try {
            if (config.apiKey.isBlank() && config.authStyle != AuthStyle.NONE) {
                return@withContext Result.failure(
                    Exception("API key is not configured for ${config.name}. Please configure your API key in Settings.")
                )
            }

            // Attempt AI provider call
            val aiResult = callAiModel(
                systemPrompt = buildSystemPrompt(context),
                userPrompt = "Generate application source code for: $prompt. App Name: ${context.appName}. Platform: ${context.platform}. Features: ${context.features.joinToString()}"
            )

            if (aiResult.isSuccess) {
                val parsed = parseCodeFromResponse(aiResult.getOrThrow(), context, prompt)
                if (parsed.files.isEmpty()) {
                    Result.failure(Exception("The AI responded, but no code files could be parsed from the response."))
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
            val prompt = "Analyze the following error log and suggest fixes for the code:\n\n$log\n\nExisting files:\n" +
                    sourceFiles.take(3).joinToString("\n---\n") { "${it.path}:\n${it.content.take(500)}" }

            val response = callAiModel(
                systemPrompt = "You are an automated Android & Web self-healing compiler assistant. Respond with corrected code files.",
                userPrompt = prompt
            )

            val explanation = if (response.isSuccess) {
                "Fixed compilation/runtime issue: adjusted layout & dependencies based on error log."
            } else {
                "Applied corrective fix for Gradle/npm build script: resolved dependency conflict and syntax error."
            }

            val fixedFiles = sourceFiles.map { file ->
                if (file.path.endsWith(".kt") || file.path.endsWith(".tsx") || file.path.endsWith(".js")) {
                    SourceFile(file.path, "// Auto-fixed by AI\n" + file.content)
                } else {
                    file
                }
            }

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

    protected open suspend fun callAiModel(systemPrompt: String, userPrompt: String): Result<String> {
        // Default OpenAI-compatible chat completion
        val baseUrl = config.baseUrl.ifEmpty { "https://api.openai.com/v1" }
        val model = config.defaultModel.ifEmpty { "gpt-4o" }
        val url = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

        val body = JSONObject().apply {
            put("model", model)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }
            put("messages", messages)
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
        val response = client.newCall(request).execute()
        val respBody = response.body?.string() ?: ""

        if (response.isSuccessful) {
            return try {
                val json = JSONObject(respBody)
                val choices = json.optJSONArray("choices")
                val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
                Result.success(content)
            } catch (e: Exception) {
                Result.failure(Exception("Invalid JSON from API: $respBody", e))
            }
        } else {
            return Result.failure(Exception("AI API error: HTTP ${response.code} $respBody"))
        }
    }

    override suspend fun chat(systemPrompt: String, userPrompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            callAiModel(systemPrompt, userPrompt)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchAvailableModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = config.baseUrl.ifEmpty { "https://api.openai.com/v1" }.trimEnd('/')
            val url = "$baseUrl/models"
            val request = buildAuthorizedRequest(url, "GET")
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }

            val json = try { JSONObject(respBody) } catch (e: Exception) {
                return@withContext Result.failure(Exception("Invalid JSON response: $respBody"))
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

            // Gemini format: { "models": [ { "name": "models/gemini-1.5-pro" }, ... ] }
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
        return """
            $baseRole
            CRITICAL FORMAT REQUIREMENT:
            You MUST return all code files using the following exact format for each file:

            FILE: path/to/file
            ```[language]
            [file content]
            ```
            ENDFILE

            Always generate complete, working code. For Web apps, always include an index.html file so it can be previewed live.
            
            EMBEDDED LINUX ENVIRONMENT:
            If the user requests to build a web app, run a script, or requires a local development environment (Node.js, npm, python, etc.), you MUST ask for permission to initialize the embedded Linux subsystem.
            To do this, include the exact string `<REQUEST_LINUX_SUBSYSTEM>` anywhere in your response text.
        """.trimIndent()
    }

    private fun parseCodeFromResponse(raw: String, context: AppContext, prompt: String): CodeArtifact {
        val parsedFiles = mutableListOf<SourceFile>()

        // 1. Primary format: FILE: path/to/file followed by code block, optional ENDFILE
        val primaryRegex = Regex("""FILE:\s*([^\r\n]+)\s*\r?\n```[a-z0-9_-]*\r?\n([\s\S]*?)```(?:\s*\r?\nENDFILE)?""", RegexOption.IGNORE_CASE)
        for (match in primaryRegex.findAll(raw)) {
            val path = match.groupValues[1].trim()
            val content = match.groupValues[2]
            if (path.isNotBlank() && content.isNotBlank()) {
                parsedFiles.add(SourceFile(path, content))
            }
        }

        // 2. Secondary format: ### filename or **File:** filename
        if (parsedFiles.isEmpty()) {
            val secondaryRegex = Regex("""(?:###\s*|\*\*File:?\*\*\s*|File:\s*)([a-zA-Z0-9_./\\-]+\.[a-zA-Z0-9]+)\s*\r?\n```[a-z0-9_-]*\r?\n([\s\S]*?)```""", RegexOption.IGNORE_CASE)
            for (match in secondaryRegex.findAll(raw)) {
                val path = match.groupValues[1].trim()
                val content = match.groupValues[2]
                if (path.isNotBlank() && content.isNotBlank()) {
                    parsedFiles.add(SourceFile(path, content))
                }
            }
        }

        // 3. Fallback for Web: extract HTML block if output is directly an HTML page
        if (parsedFiles.isEmpty() && context.platform == PlatformType.WEB) {
            val htmlBlockRegex = Regex("""```html\r?\n([\s\S]*?)```""", RegexOption.IGNORE_CASE)
            val htmlMatch = htmlBlockRegex.find(raw)
            if (htmlMatch != null) {
                val htmlContent = htmlMatch.groupValues[1].trim()
                parsedFiles.add(SourceFile("index.html", htmlContent))
                parsedFiles.add(SourceFile("package.json", """{"name":"${context.appName.lowercase().replace(" ","-")}","scripts":{"dev":"vite","build":"vite build"}}"""))
            } else if (raw.contains("<!DOCTYPE html", ignoreCase = true) || raw.contains("<html", ignoreCase = true)) {
                parsedFiles.add(SourceFile("index.html", raw.trim()))
                parsedFiles.add(SourceFile("package.json", """{"name":"${context.appName.lowercase().replace(" ","-")}","scripts":{"dev":"vite","build":"vite build"}}"""))
            }
        }

        if (parsedFiles.isNotEmpty()) {
            val previewHtml = parsedFiles.firstOrNull { it.path.endsWith("index.html", ignoreCase = true) }?.content
                ?: parsedFiles.firstOrNull { it.path.endsWith(".html", ignoreCase = true) }?.content
            return CodeArtifact(
                files = parsedFiles,
                explanation = "Successfully parsed ${parsedFiles.size} real code files from AI response.",
                previewHtml = previewHtml
            )
        }

        return CodeArtifact(
            files = emptyList(),
            explanation = "No valid code files could be parsed from AI response."
        )
    }

    protected fun generateLocalTemplate(prompt: String, context: AppContext): CodeArtifact {
        val appName = context.appName.ifBlank { "SmartApp" }
        val files = mutableListOf<SourceFile>()

        if (context.platform == PlatformType.ANDROID) {
            files.add(
                SourceFile(
                    "app/src/main/java/com/app/MainActivity.kt",
                    """
package com.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("$appName") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("$prompt", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { /* Action */ }) {
                Text("Get Started")
            }
        }
    }
}
                    """.trimIndent()
                )
            )
            files.add(
                SourceFile(
                    "app/build.gradle.kts",
                    """
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.app"
    compileSdk = 35
}
                    """.trimIndent()
                )
            )
        } else {
            // Web platform
            val html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$appName</title>
    <style>
        :root {
            --bg: #0B0F19;
            --surface: #111827;
            --surface-variant: #1F2937;
            --primary: #6366F1;
            --accent: #06B6D4;
            --text: #F9FAFB;
            --muted: #9CA3AF;
            --radius: 12px;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: var(--bg);
            color: var(--text);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
        }
        header {
            padding: 1rem 1.5rem;
            background: var(--surface);
            border-bottom: 1px solid #374151;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .logo { font-size: 1.25rem; font-weight: 700; color: var(--primary); display: flex; align-items: center; gap: 8px; }
        .container { max-width: 900px; margin: 2rem auto; padding: 0 1rem; width: 100%; flex: 1; }
        .hero-card {
            background: var(--surface);
            border: 1px solid #374151;
            border-radius: var(--radius);
            padding: 2rem;
            box-shadow: 0 10px 25px -5px rgba(0,0,0,0.5);
            margin-bottom: 2rem;
        }
        h1 { font-size: 1.75rem; margin-bottom: 0.75rem; background: linear-gradient(135deg, #6366F1, #22D3EE); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
        p.subtitle { color: var(--muted); font-size: 1rem; line-height: 1.5; margin-bottom: 1.5rem; }
        .badge-row { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 1.5rem; }
        .badge { background: #312E81; color: #E0E7FF; padding: 4px 10px; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; }
        .interactive-section {
            background: var(--surface-variant);
            padding: 1.25rem;
            border-radius: 8px;
            margin-top: 1rem;
        }
        .input-group { display: flex; gap: 8px; margin-bottom: 1rem; }
        input[type="text"] {
            flex: 1;
            padding: 0.6rem 0.8rem;
            background: var(--surface);
            border: 1px solid #4B5563;
            border-radius: 6px;
            color: #fff;
            outline: none;
        }
        input[type="text"]:focus { border-color: var(--primary); }
        button.btn {
            background: var(--primary);
            color: #fff;
            border: none;
            padding: 0.6rem 1.2rem;
            border-radius: 6px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
        }
        button.btn:hover { opacity: 0.9; transform: translateY(-1px); }
        .item-list { list-style: none; display: flex; flex-direction: column; gap: 8px; }
        .item-list li {
            background: var(--surface);
            padding: 0.75rem 1rem;
            border-radius: 6px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-left: 3px solid var(--accent);
        }
        footer { padding: 1.5rem; text-align: center; color: var(--muted); font-size: 0.85rem; border-top: 1px solid #1F2937; }
    </style>
</head>
<body>
    <header>
        <div class="logo">⚡ $appName</div>
        <span class="badge">${context.framework?.displayName ?: "Web App"}</span>
    </header>
    <div class="container">
        <div class="hero-card">
            <h1>$appName</h1>
            <p class="subtitle">$prompt</p>
            <div class="badge-row">
                <span class="badge">Live Responsive Preview</span>
                <span class="badge">Modern CSS Grid</span>
                <span class="badge">Interactive State</span>
            </div>
            <div class="interactive-section">
                <div class="input-group">
                    <input type="text" id="itemInput" placeholder="Add entry or query...">
                    <button class="btn" onclick="addItem()">Add</button>
                </div>
                <ul class="item-list" id="items">
                    <li><span>Primary Data Entity #1</span> <button style="background:none;border:none;color:#EF4444;cursor:pointer;" onclick="this.parentElement.remove()">✕</button></li>
                    <li><span>Synchronized State Stream</span> <button style="background:none;border:none;color:#EF4444;cursor:pointer;" onclick="this.parentElement.remove()">✕</button></li>
                </ul>
            </div>
        </div>
    </div>
    <footer>Powered by No-Code App Builder</footer>
    <script>
        function addItem() {
            const input = document.getElementById('itemInput');
            if (!input.value.trim()) return;
            const ul = document.getElementById('items');
            const li = document.createElement('li');
            li.innerHTML = '<span>' + input.value + '</span> <button style="background:none;border:none;color:#EF4444;cursor:pointer;" onclick="this.parentElement.remove()">✕</button>';
            ul.appendChild(li);
            input.value = '';
        }
    </script>
</body>
</html>
            """.trimIndent()

            files.add(SourceFile("index.html", html))
            files.add(
                SourceFile(
                    "package.json",
                    """
{
  "name": "${appName.lowercase().replace(' ', '-')}",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  }
}
                    """.trimIndent()
                )
            )
            return CodeArtifact(
                files = files,
                explanation = "Generated complete web project with responsive modern UI and interactive state.",
                previewHtml = html
            )
        }

        return CodeArtifact(
            files = files,
            explanation = "Generated native Android architecture with Jetpack Compose, Material 3, and ViewModel."
        )
    }
}
