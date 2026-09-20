package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BuildRecord
import com.example.data.local.PreferenceStorage
import com.example.data.model.AppContext
import com.example.data.model.Blueprint
import com.example.data.model.BlueprintsData
import com.example.data.model.CodeArtifact
import com.example.data.model.PlatformType
import com.example.data.model.ProviderConfig
import com.example.data.model.WebFramework
import com.example.data.services.AiProviderRepository
import com.example.data.services.NodeService
import com.example.data.services.ProjectRepository
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
    val appName: String = "My App",
    val prompt: String = "",
    val enabledFeatures: Set<String> = BlueprintsData.webBlueprints.first().defaultFeatures.toSet(),
    val availableProviders: List<ProviderConfig> = emptyList(),
    val selectedCodegenProviderId: String = "gemini",
    val selectedFixProviderId: String = "gemini",
    val generatedPreviewHtml: String? = null,
    val isGeneratingPreview: Boolean = false,
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
    val nodeService = NodeService(application)
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

    private fun loadProvidersAndDefaults() {
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
            activeModel = restoredModel
        )
        refreshPreview()
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
        refreshPreview()
    }

    fun onWebFrameworkChanged(framework: WebFramework) {
        _state.value = _state.value.copy(webFramework = framework)
        refreshPreview()
    }

    fun onBlueprintSelected(blueprint: Blueprint) {
        _state.value = _state.value.copy(
            selectedBlueprint = blueprint,
            prompt = blueprint.defaultPrompt,
            enabledFeatures = blueprint.defaultFeatures.toSet(),
            appName = blueprint.title
        )
        refreshPreview()
    }

    fun onAppNameChanged(name: String) {
        _state.value = _state.value.copy(appName = name)
        refreshPreview()
    }

    fun onPromptChanged(prompt: String) {
        _state.value = _state.value.copy(prompt = prompt)
        refreshPreview()
    }

    fun toggleFeature(feature: String, enabled: Boolean) {
        val current = _state.value.enabledFeatures.toMutableSet()
        if (enabled) current.add(feature) else current.remove(feature)
        _state.value = _state.value.copy(enabledFeatures = current)
        refreshPreview()
    }

    fun onCodegenProviderChanged(providerId: String) {
        _state.value = _state.value.copy(selectedCodegenProviderId = providerId)
        prefs.selectedCodegenProviderId = providerId
    }

    fun onFixProviderChanged(providerId: String) {
        _state.value = _state.value.copy(selectedFixProviderId = providerId)
        prefs.selectedFixProviderId = providerId
    }

    fun refreshPreview() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isGeneratingPreview = true)
            val context = AppContext(
                appName = _state.value.appName,
                platform = _state.value.platform,
                framework = _state.value.webFramework,
                features = _state.value.enabledFeatures.toList(),
                prompt = _state.value.prompt
            )
            val result = aiRepo.generateCode(
                providerId = _state.value.selectedCodegenProviderId,
                prompt = _state.value.prompt,
                context = context
            )
            val artifact = result.getOrNull()
            if (artifact != null) {
                // Save real parsed files to disk in kodrix-projects directory
                val projectDir = projectRepo.saveArtifactToDisk(_state.value.appName, artifact)
                if (_state.value.platform == PlatformType.WEB) {
                    // Start local HTTP server on port 5173 to serve the real files
                    nodeService.startLocalDevServer(projectDir, port = 5173)
                }
            }
            val previewHtml = artifact?.previewHtml
            _state.value = _state.value.copy(
                isGeneratingPreview = false,
                generatedPreviewHtml = previewHtml
            )
        }
    }

    fun startBuild(onNavToTracker: (String) -> Unit) {
        val buildId = UUID.randomUUID().toString()
        val currentState = _state.value

        viewModelScope.launch {
            _state.value = _state.value.copy(isLaunchingBuild = true, errorMessage = null)

            val newRecord = BuildRecord(
                id = buildId,
                appName = currentState.appName.ifBlank { currentState.selectedBlueprint.title },
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
                val projectName = extractedName ?: "imported_project_${System.currentTimeMillis() % 10000}"

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
                val result = projectRepo.cloneGitHubRepo(trimmed, branch, token)

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

            val chatResult = aiRepo.chat(
                providerId = providerId,
                systemPrompt = systemPrompt,
                userPrompt = trimmed
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
                    text = "Architect response error: $errorMsg. Please check your API key in Settings.",
                    suggestedPrompt = trimmed
                )
            }
            _isChatGenerating.value = false
        }
    }
}
