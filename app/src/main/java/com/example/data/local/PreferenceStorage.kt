package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.data.model.AuthStyle
import com.example.data.model.ProviderConfig
import org.json.JSONArray
import org.json.JSONObject

class PreferenceStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("nocode_prefs", Context.MODE_PRIVATE)

    var githubToken: String
        get() = prefs.getString(KEY_GH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GH_TOKEN, value).apply()

    var githubUsername: String
        get() = prefs.getString(KEY_GH_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GH_USERNAME, value).apply()

    var githubRepo: String
        get() = prefs.getString(KEY_GH_REPO, "my-nocode-apps") ?: "my-nocode-apps"
        set(value) = prefs.edit().putString(KEY_GH_REPO, value).apply()

    var isGithubVerified: Boolean
        get() = prefs.getBoolean(KEY_GH_VERIFIED, false)
        set(value) = prefs.edit().putBoolean(KEY_GH_VERIFIED, value).apply()

    var githubClientId: String
        get() {
            val stored = prefs.getString(KEY_GH_CLIENT_ID, null)
            return if (stored.isNullOrBlank() || stored == "Ov23liNoCodeApp") DEFAULT_GITHUB_CLIENT_ID else stored
        }
        set(value) = prefs.edit().putString(KEY_GH_CLIENT_ID, value).apply()

    var authMethod: String
        get() = prefs.getString(KEY_AUTH_METHOD, "OAUTH") ?: "OAUTH"
        set(value) = prefs.edit().putString(KEY_AUTH_METHOD, value).apply()

    var selectedCodegenProviderId: String
        get() = prefs.getString(KEY_CODEGEN_PROVIDER, "gemini") ?: "gemini"
        set(value) = prefs.edit().putString(KEY_CODEGEN_PROVIDER, value).apply()

    var selectedFixProviderId: String
        get() = prefs.getString(KEY_FIX_PROVIDER, "gemini") ?: "gemini"
        set(value) = prefs.edit().putString(KEY_FIX_PROVIDER, value).apply()

    var vercelToken: String
        get() = prefs.getString(KEY_VERCEL_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_VERCEL_TOKEN, value).apply()

    var netlifyToken: String
        get() = prefs.getString(KEY_NETLIFY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NETLIFY_TOKEN, value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    // CATEGORY 1: Appearance
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "System") ?: "System"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var accentColor: String
        get() = prefs.getString(KEY_ACCENT_COLOR, "Peach") ?: "Peach"
        set(value) = prefs.edit().putString(KEY_ACCENT_COLOR, value).apply()

    var editorFontSize: Int
        get() = prefs.getInt(KEY_EDITOR_FONT_SIZE, 14)
        set(value) = prefs.edit().putInt(KEY_EDITOR_FONT_SIZE, value).apply()

    var editorFontFamily: String
        get() = prefs.getString(KEY_EDITOR_FONT_FAMILY, "Inter") ?: "Inter"
        set(value) = prefs.edit().putString(KEY_EDITOR_FONT_FAMILY, value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, value).apply()

    // CATEGORY 2: Editor & Workspace
    var wordWrap: Boolean
        get() = prefs.getBoolean(KEY_WORD_WRAP, true)
        set(value) = prefs.edit().putBoolean(KEY_WORD_WRAP, value).apply()

    var lineNumbers: Boolean
        get() = prefs.getBoolean(KEY_LINE_NUMBERS, true)
        set(value) = prefs.edit().putBoolean(KEY_LINE_NUMBERS, value).apply()

    var minimap: Boolean
        get() = prefs.getBoolean(KEY_MINIMAP, false)
        set(value) = prefs.edit().putBoolean(KEY_MINIMAP, value).apply()

    var tabSize: Int
        get() = prefs.getInt(KEY_TAB_SIZE, 2)
        set(value) = prefs.edit().putInt(KEY_TAB_SIZE, value).apply()

    var autoSave: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SAVE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SAVE, value).apply()

    var autoSaveDelay: Int
        get() = prefs.getInt(KEY_AUTO_SAVE_DELAY, 3)
        set(value) = prefs.edit().putInt(KEY_AUTO_SAVE_DELAY, value).apply()

    var keyboardLayout: String
        get() = prefs.getString(KEY_KEYBOARD_LAYOUT, "Native") ?: "Native"
        set(value) = prefs.edit().putString(KEY_KEYBOARD_LAYOUT, value).apply()

    // CATEGORY 3: AI & Models
    var defaultAiProvider: String
        get() = prefs.getString(KEY_DEFAULT_AI_PROVIDER, "Gemini") ?: "Gemini"
        set(value) = prefs.edit().putString(KEY_DEFAULT_AI_PROVIDER, value).apply()

    // CATEGORY 4: Projects & Storage
    var defaultProjectLocation: String
        get() = prefs.getString(KEY_PROJECT_LOCATION, "/Kodrix/") ?: "/Kodrix/"
        set(value) = prefs.edit().putString(KEY_PROJECT_LOCATION, value).apply()

    var autoCommitOnBuild: Boolean
        get() = prefs.getBoolean(KEY_AUTO_COMMIT_ON_BUILD, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_COMMIT_ON_BUILD, value).apply()

    // CATEGORY 5: Build & Deploy
    var buildOnSave: Boolean
        get() = prefs.getBoolean(KEY_BUILD_ON_SAVE, false)
        set(value) = prefs.edit().putBoolean(KEY_BUILD_ON_SAVE, value).apply()

    var defaultBuildTarget: String
        get() = prefs.getString(KEY_DEFAULT_BUILD_TARGET, "Web") ?: "Web"
        set(value) = prefs.edit().putString(KEY_DEFAULT_BUILD_TARGET, value).apply()

    var webBuildRuntime: String
        get() = prefs.getString(KEY_WEB_BUILD_RUNTIME, "Local Termux") ?: "Local Termux"
        set(value) = prefs.edit().putString(KEY_WEB_BUILD_RUNTIME, value).apply()

    var androidBuildRuntime: String
        get() = prefs.getString(KEY_ANDROID_BUILD_RUNTIME, "GitHub Actions") ?: "GitHub Actions"
        set(value) = prefs.edit().putString(KEY_ANDROID_BUILD_RUNTIME, value).apply()

    var autoGenerateWorkflowFile: Boolean
        get() = prefs.getBoolean(KEY_AUTO_GENERATE_WORKFLOW, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_GENERATE_WORKFLOW, value).apply()

    // CATEGORY 6: Preview & Debug Logs
    var previewMode: String
        get() = prefs.getString(KEY_PREVIEW_MODE, "Split View") ?: "Split View"
        set(value) = prefs.edit().putString(KEY_PREVIEW_MODE, value).apply()

    var autoRefreshOnCodeChange: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REFRESH_PREVIEW, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_REFRESH_PREVIEW, value).apply()

    var devicePreview: String
        get() = prefs.getString(KEY_DEVICE_PREVIEW, "Mobile") ?: "Mobile"
        set(value) = prefs.edit().putString(KEY_DEVICE_PREVIEW, value).apply()

    var devServerPort: Int
        get() = prefs.getInt(KEY_DEV_SERVER_PORT, 5173)
        set(value) = prefs.edit().putInt(KEY_DEV_SERVER_PORT, value).apply()

    var aiAutoFixOnError: Boolean
        get() = prefs.getBoolean(KEY_AI_AUTO_FIX_ON_ERROR, true)
        set(value) = prefs.edit().putBoolean(KEY_AI_AUTO_FIX_ON_ERROR, value).apply()

    var clearLogsOnReload: Boolean
        get() = prefs.getBoolean(KEY_CLEAR_LOGS_ON_RELOAD, false)
        set(value) = prefs.edit().putBoolean(KEY_CLEAR_LOGS_ON_RELOAD, value).apply()

    // CATEGORY 7: Terminal & Environment
    var environmentType: String
        get() = prefs.getString(KEY_ENVIRONMENT_TYPE, "Local Termux") ?: "Local Termux"
        set(value) = prefs.edit().putString(KEY_ENVIRONMENT_TYPE, value).apply()

    var nodeVersion: String
        get() = prefs.getString(KEY_NODE_VERSION, "Bundled Node 20") ?: "Bundled Node 20"
        set(value) = prefs.edit().putString(KEY_NODE_VERSION, value).apply()

    var autoStartDevServer: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_DEV_SERVER, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START_DEV_SERVER, value).apply()

    var showTerminal: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TERMINAL, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TERMINAL, value).apply()

    fun getProviders(): List<ProviderConfig> {
        val jsonString = prefs.getString(KEY_PROVIDERS, null)
        if (jsonString.isNullOrEmpty()) {
            return getDefaultProviders()
        }
        return try {
            val list = mutableListOf<ProviderConfig>()
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val modelsArray = obj.optJSONArray("availableModels")
                val modelsList = mutableListOf<String>()
                if (modelsArray != null) {
                    for (j in 0 until modelsArray.length()) {
                        modelsList.add(modelsArray.getString(j))
                    }
                }
                list.add(
                    ProviderConfig(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        apiKey = obj.optString("apiKey"),
                        baseUrl = obj.optString("baseUrl"),
                        authStyle = AuthStyle.valueOf(obj.optString("authStyle", AuthStyle.BEARER.name)),
                        defaultModel = obj.optString("defaultModel"),
                        lastVerifiedAt = obj.optLong("lastVerifiedAt", 0L),
                        isValid = obj.optBoolean("isValid", false),
                        statusMessage = obj.optString("statusMessage", ""),
                        supportsVision = obj.optBoolean("supportsVision", false),
                        isPrimary = obj.optBoolean("isPrimary", false),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        availableModels = getDefaultModelsForProvider(obj.optString("id"))
                    )
                )
            }
            if (list.isEmpty()) getDefaultProviders() else list
        } catch (e: Exception) {
            getDefaultProviders()
        }
    }

    fun saveProviders(providers: List<ProviderConfig>) {
        val array = JSONArray()
        providers.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("apiKey", p.apiKey)
                put("baseUrl", p.baseUrl)
                put("authStyle", p.authStyle.name)
                put("defaultModel", p.defaultModel)
                put("lastVerifiedAt", p.lastVerifiedAt)
                put("isValid", p.isValid)
                put("statusMessage", p.statusMessage)
                put("supportsVision", p.supportsVision)
                put("isPrimary", p.isPrimary)
                put("isEnabled", p.isEnabled)
                val modelsArr = JSONArray()
                p.availableModels.forEach { modelsArr.put(it) }
                put("availableModels", modelsArr)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PROVIDERS, array.toString()).apply()
    }

    fun updateProvider(updated: ProviderConfig) {
        val current = getProviders().toMutableList()
        val index = current.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            current[index] = updated
        } else {
            current.add(updated)
        }
        saveProviders(current)
    }

    fun removeProvider(providerId: String) {
        val current = getProviders().filterNot { it.id == providerId }
        saveProviders(current)
    }

    fun getDefaultModelsForProvider(providerId: String): List<String> {
        return when (providerId.lowercase()) {
            "gemini" -> listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-1.5-pro")
            "openai" -> listOf("gpt-4o", "gpt-4o-mini", "o3-mini", "o1", "gpt-4.5-preview")
            "anthropic", "claude" -> listOf("claude-3-7-sonnet", "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229")
            "groq" -> listOf("llama-3.3-70b-versatile", "deepseek-r1-distill-llama-70b", "llama-3.1-8b-instant", "mixtral-8x7b-32768")
            "deepseek" -> listOf("deepseek-chat", "deepseek-reasoner", "deepseek-v3")
            "openrouter" -> emptyList()
            "local_tunnel", "ollama" -> emptyList()
            else -> emptyList()
        }
    }

    private fun getDefaultProviders(): List<ProviderConfig> {
        val defaultGeminiKey = try {
            BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }

        return listOf(
            ProviderConfig(
                id = "gemini",
                name = "Google Gemini",
                apiKey = defaultGeminiKey,
                baseUrl = "https://generativelanguage.googleapis.com",
                authStyle = AuthStyle.QUERY_PARAM,
                defaultModel = "gemini-2.5-flash",
                isValid = defaultGeminiKey.isNotBlank(),
                statusMessage = if (defaultGeminiKey.isNotBlank()) "Connected (AI Studio Secret)" else "Requires API key",
                supportsVision = true,
                isPrimary = true,
                isEnabled = true,
                availableModels = getDefaultModelsForProvider("gemini")
            ),
            ProviderConfig(
                id = "openai",
                name = "OpenAI",
                apiKey = "",
                baseUrl = "https://api.openai.com/v1",
                authStyle = AuthStyle.BEARER,
                defaultModel = "gpt-4o",
                supportsVision = true,
                isEnabled = true,
                availableModels = getDefaultModelsForProvider("openai")
            ),
            ProviderConfig(
                id = "anthropic",
                name = "Claude (Anthropic)",
                apiKey = "",
                baseUrl = "https://api.anthropic.com/v1",
                authStyle = AuthStyle.X_API_KEY,
                defaultModel = "claude-3-7-sonnet",
                supportsVision = true,
                isEnabled = true,
                availableModels = getDefaultModelsForProvider("anthropic")
            ),
            ProviderConfig(
                id = "groq",
                name = "Groq",
                apiKey = "",
                baseUrl = "https://api.groq.com/openai/v1",
                authStyle = AuthStyle.BEARER,
                defaultModel = "llama-3.3-70b-versatile",
                supportsVision = false,
                isEnabled = true,
                availableModels = getDefaultModelsForProvider("groq")
            ),
            ProviderConfig(
                id = "custom_openai",
                name = "OpenAI Compatible",
                apiKey = "",
                baseUrl = "https://api.together.xyz/v1",
                authStyle = AuthStyle.BEARER,
                defaultModel = "deepseek-ai/DeepSeek-V3",
                supportsVision = false,
                isEnabled = true,
                availableModels = getDefaultModelsForProvider("custom_openai")
            )
        )
    }

    companion object {
        const val DEFAULT_GITHUB_CLIENT_ID = "Ov23liNWCYhzl36uNg6b"
        private const val KEY_GH_CLIENT_ID = "key_gh_client_id"
        private const val KEY_AUTH_METHOD = "key_auth_method"
        private const val KEY_GH_TOKEN = "key_gh_token"
        private const val KEY_GH_USERNAME = "key_gh_username"
        private const val KEY_GH_REPO = "key_gh_repo"
        private const val KEY_GH_VERIFIED = "key_gh_verified"
        private const val KEY_PROVIDERS = "key_providers"
        private const val KEY_CODEGEN_PROVIDER = "key_codegen_provider"
        private const val KEY_FIX_PROVIDER = "key_fix_provider"
        private const val KEY_VERCEL_TOKEN = "key_vercel_token"
        private const val KEY_NETLIFY_TOKEN = "key_netlify_token"
        private const val KEY_ONBOARDING_DONE = "key_onboarding_done"

        // Category 1: Appearance
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_ACCENT_COLOR = "key_accent_color"
        private const val KEY_EDITOR_FONT_SIZE = "key_editor_font_size"
        private const val KEY_EDITOR_FONT_FAMILY = "key_editor_font_family"
        private const val KEY_HAPTICS_ENABLED = "key_haptics_enabled"

        // Category 2: Editor & Workspace
        private const val KEY_WORD_WRAP = "key_word_wrap"
        private const val KEY_LINE_NUMBERS = "key_line_numbers"
        private const val KEY_MINIMAP = "key_minimap"
        private const val KEY_TAB_SIZE = "key_tab_size"
        private const val KEY_AUTO_SAVE = "key_auto_save"
        private const val KEY_AUTO_SAVE_DELAY = "key_auto_save_delay"
        private const val KEY_KEYBOARD_LAYOUT = "key_keyboard_layout"

        // Category 3: AI & Models
        private const val KEY_DEFAULT_AI_PROVIDER = "key_default_ai_provider"

        // Category 4: Projects & Storage
        private const val KEY_PROJECT_LOCATION = "key_project_location"
        private const val KEY_AUTO_COMMIT_ON_BUILD = "key_auto_commit_on_build"

        // Category 5: Build & Deploy
        private const val KEY_BUILD_ON_SAVE = "key_build_on_save"
        private const val KEY_DEFAULT_BUILD_TARGET = "key_default_build_target"
        private const val KEY_WEB_BUILD_RUNTIME = "key_web_build_runtime"
        private const val KEY_ANDROID_BUILD_RUNTIME = "key_android_build_runtime"
        private const val KEY_AUTO_GENERATE_WORKFLOW = "key_auto_generate_workflow"

        // Category 6: Preview & Debug Logs
        private const val KEY_PREVIEW_MODE = "key_preview_mode"
        private const val KEY_AUTO_REFRESH_PREVIEW = "key_auto_refresh_preview"
        private const val KEY_DEVICE_PREVIEW = "key_device_preview"
        private const val KEY_DEV_SERVER_PORT = "key_dev_server_port"
        private const val KEY_AI_AUTO_FIX_ON_ERROR = "key_ai_auto_fix_on_error"
        private const val KEY_CLEAR_LOGS_ON_RELOAD = "key_clear_logs_on_reload"

        // Category 7: Terminal & Environment
        private const val KEY_ENVIRONMENT_TYPE = "key_environment_type"
        private const val KEY_NODE_VERSION = "key_node_version"
        private const val KEY_AUTO_START_DEV_SERVER = "key_auto_start_dev_server"
        private const val KEY_SHOW_TERMINAL = "key_show_terminal"
    }
}
