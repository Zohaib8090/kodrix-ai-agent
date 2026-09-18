package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BuildRecord
import com.example.data.local.PreferenceStorage
import com.example.data.model.AuthStyle
import com.example.data.model.ProviderConfig
import com.example.data.remote.ApiClient
import com.example.data.remote.GitHubUser
import com.example.data.services.AiProviderRepository
import com.example.data.services.GitHubOAuthService
import com.example.data.services.ProjectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SettingsUiState(
    // Category 1: Appearance
    val themeMode: String = "System",
    val accentColor: String = "Peach",
    val editorFontSize: Int = 14,
    val editorFontFamily: String = "Inter",
    val hapticsEnabled: Boolean = true,

    // Category 2: Editor & Workspace
    val wordWrap: Boolean = true,
    val lineNumbers: Boolean = true,
    val minimap: Boolean = false,
    val tabSize: Int = 2,
    val autoSave: Boolean = true,
    val autoSaveDelay: Int = 3,
    val keyboardLayout: String = "Native",

    // Category 3: AI & Models
    val defaultProvider: String = "Gemini",
    val providers: List<ProviderConfig> = emptyList(),
    val verifyingProviderId: String? = null,

    // Category 4: Projects & Storage
    val defaultProjectLocation: String = "/Kodrix/",
    val githubUsername: String = "",
    val isGithubConnected: Boolean = false,
    val verifiedUser: GitHubUser? = null,
    val isStartingOAuth: Boolean = false,
    val deviceFlow: DeviceFlowState? = null,
    val githubError: String? = null,
    val autoCommitOnBuild: Boolean = true,
    val cacheSizeFormatted: String = "14.2 MB",
    val exportStatusMessage: String? = null,

    // Category 5: Build & Deploy
    val buildOnSave: Boolean = false,
    val defaultBuildTarget: String = "Web",
    val webBuildRuntime: String = "Local Termux",
    val androidBuildRuntime: String = "GitHub Actions",
    val autoGenerateWorkflowFile: Boolean = true,
    val buildRecords: List<BuildRecord> = emptyList(),

    // Category 6: Preview & Debug Logs
    val previewMode: String = "Split View",
    val autoRefreshOnCodeChange: Boolean = true,
    val devicePreview: String = "Mobile",
    val devServerPort: Int = 5173,
    val aiAutoFixOnError: Boolean = true,
    val clearLogsOnReload: Boolean = false,

    // Category 7: Terminal & Environment
    val environmentType: String = "Local Termux",
    val nodeVersion: String = "Bundled Node 20",
    val autoStartDevServer: Boolean = true,
    val showTerminal: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceStorage(application)
    private val aiRepo = AiProviderRepository(prefs)
    private val oAuthService = GitHubOAuthService()
    private val database = AppDatabase.getInstance(application)
    private var pollingJob: Job? = null

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        loadSettings()
        observeBuildRecords()
        calculateCacheSize()
    }

    private fun loadSettings() {
        val providers = prefs.getProviders()
        val isVerified = prefs.isGithubVerified
        val ghToken = prefs.githubToken
        val ghUser = prefs.githubUsername

        _state.value = _state.value.copy(
            // Category 1
            themeMode = prefs.themeMode,
            accentColor = prefs.accentColor,
            editorFontSize = prefs.editorFontSize,
            editorFontFamily = prefs.editorFontFamily,
            hapticsEnabled = prefs.hapticsEnabled,

            // Category 2
            wordWrap = prefs.wordWrap,
            lineNumbers = prefs.lineNumbers,
            minimap = prefs.minimap,
            tabSize = prefs.tabSize,
            autoSave = prefs.autoSave,
            autoSaveDelay = prefs.autoSaveDelay,
            keyboardLayout = prefs.keyboardLayout,

            // Category 3
            defaultProvider = prefs.defaultAiProvider,
            providers = providers,

            // Category 4
            defaultProjectLocation = prefs.defaultProjectLocation,
            githubUsername = ghUser,
            isGithubConnected = isVerified && ghToken.isNotBlank(),
            autoCommitOnBuild = prefs.autoCommitOnBuild,

            // Category 5
            buildOnSave = prefs.buildOnSave,
            defaultBuildTarget = prefs.defaultBuildTarget,
            webBuildRuntime = prefs.webBuildRuntime,
            androidBuildRuntime = prefs.androidBuildRuntime,
            autoGenerateWorkflowFile = prefs.autoGenerateWorkflowFile,

            // Category 6
            previewMode = prefs.previewMode,
            autoRefreshOnCodeChange = prefs.autoRefreshOnCodeChange,
            devicePreview = prefs.devicePreview,
            devServerPort = prefs.devServerPort,
            aiAutoFixOnError = prefs.aiAutoFixOnError,
            clearLogsOnReload = prefs.clearLogsOnReload,

            // Category 7
            environmentType = prefs.environmentType,
            nodeVersion = prefs.nodeVersion,
            autoStartDevServer = prefs.autoStartDevServer,
            showTerminal = prefs.showTerminal
        )

        if (isVerified && ghToken.isNotBlank()) {
            verifyGitHubTokenSilently(ghToken)
        }
    }

    private fun observeBuildRecords() {
        viewModelScope.launch {
            database.buildRecordDao().getAllRecords().collect { records ->
                _state.value = _state.value.copy(buildRecords = records)
            }
        }
    }

    // CATEGORY 1: Appearance
    fun setThemeMode(mode: String) {
        prefs.themeMode = mode
        _state.value = _state.value.copy(themeMode = mode)
        triggerHaptic()
    }

    fun setAccentColor(color: String) {
        prefs.accentColor = color
        _state.value = _state.value.copy(accentColor = color)
        triggerHaptic()
    }

    fun setEditorFontSize(size: Int) {
        val clamped = size.coerceIn(12, 24)
        prefs.editorFontSize = clamped
        _state.value = _state.value.copy(editorFontSize = clamped)
    }

    fun setEditorFontFamily(family: String) {
        prefs.editorFontFamily = family
        _state.value = _state.value.copy(editorFontFamily = family)
        triggerHaptic()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.hapticsEnabled = enabled
        _state.value = _state.value.copy(hapticsEnabled = enabled)
        if (enabled) triggerHaptic(force = true)
    }

    // CATEGORY 2: Editor & Workspace
    fun setWordWrap(wrap: Boolean) {
        prefs.wordWrap = wrap
        _state.value = _state.value.copy(wordWrap = wrap)
        triggerHaptic()
    }

    fun setLineNumbers(show: Boolean) {
        prefs.lineNumbers = show
        _state.value = _state.value.copy(lineNumbers = show)
        triggerHaptic()
    }

    fun setMinimap(show: Boolean) {
        prefs.minimap = show
        _state.value = _state.value.copy(minimap = show)
        triggerHaptic()
    }

    fun setTabSize(size: Int) {
        prefs.tabSize = size
        _state.value = _state.value.copy(tabSize = size)
        triggerHaptic()
    }

    fun setAutoSave(enabled: Boolean) {
        prefs.autoSave = enabled
        _state.value = _state.value.copy(autoSave = enabled)
        triggerHaptic()
    }

    fun setAutoSaveDelay(delay: Int) {
        prefs.autoSaveDelay = delay
        _state.value = _state.value.copy(autoSaveDelay = delay)
        triggerHaptic()
    }

    fun setKeyboardLayout(layout: String) {
        prefs.keyboardLayout = layout
        _state.value = _state.value.copy(keyboardLayout = layout)
        triggerHaptic()
    }

    // CATEGORY 3: AI & Models
    fun setDefaultProvider(provider: String) {
        prefs.defaultAiProvider = provider
        _state.value = _state.value.copy(defaultProvider = provider)
        triggerHaptic()
    }

    fun toggleProviderEnabled(providerId: String, isEnabled: Boolean) {
        val updated = _state.value.providers.map {
            if (it.id == providerId) it.copy(isEnabled = isEnabled) else it
        }
        prefs.saveProviders(updated)
        _state.value = _state.value.copy(providers = updated)
        triggerHaptic()
    }

    fun updateProviderApiKey(providerId: String, apiKey: String) {
        val updated = _state.value.providers.map {
            if (it.id == providerId) it.copy(apiKey = apiKey.trim()) else it
        }
        prefs.saveProviders(updated)
        _state.value = _state.value.copy(providers = updated)
    }

    fun updateProviderModel(providerId: String, model: String) {
        val trimmed = model.trim()
        val updated = _state.value.providers.map { p ->
            if (p.id == providerId) {
                val updatedModels = if (p.availableModels.contains(trimmed)) {
                    p.availableModels
                } else {
                    listOf(trimmed) + p.availableModels
                }
                p.copy(defaultModel = trimmed, availableModels = updatedModels)
            } else {
                p
            }
        }
        prefs.saveProviders(updated)
        _state.value = _state.value.copy(providers = updated)
        triggerHaptic()
    }

    fun updateProviderBaseUrl(providerId: String, baseUrl: String) {
        val updated = _state.value.providers.map {
            if (it.id == providerId) it.copy(baseUrl = baseUrl.trim()) else it
        }
        prefs.saveProviders(updated)
        _state.value = _state.value.copy(providers = updated)
    }

    fun updateProviderThinking(providerId: String, enabled: Boolean, level: String? = null, budgetTokens: Int? = null) {
        val updated = _state.value.providers.map { p ->
            if (p.id == providerId) {
                p.copy(
                    thinkingEnabled = enabled,
                    thinkingLevel = level ?: p.thinkingLevel,
                    thinkingBudgetTokens = budgetTokens ?: p.thinkingBudgetTokens
                )
            } else p
        }
        prefs.saveProviders(updated)
        _state.value = _state.value.copy(providers = updated)
        triggerHaptic()
    }

    fun addCustomThinkingLevel(providerId: String, level: String) {
        val trimmed = level.trim().lowercase()
        if (trimmed.isBlank()) return
        val updated = _state.value.providers.map { p ->
            if (p.id == providerId) {
                val newLevels = if (p.supportedThinkingLevels.contains(trimmed)) {
                    p.supportedThinkingLevels
                } else {
                    p.supportedThinkingLevels + trimmed
                }
                p.copy(
                    thinkingLevel = trimmed,
                    supportedThinkingLevels = newLevels,
                    thinkingEnabled = true
                )
            } else p
        }
        prefs.saveProviders(updated)
        _state.value = _state.value.copy(providers = updated)
        triggerHaptic()
    }

    fun updateProviderCustomPayload(providerId: String, customPayloadJson: String) {
        val updated = _state.value.providers.map { p ->
            if (p.id == providerId) p.copy(customPayloadJson = customPayloadJson) else p
        }
        prefs.saveProviders(updated)
        _state.value = _state.value.copy(providers = updated)
    }

    fun updateProviderFullConfig(updatedConfig: ProviderConfig) {
        val updated = _state.value.providers.map { p ->
            if (p.id == updatedConfig.id) updatedConfig else p
        }
        prefs.saveProviders(updated)
        _state.value = _state.value.copy(providers = updated)
        triggerHaptic()
    }

    fun updateProviderFromJson(providerId: String, json: String): Result<Unit> {
        return try {
            val obj = org.json.JSONObject(json.trim())
            val current = _state.value.providers.find { it.id == providerId }
                ?: return Result.failure(Exception("Provider not found"))

            val modelsArray = obj.optJSONArray("availableModels")
            val modelsList = mutableListOf<String>()
            if (modelsArray != null) {
                for (j in 0 until modelsArray.length()) {
                    modelsList.add(modelsArray.getString(j))
                }
            }

            val thinkingLevelsArray = obj.optJSONArray("supportedThinkingLevels")
            val thinkingLevelsList = mutableListOf<String>()
            if (thinkingLevelsArray != null) {
                for (k in 0 until thinkingLevelsArray.length()) {
                    thinkingLevelsList.add(thinkingLevelsArray.getString(k))
                }
            }

            val authStyleStr = obj.optString("authStyle", current.authStyle.name)
            val authStyle = try { AuthStyle.valueOf(authStyleStr) } catch (_: Exception) { current.authStyle }

            val updatedConfig = current.copy(
                name = obj.optString("name", current.name),
                apiKey = obj.optString("apiKey", current.apiKey),
                baseUrl = obj.optString("baseUrl", current.baseUrl),
                authStyle = authStyle,
                defaultModel = obj.optString("defaultModel", current.defaultModel),
                supportsVision = obj.optBoolean("supportsVision", current.supportsVision),
                isEnabled = obj.optBoolean("isEnabled", current.isEnabled),
                availableModels = if (modelsList.isNotEmpty()) modelsList else current.availableModels,
                thinkingEnabled = obj.optBoolean("thinkingEnabled", current.thinkingEnabled),
                thinkingLevel = obj.optString("thinkingLevel", current.thinkingLevel),
                thinkingBudgetTokens = obj.optInt("thinkingBudgetTokens", current.thinkingBudgetTokens),
                supportedThinkingLevels = if (thinkingLevelsList.isNotEmpty()) thinkingLevelsList else current.supportedThinkingLevels,
                customPayloadJson = obj.optString("customPayloadJson", current.customPayloadJson)
            )

            updateProviderFullConfig(updatedConfig)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportProviderToJson(provider: ProviderConfig): String {
        val obj = org.json.JSONObject().apply {
            put("id", provider.id)
            put("name", provider.name)
            put("apiKey", provider.apiKey)
            put("baseUrl", provider.baseUrl)
            put("authStyle", provider.authStyle.name)
            put("defaultModel", provider.defaultModel)
            put("supportsVision", provider.supportsVision)
            put("isEnabled", provider.isEnabled)
            val modelsArr = org.json.JSONArray()
            provider.availableModels.forEach { modelsArr.put(it) }
            put("availableModels", modelsArr)
            put("thinkingEnabled", provider.thinkingEnabled)
            put("thinkingLevel", provider.thinkingLevel)
            put("thinkingBudgetTokens", provider.thinkingBudgetTokens)
            val levelsArr = org.json.JSONArray()
            provider.supportedThinkingLevels.forEach { levelsArr.put(it) }
            put("supportedThinkingLevels", levelsArr)
            put("customPayloadJson", provider.customPayloadJson)
        }
        return obj.toString(2)
    }

    fun deleteCustomProvider(providerId: String) {
        prefs.removeProvider(providerId)
        val updated = _state.value.providers.filterNot { it.id == providerId }
        _state.value = _state.value.copy(providers = updated)
        triggerHaptic()
    }

    fun addCustomProvider(
        name: String,
        baseUrl: String,
        apiKey: String,
        modelName: String,
        type: String = "OpenAI Compatible",
        thinkingEnabled: Boolean = false,
        thinkingLevel: String = "medium"
    ) {
        val prefix = when(type) {
            "Anthropic Compatible" -> "custom_anthropic_"
            "Gemini Compatible" -> "custom_gemini_"
            else -> "custom_openai_"
        }
        val id = prefix + System.currentTimeMillis()
        val newProvider = ProviderConfig(
            id = id,
            name = name.ifBlank { "Custom Provider" },
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            authStyle = when(type) {
                "Anthropic Compatible" -> AuthStyle.X_API_KEY
                "Gemini Compatible" -> AuthStyle.QUERY_PARAM
                else -> AuthStyle.BEARER
            },
            defaultModel = modelName.trim().ifBlank { "custom-model" },
            isEnabled = true,
            isValid = false,
            statusMessage = "Custom configured",
            availableModels = listOf(modelName.trim().ifBlank { "custom-model" }),
            thinkingEnabled = thinkingEnabled,
            thinkingLevel = thinkingLevel,
            supportedThinkingLevels = listOf("low", "medium", "high", "ultra", "adaptive")
        )
        val current = _state.value.providers.toMutableList().apply { add(newProvider) }
        prefs.saveProviders(current)
        _state.value = _state.value.copy(providers = current)
        triggerHaptic()
    }

    /**
     * Imports one or more AI providers from a JSON string.
     * Accepts either:
     * - A single provider object: { "id": ..., "name": ..., "apiKey": ..., ... }
     * - An array of provider objects: [{ ... }, { ... }]
     */
    fun importProvidersFromJson(json: String): Result<Int> {
        return try {
            val trimmed = json.trim()
            val providerList = mutableListOf<ProviderConfig>()

            fun parseObject(obj: org.json.JSONObject): ProviderConfig {
                val id = obj.optString("id").ifBlank { "custom_openai_${System.currentTimeMillis()}" }
                val name = obj.optString("name", "Imported Provider")
                val baseUrl = obj.optString("baseUrl", obj.optString("base_url", ""))
                val apiKey = obj.optString("apiKey", obj.optString("api_key", ""))
                val model = obj.optString("defaultModel", obj.optString("model", "custom-model"))
                val authStyleStr = obj.optString("authStyle", obj.optString("auth_style", "BEARER"))
                val authStyle = try { AuthStyle.valueOf(authStyleStr.uppercase()) } catch (_: Exception) { AuthStyle.BEARER }
                val supportsVision = obj.optBoolean("supportsVision", false)
                val isEnabled = obj.optBoolean("isEnabled", true)
                val thinkingEnabled = obj.optBoolean("thinkingEnabled", false)
                val thinkingLevel = obj.optString("thinkingLevel", "medium")
                val customPayloadJson = obj.optString("customPayloadJson", "")
                return ProviderConfig(
                    id = id,
                    name = name,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    authStyle = authStyle,
                    defaultModel = model,
                    isEnabled = isEnabled,
                    isValid = false,
                    supportsVision = supportsVision,
                    statusMessage = "Imported from JSON",
                    availableModels = listOf(model),
                    thinkingEnabled = thinkingEnabled,
                    thinkingLevel = thinkingLevel,
                    customPayloadJson = customPayloadJson
                )
            }

            if (trimmed.startsWith("[")) {
                val arr = org.json.JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    providerList.add(parseObject(arr.getJSONObject(i)))
                }
            } else {
                providerList.add(parseObject(org.json.JSONObject(trimmed)))
            }

            val current = _state.value.providers.toMutableList()
            // Avoid duplicates: replace if same id, otherwise append
            providerList.forEach { imported ->
                val idx = current.indexOfFirst { it.id == imported.id }
                if (idx >= 0) current[idx] = imported else current.add(imported)
            }
            prefs.saveProviders(current)
            _state.value = _state.value.copy(providers = current)
            triggerHaptic()
            Result.success(providerList.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun verifyProvider(providerId: String) {
        val config = _state.value.providers.find { it.id == providerId } ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(verifyingProviderId = providerId)
            val result = aiRepo.verifyProvider(config)
            val updatedList = prefs.getProviders()
            _state.value = _state.value.copy(
                providers = updatedList,
                verifyingProviderId = null
            )
            triggerHaptic()
        }
    }

    // CATEGORY 4: Projects & Storage
    fun setDefaultProjectLocation(location: String) {
        prefs.defaultProjectLocation = location
        _state.value = _state.value.copy(defaultProjectLocation = location)
    }

    fun setAutoCommitOnBuild(autoCommit: Boolean) {
        prefs.autoCommitOnBuild = autoCommit
        _state.value = _state.value.copy(autoCommitOnBuild = autoCommit)
        triggerHaptic()
    }

    fun startDeviceOAuth() {
        val clientId = prefs.githubClientId.ifBlank { PreferenceStorage.DEFAULT_GITHUB_CLIENT_ID }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isStartingOAuth = true,
                githubError = null,
                deviceFlow = null
            )

            val result = oAuthService.requestDeviceCode(clientId)
            if (result.isSuccess) {
                val flow = result.getOrThrow()
                _state.value = _state.value.copy(
                    isStartingOAuth = false,
                    deviceFlow = DeviceFlowState(
                        deviceCode = flow.deviceCode,
                        userCode = flow.userCode,
                        verificationUri = flow.verificationUri,
                        verificationUriComplete = flow.verificationUriComplete,
                        expiresIn = flow.expiresIn,
                        interval = flow.interval,
                        isPolling = true,
                        statusText = "Waiting for GitHub authorization..."
                    )
                )

                startPollingOAuth(clientId, flow.deviceCode, flow.interval, flow.expiresIn)
            } else {
                _state.value = _state.value.copy(
                    isStartingOAuth = false,
                    githubError = result.exceptionOrNull()?.localizedMessage ?: "Failed to initialize GitHub OAuth"
                )
            }
        }
    }

    private fun startPollingOAuth(clientId: String, deviceCode: String, interval: Int, expiresIn: Int) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val tokenResult = oAuthService.pollForAccessToken(
                clientId = clientId,
                deviceCode = deviceCode,
                initialIntervalSeconds = interval,
                expiresInSeconds = expiresIn
            )

            if (tokenResult.isSuccess) {
                val token = tokenResult.getOrThrow()
                prefs.githubToken = token
                prefs.isGithubVerified = true
                _state.value = _state.value.copy(
                    isGithubConnected = true,
                    deviceFlow = null,
                    githubError = null
                )
                verifyGitHubTokenSilently(token)
                triggerHaptic()
            } else {
                _state.value = _state.value.copy(
                    deviceFlow = _state.value.deviceFlow?.copy(
                        isPolling = false,
                        error = tokenResult.exceptionOrNull()?.localizedMessage ?: "Authorization expired"
                    )
                )
            }
        }
    }

    fun cancelDeviceOAuth() {
        pollingJob?.cancel()
        pollingJob = null
        _state.value = _state.value.copy(isStartingOAuth = false, deviceFlow = null)
    }

    fun disconnectGitHub() {
        pollingJob?.cancel()
        pollingJob = null
        prefs.githubToken = ""
        prefs.githubUsername = ""
        prefs.isGithubVerified = false
        _state.value = _state.value.copy(
            isGithubConnected = false,
            githubUsername = "",
            verifiedUser = null,
            deviceFlow = null
        )
        triggerHaptic()
    }

    private fun verifyGitHubTokenSilently(token: String) {
        viewModelScope.launch {
            try {
                val api = ApiClient.createGitHubService { token }
                val response = api.getUser()
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    prefs.githubUsername = user.login
                    prefs.isGithubVerified = true
                    _state.value = _state.value.copy(
                        githubUsername = user.login,
                        verifiedUser = user,
                        isGithubConnected = true
                    )
                }
            } catch (e: Exception) {
                // Keep connected status
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                val cacheDir = getApplication<Application>().cacheDir
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
            } catch (e: Exception) {
                // Ignore
            }
            _state.value = _state.value.copy(cacheSizeFormatted = "0 KB")
            triggerHaptic()
        }
    }

    private fun calculateCacheSize() {
        try {
            val cacheDir = getApplication<Application>().cacheDir
            val sizeBytes = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            val formatted = when {
                sizeBytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
                sizeBytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", sizeBytes / 1024.0)
                else -> "$sizeBytes B"
            }
            _state.value = _state.value.copy(cacheSizeFormatted = formatted)
        } catch (e: Exception) {
            _state.value = _state.value.copy(cacheSizeFormatted = "0 KB")
        }
    }

    private fun getDirSize(dir: File): Long {
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    fun exportProjectAsZip(): File? {
        triggerHaptic()
        return try {
            val app = getApplication<Application>()
            val exportDir = File(app.filesDir, "exports").apply { mkdirs() }
            val zipFile = File(exportDir, "kodrix_project_${System.currentTimeMillis()}.zip")
            val projectRepo = ProjectRepository(app)
            val projects = projectRepo.rootProjectsDir.listFiles()?.filter { it.isDirectory }
            val activeProjectDir = projects?.firstOrNull() ?: projectRepo.getProjectDir("kodrix_default_app")

            // Real ZIP packing with ZipOutputStream walking through project dir with relative paths
            projectRepo.exportProjectAsZip(activeProjectDir, zipFile)

            _state.value = _state.value.copy(exportStatusMessage = "Project exported to ${zipFile.name}")
            zipFile
        } catch (e: Exception) {
            _state.value = _state.value.copy(exportStatusMessage = "Export failed: ${e.message}")
            null
        }
    }

    // CATEGORY 5: Build & Deploy
    fun setBuildOnSave(enabled: Boolean) {
        prefs.buildOnSave = enabled
        _state.value = _state.value.copy(buildOnSave = enabled)
        triggerHaptic()
    }

    fun setDefaultBuildTarget(target: String) {
        prefs.defaultBuildTarget = target
        _state.value = _state.value.copy(defaultBuildTarget = target)
        triggerHaptic()
    }

    fun setAutoGenerateWorkflowFile(enabled: Boolean) {
        prefs.autoGenerateWorkflowFile = enabled
        _state.value = _state.value.copy(autoGenerateWorkflowFile = enabled)
        triggerHaptic()
    }

    // CATEGORY 6: Preview & Debug Logs
    fun setPreviewMode(mode: String) {
        prefs.previewMode = mode
        _state.value = _state.value.copy(previewMode = mode)
        triggerHaptic()
    }

    fun setAutoRefreshOnCodeChange(enabled: Boolean) {
        prefs.autoRefreshOnCodeChange = enabled
        _state.value = _state.value.copy(autoRefreshOnCodeChange = enabled)
        triggerHaptic()
    }

    fun setDevicePreview(preview: String) {
        prefs.devicePreview = preview
        _state.value = _state.value.copy(devicePreview = preview)
        triggerHaptic()
    }

    fun setDevServerPort(port: Int) {
        prefs.devServerPort = port
        _state.value = _state.value.copy(devServerPort = port)
    }

    fun setAiAutoFixOnError(enabled: Boolean) {
        prefs.aiAutoFixOnError = enabled
        _state.value = _state.value.copy(aiAutoFixOnError = enabled)
        triggerHaptic()
    }

    fun setClearLogsOnReload(enabled: Boolean) {
        prefs.clearLogsOnReload = enabled
        _state.value = _state.value.copy(clearLogsOnReload = enabled)
        triggerHaptic()
    }

    // CATEGORY 7: Terminal & Environment
    fun setEnvironmentType(type: String) {
        prefs.environmentType = type
        _state.value = _state.value.copy(environmentType = type)
        triggerHaptic()
    }

    fun setNodeVersion(version: String) {
        prefs.nodeVersion = version
        _state.value = _state.value.copy(nodeVersion = version)
        triggerHaptic()
    }

    fun setAutoStartDevServer(enabled: Boolean) {
        prefs.autoStartDevServer = enabled
        _state.value = _state.value.copy(autoStartDevServer = enabled)
        triggerHaptic()
    }

    fun setShowTerminal(show: Boolean) {
        prefs.showTerminal = show
        _state.value = _state.value.copy(showTerminal = show)
        triggerHaptic()
    }

    private fun triggerHaptic(force: Boolean = false) {
        if (!force && !_state.value.hapticsEnabled) return
        try {
            val app = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(20)
            }
        } catch (e: Exception) {
            // Ignore if vibration unavailable
        }
    }
}
