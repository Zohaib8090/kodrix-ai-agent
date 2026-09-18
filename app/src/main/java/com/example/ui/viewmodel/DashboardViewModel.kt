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
    val errorMessage: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceStorage(application)
    private val db = AppDatabase.getInstance(application)
    private val aiRepo = AiProviderRepository(prefs)
    val nodeService = NodeService(application)
    val projectRepo = ProjectRepository(application)

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        loadProvidersAndDefaults()
    }

    private fun loadProvidersAndDefaults() {
        val providers = prefs.getProviders()
        val codegenId = prefs.selectedCodegenProviderId.ifEmpty { "gemini" }
        val fixId = prefs.selectedFixProviderId.ifEmpty { "gemini" }

        _state.value = _state.value.copy(
            availableProviders = providers,
            selectedCodegenProviderId = codegenId,
            selectedFixProviderId = fixId
        )
        refreshPreview()
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

    private val _chatMessages = MutableStateFlow<List<AiChatMessage>>(listOf(
        AiChatMessage(
            sender = ChatSender.AI,
            text = "Hi! I'm your AI App Architect. Describe what you'd like to build (e.g. an e-commerce store, a habit tracker, or an AI assistant), and I'll help plan the architecture, UI layout, and create the optimal prompt for you!"
        )
    ))
    val chatMessages: StateFlow<List<AiChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatGenerating = MutableStateFlow(false)
    val isChatGenerating: StateFlow<Boolean> = _isChatGenerating.asStateFlow()

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
            val providerId = _state.value.selectedCodegenProviderId

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
