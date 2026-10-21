package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ChatTurn
import com.example.data.local.AppDatabase
import com.example.data.local.BuildRecord
import com.example.data.local.PreferenceStorage
import com.example.data.model.Blueprint
import com.example.data.model.BlueprintsData
import com.example.data.model.PlatformType
import com.example.data.model.ProviderConfig
import com.example.data.model.WebFramework
import com.example.data.services.AiProviderRepository
import com.example.data.services.ProjectRepository
import com.example.util.ProjectNaming
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class ChatSender { USER, AI }

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: ChatSender,
    val text: String,
    val suggestedPrompt: String? = null,
    val suggestedCategory: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class DashboardUiState(
    val platform: PlatformType = PlatformType.WEB,
    val webFramework: WebFramework = WebFramework.REACT_VITE,
    val blueprints: List<Blueprint> = BlueprintsData.webBlueprints,
    val selectedBlueprint: Blueprint = BlueprintsData.webBlueprints.first(),
    /** Blank means "derive a name from the prompt when the project is created". */
    val appName: String = "",
    val prompt: String = "",
    val enabledFeatures: Set<String> = BlueprintsData.webBlueprints.first().defaultFeatures.toSet(),
    val availableProviders: List<ProviderConfig> = emptyList(),
    val selectedCodegenProviderId: String = "gemini",
    val selectedFixProviderId: String = "gemini",
    val isLaunchingBuild: Boolean = false,
    val launchedBuildId: String? = null,
    val errorMessage: String? = null,
    /** The currently active provider id for the model selector chip */
    val activeProviderId: String = "gemini",
    /** The currently active model name for the model selector chip */
    val activeModel: String = "gemini-2.5-flash"
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceStorage(application)
    private val db = AppDatabase.getInstance(application)
    private val aiRepo = AiProviderRepository(prefs)
    val projectRepo = ProjectRepository(application)

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    // Providers that actually have an API key configured
    private val _usableProviders = MutableStateFlow<List<com.example.data.model.ProviderConfig>>(emptyList())
    val usableProviders: StateFlow<List<com.example.data.model.ProviderConfig>> = _usableProviders.asStateFlow()

    // Which provider the chat should use right now
    private val _chatProviderId = MutableStateFlow("")
    val chatProviderId: StateFlow<String> = _chatProviderId.asStateFlow()

    init {
        loadProvidersAndDefaults()
    }

    fun loadProvidersAndDefaults() {
        val providers = prefs.getProviders()
        val codegenId = prefs.selectedCodegenProviderId.ifEmpty { "gemini" }
        val fixId = prefs.selectedFixProviderId.ifEmpty { "gemini" }

        // Determine providers that actually have a real API key
        val usable = aiRepo.getUsableProviders()
        _usableProviders.value = usable
        // Auto-select the best chat provider: prefer codegenId if it has a key, else first usable
        val bestChatProvider = usable.firstOrNull { it.id == codegenId }?.id
            ?: usable.firstOrNull()?.id
            ?: codegenId
        _chatProviderId.value = bestChatProvider

        // Restore persisted model selector choice
        val savedChoice = prefs.selectedActiveModel  // "providerId::modelName" or ""
        val (restoredProvider, restoredModel) = if (savedChoice.contains("::")) {
            val parts = savedChoice.split("::", limit = 2)
            parts[0] to parts[1]
        } else {
            val defaultProvider = providers.firstOrNull { it.id == codegenId } ?: providers.firstOrNull()
            val pid = defaultProvider?.id ?: "gemini"
            val model = defaultProvider?.defaultModel ?: "gemini-2.5-flash"
            pid to model
        }

        _state.value = _state.value.copy(
            availableProviders = providers,
            selectedCodegenProviderId = codegenId,
            selectedFixProviderId = fixId,
            activeProviderId = restoredProvider,
            // Show the model that will actually be used (it can change, e.g. when a retired model is migrated)
            activeModel = providers.firstOrNull { it.id == restoredProvider }?.defaultModel?.ifBlank { null } ?: restoredModel
        )
    }

    fun selectChatProvider(providerId: String) {
        _chatProviderId.value = providerId
    }

    /**
     * Called from the Model Selector bottom sheet when the user picks a provider+model.
     * Persists the choice and drives code generation provider accordingly.
     */
    fun selectModel(providerId: String, modelName: String) {
        prefs.selectedActiveModel = "$providerId::$modelName"
        // Update the provider's defaultModel so it's used at code generation time
        val providers = _state.value.availableProviders.toMutableList()
        val idx = providers.indexOfFirst { it.id == providerId }
        if (idx >= 0) {
            providers[idx] = providers[idx].copy(defaultModel = modelName)
            prefs.saveProviders(providers)
        }
        _state.value = _state.value.copy(
            activeProviderId = providerId,
            activeModel = modelName,
            selectedCodegenProviderId = providerId,
            availableProviders = providers
        )
        prefs.selectedCodegenProviderId = providerId
        _chatProviderId.value = providerId
    }

    fun onPlatformChanged(platform: PlatformType) {
        val newBlueprints = BlueprintsData.getBlueprints(platform)
        val defaultBp = newBlueprints.first()
        _state.value = _state.value.copy(
            platform = platform,
            blueprints = newBlueprints,
            selectedBlueprint = defaultBp,
            prompt = defaultBp.defaultPrompt,
            enabledFeatures = defaultBp.defaultFeatures.toSet()
        )
    }

    fun onWebFrameworkChanged(framework: WebFramework) {
        _state.value = _state.value.copy(webFramework = framework)
    }

    fun onBlueprintSelected(blueprint: Blueprint) {
        _state.value = _state.value.copy(
            selectedBlueprint = blueprint,
            prompt = blueprint.defaultPrompt,
            enabledFeatures = blueprint.defaultFeatures.toSet(),
            appName = blueprint.title
        )
    }

    fun onAppNameChanged(name: String) {
        _state.value = _state.value.copy(appName = name)
    }

    fun onPromptChanged(prompt: String) {
        _state.value = _state.value.copy(prompt = prompt)
    }

    fun toggleFeature(feature: String, enabled: Boolean) {
        val current = _state.value.enabledFeatures.toMutableSet()
        if (enabled) current.add(feature) else current.remove(feature)
        _state.value = _state.value.copy(enabledFeatures = current)
    }

    fun onCodegenProviderChanged(providerId: String) {
        _state.value = _state.value.copy(selectedCodegenProviderId = providerId)
        prefs.selectedCodegenProviderId = providerId
    }

    fun onFixProviderChanged(providerId: String) {
        _state.value = _state.value.copy(selectedFixProviderId = providerId)
        prefs.selectedFixProviderId = providerId
    }

    fun startBuild(onNavToTracker: (String) -> Unit) {
        val buildId = UUID.randomUUID().toString()
        val currentState = _state.value

        viewModelScope.launch {
            _state.value = _state.value.copy(isLaunchingBuild = true, errorMessage = null)

            // Each project needs its own folder: derive a name from the prompt and make it unique.
            val baseName = currentState.appName.trim().ifBlank { ProjectNaming.deriveName(currentState.prompt) }
            val appName = projectRepo.uniqueProjectName(baseName, db.buildRecordDao().getAllAppNames())

            val newRecord = BuildRecord(
                id = buildId,
                appName = appName,
                platform = currentState.platform.name,
                framework = currentState.webFramework.name,
                prompt = currentState.prompt,
                blueprintId = currentState.selectedBlueprint.id,
                blueprintTitle = currentState.selectedBlueprint.title,
                codegenProviderId = currentState.selectedCodegenProviderId,
                fixProviderId = currentState.selectedFixProviderId,
                featuresJson = currentState.enabledFeatures.joinToString(","),
                status = "InProgress",
                currentStep = "Committing Workflow",
                timestamp = System.currentTimeMillis()
            )

            db.buildRecordDao().insert(newRecord)

            _state.value = _state.value.copy(
                isLaunchingBuild = false,
                launchedBuildId = buildId
            )

            onNavToTracker(buildId)
        }
    }

    val isGithubConnected: Boolean
        get() = prefs.isGithubVerified

    val githubUsername: String
        get() = prefs.githubUsername

    private val _chatMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<AiChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatGenerating = MutableStateFlow(false)
    val isChatGenerating: StateFlow<Boolean> = _isChatGenerating.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processMessage = MutableStateFlow<String?>(null)
    val processMessage: StateFlow<String?> = _processMessage.asStateFlow()

    private val _processError = MutableStateFlow<String?>(null)
    val processError: StateFlow<String?> = _processError.asStateFlow()

    fun clearProcessError() {
        _processError.value = null
    }

    fun importZipProject(uri: android.net.Uri, context: android.content.Context, customProjectName: String? = null, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processMessage.value = "Extracting and importing project ZIP..."
            _processError.value = null

            try {
                var extractedName = customProjectName?.trim()?.ifEmpty { null }
                if (extractedName == null) {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (displayNameIndex != -1) {
                                val name = cursor.getString(displayNameIndex)
                                extractedName = name.removeSuffix(".zip").removeSuffix(".ZIP")
                            }
                        }
                    }
                }
                val projectName = projectRepo.uniqueProjectName(
                    extractedName ?: "imported_project",
                    db.buildRecordDao().getAllAppNames()
                )

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Could not open selected ZIP file")

                val result = projectRepo.importProjectFromZipStream(inputStream, projectName)
                if (result.isSuccess) {
                    val projectDir = result.getOrThrow()
                    val files = projectRepo.readProjectFiles(projectDir)
                    val isWeb = files.any { it.path.endsWith("index.html") || it.path.endsWith("package.json") }
                    val platform = if (isWeb) "WEB" else "ANDROID"

                    val recordId = UUID.randomUUID().toString()
                    val record = BuildRecord(
                        id = recordId,
                        appName = projectName,
                        prompt = "Imported from local ZIP archive ($projectName)",
                        platform = platform,
                        framework = if (isWeb) "REACT_VITE" else "COMPOSE",
                        featuresJson = "Local Import",
                        codegenProviderId = prefs.selectedCodegenProviderId.ifEmpty { "gemini" },
                        status = "Completed",
                        currentStep = "Finished",
                        webDeployUrl = if (isWeb) "http://localhost:5173" else null,
                        timestamp = System.currentTimeMillis()
                    )
                    db.buildRecordDao().insert(record)
                    _isProcessing.value = false
                    _processMessage.value = null
                    onComplete(recordId)
                } else {
                    _isProcessing.value = false
                    _processMessage.value = null
                    _processError.value = result.exceptionOrNull()?.message ?: "Failed to extract project ZIP."
                }
            } catch (e: Exception) {
                _isProcessing.value = false
                _processMessage.value = null
                _processError.value = e.message ?: "Failed to import ZIP file"
            }
        }
    }

    fun cloneGitHubRepo(repoUrlOrSlug: String, branch: String? = null, customToken: String? = null, onComplete: (String) -> Unit) {
        val trimmed = repoUrlOrSlug.trim()
        if (trimmed.isBlank()) {
            _processError.value = "Please enter a GitHub repository URL or 'owner/repo' slug."
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _processMessage.value = "Cloning repository from GitHub..."
            _processError.value = null

            try {
                val token = customToken?.trim()?.ifEmpty { null } ?: prefs.githubToken.ifBlank { null }
                val result = projectRepo.cloneGitHubRepo(trimmed, branch, token, db.buildRecordDao().getAllAppNames())

                if (result.isSuccess) {
                    val (projectDir, projectName) = result.getOrThrow()
                    val files = projectRepo.readProjectFiles(projectDir)
                    val isWeb = files.any { it.path.endsWith("index.html") || it.path.endsWith("package.json") }
                    val platform = if (isWeb) "WEB" else "ANDROID"

                    val recordId = UUID.randomUUID().toString()
                    val record = BuildRecord(
                        id = recordId,
                        appName = projectName,
                        prompt = "Cloned from GitHub: $trimmed",
                        platform = platform,
                        framework = if (isWeb) "REACT_VITE" else "COMPOSE",
                        featuresJson = "GitHub Clone",
                        codegenProviderId = prefs.selectedCodegenProviderId.ifEmpty { "gemini" },
                        status = "Completed",
                        currentStep = "Finished",
                        webDeployUrl = if (isWeb) "http://localhost:5173" else null,
                        timestamp = System.currentTimeMillis()
                    )
                    db.buildRecordDao().insert(record)
                    _isProcessing.value = false
                    _processMessage.value = null
                    onComplete(recordId)
                } else {
                    _isProcessing.value = false
                    _processMessage.value = null
                    _processError.value = result.exceptionOrNull()?.message ?: "Failed to clone GitHub repository."
                }
            } catch (e: Exception) {
                _isProcessing.value = false
                _processMessage.value = null
                _processError.value = e.message ?: "Failed to clone GitHub repository"
            }
        }
    }

    fun sendChatMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isBlank()) return

        val userMessage = AiChatMessage(
            sender = ChatSender.USER,
            text = trimmed
        )
        _chatMessages.value = _chatMessages.value + userMessage

        viewModelScope.launch {
            _isChatGenerating.value = true
            val providerId = _chatProviderId.value.ifBlank { _state.value.selectedCodegenProviderId }

            val systemPrompt = """
                You are an expert AI Software Architect and UI/UX Designer for Kodrix App Builder.
                Analyze the user's project idea and provide a concise, high-value architecture recommendation:
                1. Recommended Platform (Web or Android) & Tech Stack.
                2. Key Architecture & UI components.
                3. Refined, production-grade prompt they can use to build it.
                Keep it helpful, clear, and direct.
            """.trimIndent()

            // Send the conversation so far (minus error notices), not just the newest message
            val history = _chatMessages.value
                .filterNot { it.sender == ChatSender.AI && it.text.startsWith(ARCHITECT_ERROR_PREFIX) }
                .map { if (it.sender == ChatSender.USER) ChatTurn.user(it.text) else ChatTurn.assistant(it.text) }
            val chatResult = aiRepo.chat(
                providerId = providerId,
                systemPrompt = systemPrompt,
                history = ChatTurn.recent(history, maxTurns = 20, maxChars = 24_000)
            )

            if (chatResult.isSuccess) {
                val aiResponseText = chatResult.getOrThrow()
                val cat = if (aiResponseText.contains("Android", ignoreCase = true) || trimmed.contains("mobile", ignoreCase = true) || trimmed.contains("android", ignoreCase = true)) {
                    "mobile"
                } else {
                    "website"
                }

                _chatMessages.value = _chatMessages.value + AiChatMessage(
                    sender = ChatSender.AI,
                    text = aiResponseText,
                    suggestedPrompt = trimmed,
                    suggestedCategory = cat
                )
            } else {
                val errorMsg = chatResult.exceptionOrNull()?.localizedMessage ?: "Failed to connect to AI provider"
                _chatMessages.value = _chatMessages.value + AiChatMessage(
                    sender = ChatSender.AI,
                    text = "$ARCHITECT_ERROR_PREFIX$errorMsg. Please check your API key in Settings.",
                    suggestedPrompt = trimmed
                )
            }
            _isChatGenerating.value = false
        }
    }

    private companion object {
        const val ARCHITECT_ERROR_PREFIX = "Architect response error: "
    }
}
