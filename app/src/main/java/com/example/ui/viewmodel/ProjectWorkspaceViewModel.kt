package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiResponseParser
import com.example.data.ai.BaseAiProvider
import com.example.data.ai.ChatTurn
import com.example.data.ai.ProjectContextBuilder
import com.example.data.local.AppDatabase
import com.example.data.local.BuildRecord
import com.example.data.local.PreferenceStorage
import com.example.data.local.ProjectChatMessage
import com.example.data.local.ProjectChatSession
import com.example.data.model.AppContext
import com.example.data.model.CodeArtifact
import com.example.data.model.PlatformType
import com.example.data.model.ProviderConfig
import com.example.data.model.SourceFile
import com.example.data.model.WebFramework
import com.example.data.services.AiProviderRepository
import com.example.data.services.EmbeddedTermuxManager
import com.example.data.services.NodeService
import com.example.data.services.ProjectFileNode
import com.example.data.services.ProjectRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class WorkspaceBottomNav(val title: String) {
    AI_CHAT("AI Chat"),
    CODE("Code"),
    TERMINAL("Terminal"),
    PREVIEW("Preview")
}

data class WorkspaceChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "USER" or "AI"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ProjectWorkspaceUiState(
    val record: BuildRecord? = null,
    val projectName: String = "",
    val platform: String = "WEB",
    val isGenerating: Boolean = false,
    val statusText: String = "",
    val files: List<SourceFile> = emptyList(),
    val directoryTree: ProjectFileNode? = null,
    val selectedFile: SourceFile? = null,
    val editedContent: String = "",
    val activeTab: WorkspaceBottomNav = WorkspaceBottomNav.PREVIEW,
    val previewUrl: String = "http://localhost:5173",
    val previewHtml: String? = null,
    val previewBaseUrl: String? = null,
    val chatSessions: List<ProjectChatSession> = emptyList(),
    val activeChatSession: ProjectChatSession? = null,
    val chatMessages: List<WorkspaceChatMessage> = emptyList(),
    val isAiRefining: Boolean = false,
    val notification: String? = null,
    val activeProviderId: String = "gemini",
    val activeProviderName: String = "Gemini",
    val availableProviders: List<ProviderConfig> = emptyList(),
    val hasUnsavedChanges: Boolean = false,
    val mainFolderPath: String = "my_projects",
    val terminalLogs: List<String> = emptyList(),
    val isTerminalRunning: Boolean = false,
    // Onboarding conversation state
    val isOnboarding: Boolean = false,
    val isOnboardingThinking: Boolean = false,
    val showLinuxSubsystemPrompt: Boolean = false,
    val onboardingConversation: List<WorkspaceChatMessage> = emptyList() // full Q&A history for context
)

class ProjectWorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val db = AppDatabase.getInstance(application)
    private val prefs = PreferenceStorage(application)
    private val aiRepo = AiProviderRepository(prefs)
    private val projectRepo = ProjectRepository(application)
    private val nodeService = NodeService(application)
    private var activeAiJob: Job? = null
    private var chatSessionsJob: Job? = null
    private var chatMessagesJob: Job? = null
    private var terminalSession: com.example.data.services.RealTerminalSession? = null

    private val _state = MutableStateFlow(ProjectWorkspaceUiState())
    val state: StateFlow<ProjectWorkspaceUiState> = _state.asStateFlow()

    fun stopAiResponse() {
        activeAiJob?.cancel()
        activeAiJob = null
        val currentRecord = _state.value.record
        if (currentRecord != null && _state.value.isGenerating) {
            viewModelScope.launch {
                val stopped = currentRecord.copy(status = "Idle", currentStep = "Stopped by user")
                db.buildRecordDao().update(stopped)
            }
        }
        _state.value = _state.value.copy(
            isGenerating = false,
            isAiRefining = false,
            isOnboardingThinking = false,
            statusText = "Response stopped",
            notification = "Stopped AI response"
        )
    }

    fun executeTerminalCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return
        terminalSession?.executeCommand(trimmed) { exitCode ->
            // If command may have modified project files (e.g. npm, curl, git, rm, mkdir), refresh file tree
            val lower = trimmed.lowercase()
            if (lower.startsWith("curl") || lower.startsWith("wget") || lower.startsWith("npm") || lower.startsWith("unzip") || lower.startsWith("git") || lower.startsWith("touch") || lower.startsWith("mkdir") || lower.startsWith("rm")) {
                viewModelScope.launch {
                    val currentRecord = _state.value.record
                    if (currentRecord != null) {
                        val projectDir = projectRepo.getProjectDir(currentRecord.appName)
                        val updatedFiles = projectRepo.readProjectFiles(projectDir)
                        val tree = projectRepo.getProjectDirectoryTree(projectDir)
                        _state.value = _state.value.copy(
                            files = updatedFiles,
                            directoryTree = tree
                        )
                    }
                }
            }
        }
    }

    fun clearTerminal() {
        terminalSession?.clear()
    }

    private fun observeMessagesForSession(sessionId: String) {
        chatMessagesJob?.cancel()
        chatMessagesJob = viewModelScope.launch {
            db.projectChatDao().getMessagesForSession(sessionId).collect { msgList ->
                val uiMsgs = msgList.map {
                    WorkspaceChatMessage(
                        id = it.id,
                        sender = it.sender,
                        message = it.message,
                        timestamp = it.timestamp
                    )
                }
                _state.value = _state.value.copy(chatMessages = uiMsgs)
            }
        }
    }

    private suspend fun persistMessage(sender: String, message: String): WorkspaceChatMessage {
        val currentRecord = _state.value.record
        val projectId = currentRecord?.id ?: ""
        var activeSession = _state.value.activeChatSession
        if (activeSession == null && projectId.isNotBlank()) {
            val fallback = ProjectChatSession(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                title = "Main Chat",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            db.projectChatDao().insertSession(fallback)
            activeSession = fallback
            _state.value = _state.value.copy(activeChatSession = fallback)
            observeMessagesForSession(fallback.id)
        }

        val sessionId = activeSession?.id ?: UUID.randomUUID().toString()
        val entity = ProjectChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            projectId = projectId,
            sender = sender,
            message = message,
            timestamp = System.currentTimeMillis()
        )
        db.projectChatDao().insertMessage(entity)
        if (activeSession != null) {
            val updated = activeSession.copy(updatedAt = System.currentTimeMillis())
            db.projectChatDao().updateSession(updated)
        }
        return WorkspaceChatMessage(entity.id, entity.sender, entity.message, entity.timestamp)
    }

    fun createNewChatSession(title: String? = null) {
        val currentRecord = _state.value.record ?: return
        viewModelScope.launch {
            val count = db.projectChatDao().getDirectSessionsForProject(currentRecord.id).size
            val sessionTitle = title?.trim()?.ifBlank { null } ?: "Chat ${count + 1}"
            val newSession = ProjectChatSession(
                id = UUID.randomUUID().toString(),
                projectId = currentRecord.id,
                title = sessionTitle,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            db.projectChatDao().insertSession(newSession)
            _state.value = _state.value.copy(
                activeChatSession = newSession,
                notification = "Created new chat: $sessionTitle"
            )
            observeMessagesForSession(newSession.id)
        }
    }

    fun switchChatSession(session: ProjectChatSession) {
        if (_state.value.activeChatSession?.id == session.id) return
        _state.value = _state.value.copy(
            activeChatSession = session,
            notification = "Switched to ${session.title}"
        )
        observeMessagesForSession(session.id)
    }

    fun renameChatSession(session: ProjectChatSession, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val updated = session.copy(title = trimmed, updatedAt = System.currentTimeMillis())
            db.projectChatDao().updateSession(updated)
            if (_state.value.activeChatSession?.id == session.id) {
                _state.value = _state.value.copy(activeChatSession = updated)
            }
            _state.value = _state.value.copy(notification = "Renamed to $trimmed")
        }
    }

    fun deleteChatSession(session: ProjectChatSession) {
        val currentRecord = _state.value.record ?: return
        viewModelScope.launch {
            db.projectChatDao().deleteMessagesForSession(session.id)
            db.projectChatDao().deleteSessionById(session.id)

            val remaining = db.projectChatDao().getDirectSessionsForProject(currentRecord.id)
            if (remaining.isEmpty()) {
                val newMain = ProjectChatSession(
                    id = UUID.randomUUID().toString(),
                    projectId = currentRecord.id,
                    title = "Main Chat",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                db.projectChatDao().insertSession(newMain)
                _state.value = _state.value.copy(
                    activeChatSession = newMain,
                    notification = "Chat deleted"
                )
                observeMessagesForSession(newMain.id)
            } else {
                val nextActive = remaining.first()
                _state.value = _state.value.copy(
                    activeChatSession = nextActive,
                    notification = "Chat deleted"
                )
                observeMessagesForSession(nextActive.id)
            }
        }
    }

    fun loadProject(projectId: String) {
        // The screen calls this every time it re-enters composition (rotation, coming back from
        // Settings). Reloading would restart onboarding, the dev server and the terminal session,
        // so for the project that is already open only refresh the provider list.
        if (_state.value.record?.id == projectId) {
            val providers = aiRepo.getProviders().filter { it.isEnabled }
            _state.value = _state.value.copy(
                availableProviders = providers,
                activeProviderName = providers.firstOrNull { it.id == _state.value.activeProviderId }?.name
                    ?: _state.value.activeProviderName
            )
            return
        }
        activeAiJob?.cancel()
        chatSessionsJob?.cancel()
        chatMessagesJob?.cancel()

        activeAiJob = viewModelScope.launch {
            val record = db.buildRecordDao().getRecordDirect(projectId)
            if (record == null) {
                _state.value = _state.value.copy(
                    notification = "Project not found for ID: $projectId"
                )
                return@launch
            }

            val allProviders = aiRepo.getProviders().filter { it.isEnabled }
            val providerConfig = aiRepo.getProviderConfig(record.codegenProviderId)
            val providerName = providerConfig?.name ?: record.codegenProviderId.replaceFirstChar { it.uppercase() }

            // Ensure at least one chat session exists in Room
            val directSessions = db.projectChatDao().getDirectSessionsForProject(projectId)
            val currentActiveSession: ProjectChatSession = if (directSessions.isEmpty()) {
                val initialSession = ProjectChatSession(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    title = "Main Chat",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                db.projectChatDao().insertSession(initialSession)
                initialSession
            } else {
                directSessions.first()
            }

            _state.value = _state.value.copy(
                record = record,
                projectName = record.appName,
                platform = record.platform,
                activeProviderId = record.codegenProviderId,
                activeProviderName = providerName,
                availableProviders = allProviders,
                activeChatSession = currentActiveSession,
                mainFolderPath = "my_projects/${record.appName}"
            )

            // Start observing chat sessions flow for this project
            chatSessionsJob = viewModelScope.launch {
                db.projectChatDao().getSessionsForProject(projectId).collect { sessionList ->
                    val curActive = _state.value.activeChatSession
                    val validActive = sessionList.firstOrNull { it.id == curActive?.id } ?: sessionList.firstOrNull()
                    _state.value = _state.value.copy(
                        chatSessions = sessionList,
                        activeChatSession = validActive
                    )
                }
            }

            // Start observing messages for the active session
            observeMessagesForSession(currentActiveSession.id)

            val projectDir = projectRepo.getProjectDir(record.appName)
            terminalSession?.destroy()
            val session = nodeService.createTerminalSession(projectDir)
            terminalSession = session

            // Stream terminal logs into state
            viewModelScope.launch {
                session.outputLines.collect { lines ->
                    _state.value = _state.value.copy(terminalLogs = lines)
                }
            }
            viewModelScope.launch {
                session.isRunning.collect { running ->
                    _state.value = _state.value.copy(isTerminalRunning = running)
                }
            }

            val existingFiles = projectRepo.readProjectFiles(projectDir)
            val needsGeneration = existingFiles.isEmpty() || record.status.equals("InProgress", ignoreCase = true)

            if (needsGeneration) {
                // Start onboarding conversation — AI gathers requirements before building
                startOnboarding(record)
            } else {
                val tree = projectRepo.getProjectDirectoryTree(projectDir)
                val defaultFile = existingFiles.firstOrNull { it.path.endsWith("index.html") }
                    ?: existingFiles.firstOrNull { it.path.endsWith(".html") }
                    ?: existingFiles.firstOrNull { it.path.endsWith(".kt") }
                    ?: existingFiles.firstOrNull()

                val htmlFile = existingFiles.firstOrNull { it.path.endsWith("index.html") }
                    ?: existingFiles.firstOrNull { it.path.endsWith(".html") }

                if (record.platform.equals("WEB", ignoreCase = true)) {
                    nodeService.startLocalDevServer(projectDir, port = 5173)
                }

                _state.value = _state.value.copy(
                    files = existingFiles,
                    directoryTree = tree,
                    selectedFile = defaultFile,
                    editedContent = defaultFile?.content ?: "",
                    previewHtml = htmlFile?.content,
                    previewBaseUrl = "file://${projectDir.absolutePath}/",
                    previewUrl = "http://localhost:5173",
                    isGenerating = false,
                    statusText = "Ready",
                    activeTab = if (record.platform.equals("WEB", ignoreCase = true)) WorkspaceBottomNav.PREVIEW else WorkspaceBottomNav.CODE
                )
            }
        }
    }

    fun retryPrompt(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isBlank()) return
        val currentRecord = _state.value.record
        if (currentRecord != null && _state.value.files.isEmpty()) {
            activeAiJob?.cancel()
            activeAiJob = viewModelScope.launch {
                val projectDir = projectRepo.getProjectDir(currentRecord.appName)
                generateProjectCode(currentRecord.copy(prompt = trimmed), projectDir)
            }
        } else {
            refineWithAi(trimmed)
        }
    }

    /**
     * Starts the AI onboarding conversation.
     * Instead of immediately building, Kodrix asks smart clarifying questions.
     */
    private suspend fun startOnboarding(record: BuildRecord) {
        val userOpeningMsg = persistMessage("USER", record.prompt)
        _state.value = _state.value.copy(
            isOnboarding = true,
            isOnboardingThinking = true,
            onboardingConversation = listOf(userOpeningMsg),
            activeTab = WorkspaceBottomNav.AI_CHAT,
            statusText = "Kodrix is analyzing your request..."
        )

        val systemPrompt = """
            You are Kodrix, a friendly and expert AI app builder.
            A user wants to build an app. Their initial request is below.
            
            Your job is to ask ONE smart, specific clarifying question to better understand what they need.
            Focus on the most important missing detail — this could be about:
            - The app name (if not mentioned)
            - The target platform (web, Android, or both — if unclear)
            - Key features or screens they want
            - The visual style or target audience
            - Any other critical design decision
            
            Rules:
            - Ask only ONE question. Never multiple at once.
            - Keep it short (1-3 sentences max).
            - Be friendly, warm, and conversational.
            - Do NOT generate any code.
            - Do NOT say you will now build the app.
            - If the request is already very clear and specific, you may confirm briefly and say:
              "READY_TO_BUILD" as your last line to signal you have enough context.
        """.trimIndent()

        val firstQuestion = aiRepo.chat(
            providerId = _state.value.activeProviderId,
            systemPrompt = systemPrompt,
            userPrompt = "User request: ${record.prompt}"
        )

        if (firstQuestion.isSuccess) {
            val aiText = firstQuestion.getOrThrow().trim()
            if (aiText.contains("READY_TO_BUILD", ignoreCase = true)) {
                // AI already has enough context — build immediately
                val cleanText = aiText.replace("READY_TO_BUILD", "").trim()
                val aiMsg = if (cleanText.isNotBlank()) persistMessage("AI", cleanText) else null
                val allMsgs = if (aiMsg != null) listOf(userOpeningMsg, aiMsg) else listOf(userOpeningMsg)
                _state.value = _state.value.copy(
                    onboardingConversation = allMsgs,
                    isOnboarding = false,
                    isOnboardingThinking = false
                )
                val enriched = buildEnrichedPrompt(_state.value.onboardingConversation, record.prompt)
                val projectDir = projectRepo.getProjectDir(record.appName)
                generateProjectCode(record.copy(prompt = enriched), projectDir)
            } else {
                val aiMsg = persistMessage("AI", aiText)
                val conversation = listOf(userOpeningMsg, aiMsg)
                _state.value = _state.value.copy(
                    onboardingConversation = conversation,
                    isOnboarding = true,
                    isOnboardingThinking = false,
                    statusText = "Answer Kodrix's questions to get started"
                )
            }
        } else {
            val errorDetails = firstQuestion.exceptionOrNull()?.message ?: "Unable to connect to AI provider"
            val aiMsg = persistMessage(
                "AI",
                "${ERROR_PREFIX}Could not connect to ${_state.value.activeProviderName}:\n\n$errorDetails\n\nPlease configure your API key for ${_state.value.activeProviderName} in Settings (top-right gear icon), then reply here to continue."
            )
            val conversation = listOf(userOpeningMsg, aiMsg)
            _state.value = _state.value.copy(
                onboardingConversation = conversation,
                isOnboarding = true,
                isOnboardingThinking = false,
                statusText = "API key required or AI connection error"
            )
        }
    }

    /**
     * Called when the user replies during the onboarding conversation.
     * The AI decides when it has enough info and signals READY_TO_BUILD.
     */
    fun sendOnboardingReply(userMessage: String) {
        val trimmed = userMessage.trim()
        if (trimmed.isBlank()) return
        val record = _state.value.record ?: return

        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            val userMsg = persistMessage("USER", trimmed)
            val updatedConversation = _state.value.onboardingConversation + userMsg

            _state.value = _state.value.copy(
                onboardingConversation = updatedConversation,
                isOnboardingThinking = true
            )

            val systemPrompt = """
                You are Kodrix, a friendly and expert AI app builder.
                You are gathering requirements to build an app for a user.
                The conversation so far is in the messages.
                
                Based on what you know now:
                - If you still need ONE more critical piece of information, ask ONLY that one question (1-3 sentences, friendly).
                - If you have enough context to build a great app, respond with a brief enthusiastic confirmation (1-2 sentences) 
                  then end your reply with exactly: READY_TO_BUILD
                  
                Do NOT generate code. Do NOT ask multiple questions.
            """.trimIndent()

            val aiReply = aiRepo.chat(
                providerId = _state.value.activeProviderId,
                systemPrompt = systemPrompt,
                history = toChatTurns(updatedConversation.map { it.sender to it.message })
            )

            if (aiReply.isSuccess) {
                val rawText = aiReply.getOrThrow().trim()
                val readyToBuild = rawText.contains("READY_TO_BUILD", ignoreCase = true)
                val cleanText = rawText.replace("READY_TO_BUILD", "").trim()

                val aiMsg = persistMessage("AI", cleanText)
                val fullConversation = updatedConversation + aiMsg

                _state.value = _state.value.copy(
                    onboardingConversation = fullConversation,
                    isOnboardingThinking = false
                )

                if (readyToBuild) {
                    // Enough info gathered — build the project
                    _state.value = _state.value.copy(isOnboarding = false)
                    val enriched = buildEnrichedPrompt(fullConversation, record.prompt)
                    val projectDir = projectRepo.getProjectDir(record.appName)
                    generateProjectCode(record.copy(prompt = enriched), projectDir)
                }
            } else {
                // AI error — keep onboarding going, show error
                val reason = aiReply.exceptionOrNull()?.message ?: "Unknown error"
                persistMessage(
                    "AI",
                    "${ERROR_PREFIX}Could not reach the AI provider: $reason\n\nPlease check your API key in Settings, then try replying again."
                )
                _state.value = _state.value.copy(
                    isOnboardingThinking = false
                )
            }
        }
    }

    /**
     * Builds an enriched prompt from the full onboarding conversation for use in code generation.
     */
    private fun buildEnrichedPrompt(conversation: List<WorkspaceChatMessage>, originalPrompt: String): String {
        val qaContext = conversation.joinToString("\n") {
            "${if (it.sender == "USER") "User" else "Kodrix"}: ${it.message}"
        }
        return """
            Original request: $originalPrompt
            
            Full requirements conversation:
            $qaContext
            
            Based on the above conversation, build exactly what the user described.
        """.trimIndent()
    }

    fun selectProvider(providerId: String) {
        val config = aiRepo.getProviderConfig(providerId)
        val name = config?.name ?: providerId.replaceFirstChar { it.uppercase() }
        _state.value = _state.value.copy(
            activeProviderId = providerId,
            activeProviderName = name,
            notification = "Switched AI to $name"
        )
        val currentRecord = _state.value.record ?: return
        viewModelScope.launch {
            val updated = currentRecord.copy(codegenProviderId = providerId)
            db.buildRecordDao().update(updated)
            _state.value = _state.value.copy(record = updated)
        }
    }

    private suspend fun generateProjectCode(record: BuildRecord, projectDir: File) {
        _state.value = _state.value.copy(
            // Requirements gathering is over: later chat messages are change requests
            isOnboarding = false,
            isGenerating = true,
            statusText = "Contacting ${_state.value.activeProviderName} to write application code..."
        )

        val isWeb = record.platform.equals("WEB", ignoreCase = true)
        val appContext = AppContext(
            appName = record.appName,
            platform = if (isWeb) PlatformType.WEB else PlatformType.ANDROID,
            framework = try { WebFramework.valueOf(record.framework) } catch (_: Exception) { WebFramework.REACT_VITE },
            features = record.featuresJson.split(",").filter { it.isNotBlank() },
            prompt = record.prompt
        )

        _state.value = _state.value.copy(
            statusText = "AI is writing source code and structuring files..."
        )

        val codeResult = aiRepo.generateCode(_state.value.activeProviderId, record.prompt, appContext)
        val artifact = codeResult.getOrNull()

        if (artifact == null || artifact.files.isEmpty()) {
            val errorReason = codeResult.exceptionOrNull()?.message ?: "AI provider returned empty response."
            val failedRecord = record.copy(
                status = "Failed",
                currentStep = "Failed: $errorReason"
            )
            db.buildRecordDao().update(failedRecord)

            persistMessage(
                "AI",
                "${ERROR_PREFIX}Code generation failed:\n\n$errorReason\n\nPlease check your API key in Settings, verify your provider connection, and try again."
            )

            _state.value = _state.value.copy(
                record = failedRecord,
                isGenerating = false,
                statusText = "Generation failed: $errorReason",
                activeTab = WorkspaceBottomNav.AI_CHAT,
                notification = "Build failed: $errorReason"
            )
            return
        }

        val savedDir = projectRepo.saveArtifactToDisk(record.appName, artifact)

        if (isWeb) {
            nodeService.startLocalDevServer(savedDir, port = 5173)
        }

        val freshFiles = projectRepo.readProjectFiles(savedDir)
        val tree = projectRepo.getProjectDirectoryTree(savedDir)

        val defaultFile = freshFiles.firstOrNull { it.path.endsWith("index.html") }
            ?: freshFiles.firstOrNull { it.path.endsWith(".html") }
            ?: freshFiles.firstOrNull { it.path.endsWith(".kt") }
            ?: freshFiles.firstOrNull()

        val htmlContent = freshFiles.firstOrNull { it.path.endsWith("index.html") }?.content
            ?: artifact?.previewHtml

        // Update database record to Completed
        val updatedRecord = record.copy(
            status = "Completed",
            currentStep = "Finished",
            webDeployUrl = if (isWeb) "http://localhost:5173" else null
        )
        db.buildRecordDao().update(updatedRecord)

        // Trigger background notification to alert user that project coding is complete
        try {
            NotificationHelper.notifyBuildCompleted(
                context = context,
                projectName = record.appName,
                projectId = record.id,
                isSuccess = true
            )
        } catch (_: Exception) {}

        persistMessage(
            "AI",
            "I've built your application '${record.appName}'.\n\n" +
                    "Generated ${freshFiles.size} project file(s) in 'my_projects/${record.appName}/':\n" +
                    freshFiles.joinToString("\n") { "• ${it.path}" } +
                    "\n\nUse the bottom navigation to view the live Preview, inspect Code files in the directory, or chat with me for modifications." +
                    if (artifact.incompleteFiles.isNotEmpty()) {
                        "\n\nThe response was cut off, so these files were not created: ${artifact.incompleteFiles.joinToString()}. " +
                            "Ask me to add them."
                    } else {
                        ""
                    }
        )

        _state.value = _state.value.copy(
            record = updatedRecord,
            isGenerating = false,
            statusText = "Project generated successfully!",
            files = freshFiles,
            directoryTree = tree,
            selectedFile = defaultFile,
            editedContent = defaultFile?.content ?: "",
            previewHtml = htmlContent,
            previewBaseUrl = "file://${savedDir.absolutePath}/",
            previewUrl = "http://localhost:5173",
            activeTab = if (isWeb) WorkspaceBottomNav.PREVIEW else WorkspaceBottomNav.CODE,
            notification = "Generated ${freshFiles.size} project files!"
        )
    }

    fun selectTab(tab: WorkspaceBottomNav) {
        _state.value = _state.value.copy(activeTab = tab)
    }

    fun selectFile(file: SourceFile) {
        _state.value = _state.value.copy(
            selectedFile = file,
            editedContent = file.content,
            hasUnsavedChanges = false
        )
    }

    fun selectFileByPath(relativePath: String) {
        val target = _state.value.files.firstOrNull { it.path == relativePath }
        if (target != null) {
            selectFile(target)
        }
    }

    fun updateEditedContent(newContent: String) {
        _state.value = _state.value.copy(
            editedContent = newContent,
            hasUnsavedChanges = true
        )
    }

    fun saveCurrentFile() {
        val currentFile = _state.value.selectedFile ?: return
        val currentRecord = _state.value.record ?: return
        val newContent = _state.value.editedContent

        viewModelScope.launch {
            val projectDir = projectRepo.getProjectDir(currentRecord.appName)
            val success = projectRepo.saveFile(projectDir, currentFile.path, newContent)

            if (success) {
                val updatedFiles = projectRepo.readProjectFiles(projectDir)
                val tree = projectRepo.getProjectDirectoryTree(projectDir)
                val updatedHtml = updatedFiles.firstOrNull { it.path.endsWith("index.html") }?.content

                _state.value = _state.value.copy(
                    files = updatedFiles,
                    directoryTree = tree,
                    selectedFile = currentFile.copy(content = newContent),
                    hasUnsavedChanges = false,
                    previewHtml = updatedHtml ?: _state.value.previewHtml,
                    notification = "Saved ${currentFile.path} to disk!"
                )
            } else {
                _state.value = _state.value.copy(
                    notification = "Failed to save ${currentFile.path}"
                )
            }
        }
    }

    fun createNewFile(relativePath: String, content: String = "") {
        val currentRecord = _state.value.record ?: return
        if (relativePath.isBlank()) return

        viewModelScope.launch {
            val projectDir = projectRepo.getProjectDir(currentRecord.appName)
            val success = projectRepo.saveFile(projectDir, relativePath, content)
            if (success) {
                val updatedFiles = projectRepo.readProjectFiles(projectDir)
                val tree = projectRepo.getProjectDirectoryTree(projectDir)
                val newFile = updatedFiles.firstOrNull { it.path == relativePath }
                _state.value = _state.value.copy(
                    files = updatedFiles,
                    directoryTree = tree,
                    selectedFile = newFile ?: _state.value.selectedFile,
                    editedContent = content,
                    notification = "Created file $relativePath"
                )
            } else {
                _state.value = _state.value.copy(notification = "Failed to create file $relativePath")
            }
        }
    }

    fun createNewFolder(relativePath: String) {
        val currentRecord = _state.value.record ?: return
        if (relativePath.isBlank()) return

        viewModelScope.launch {
            val projectDir = projectRepo.getProjectDir(currentRecord.appName)
            val success = projectRepo.createNewFolder(projectDir, relativePath)
            if (success) {
                val tree = projectRepo.getProjectDirectoryTree(projectDir)
                _state.value = _state.value.copy(
                    directoryTree = tree,
                    notification = "Created folder $relativePath"
                )
            } else {
                _state.value = _state.value.copy(notification = "Failed to create folder")
            }
        }
    }

    fun deleteFile(relativePath: String) {
        val currentRecord = _state.value.record ?: return
        viewModelScope.launch {
            val projectDir = projectRepo.getProjectDir(currentRecord.appName)
            val success = projectRepo.deleteFile(projectDir, relativePath)
            if (success) {
                val updatedFiles = projectRepo.readProjectFiles(projectDir)
                val tree = projectRepo.getProjectDirectoryTree(projectDir)
                val nextSelected = if (_state.value.selectedFile?.path == relativePath) {
                    updatedFiles.firstOrNull()
                } else {
                    _state.value.selectedFile
                }
                _state.value = _state.value.copy(
                    files = updatedFiles,
                    directoryTree = tree,
                    selectedFile = nextSelected,
                    editedContent = nextSelected?.content ?: "",
                    notification = "Deleted $relativePath"
                )
            } else {
                _state.value = _state.value.copy(notification = "Failed to delete $relativePath")
            }
        }
    }

    fun refineWithAi(userInstruction: String) {
        if (userInstruction.isBlank()) return
        val currentRecord = _state.value.record ?: return
        val currentFiles = _state.value.files
        val activePid = _state.value.activeProviderId
        val openFilePath = _state.value.selectedFile?.path

        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            persistMessage("USER", userInstruction)
            _state.value = _state.value.copy(
                isAiRefining = true
            )

            val linuxInstalled = withContext(Dispatchers.IO) { EmbeddedTermuxManager(context).isInstalled }
            val projectContext = ProjectContextBuilder.describeFiles(currentFiles, MAX_PROJECT_CONTEXT_CHARS, openFilePath)
            val systemPrompt = buildRefineSystemPrompt(currentRecord, projectContext, linuxInstalled)

            // The whole recent conversation, so follow-ups like "undo that" or "make it bigger" work.
            val sessionId = _state.value.activeChatSession?.id
            val transcript = if (sessionId != null) {
                db.projectChatDao().getDirectMessagesForSession(sessionId).map { it.sender to it.message }
            } else {
                listOf("USER" to userInstruction)
            }

            val chatResult = aiRepo.chat(
                providerId = activePid,
                systemPrompt = systemPrompt,
                history = toChatTurns(transcript)
            )

            if (chatResult.isSuccess) {
                val rawAiResponse = chatResult.getOrThrow()
                val parsedFiles = AiResponseParser.parseFiles(rawAiResponse)
                val incompleteFiles = AiResponseParser.parseIncompleteFilePaths(rawAiResponse)
                val parsedCommands = AiResponseParser.parseCommands(rawAiResponse)
                val conversationalText = AiResponseParser.extractConversationalText(rawAiResponse)

                // Commands are queued and run one after another in the terminal session
                parsedCommands.forEach { cmd ->
                    executeTerminalCommand(cmd)
                }
                if (AiResponseParser.requestsLinuxSubsystem(rawAiResponse) && !linuxInstalled) {
                    _state.value = _state.value.copy(showLinuxSubsystemPrompt = true)
                }

                val reply = buildString {
                    if (conversationalText.isNotBlank()) append(conversationalText)
                    if (parsedFiles.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append("Updated ${parsedFiles.size} file(s) in 'my_projects/${currentRecord.appName}/':\n")
                        append(parsedFiles.joinToString("\n") { "• ${it.path}" })
                    }
                    if (incompleteFiles.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append("The response was cut off, so these files were left unchanged: ${incompleteFiles.joinToString()}.")
                    }
                    if (parsedCommands.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append("Running in Terminal:\n")
                        append(parsedCommands.joinToString("\n") { "• `$it`" })
                    }
                    if (parsedFiles.isNotEmpty()) {
                        append("\n\nCheck the Preview tab to see the live updates, or the Code & Terminal tabs to inspect the changes.")
                    }
                }.ifBlank { rawAiResponse.trim() }
                    .ifBlank { "Hello! How can I assist you with '${currentRecord.appName}' today?" }

                persistMessage("AI", reply)

                if (parsedFiles.isNotEmpty()) {
                    val artifact = CodeArtifact(
                        files = parsedFiles,
                        explanation = conversationalText.ifBlank { "Updated ${parsedFiles.size} project files." },
                        previewHtml = parsedFiles.firstOrNull { it.path.endsWith("index.html") }?.content
                    )
                    val projectDir = projectRepo.saveArtifactToDisk(currentRecord.appName, artifact)
                    val updatedFiles = projectRepo.readProjectFiles(projectDir)
                    val tree = projectRepo.getProjectDirectoryTree(projectDir)
                    val updatedHtml = updatedFiles.firstOrNull { it.path.endsWith("index.html") }?.content
                    val currentSelected = _state.value.selectedFile
                    val reSelected = updatedFiles.firstOrNull { it.path == currentSelected?.path }
                        ?: updatedFiles.firstOrNull { it.path.endsWith("index.html") }
                        ?: updatedFiles.firstOrNull()

                    try {
                        NotificationHelper.notifyAiRefineCompleted(
                            context = context,
                            projectName = currentRecord.appName,
                            projectId = currentRecord.id,
                            summary = "Updated ${parsedFiles.size} file(s) for your prompt."
                        )
                    } catch (_: Exception) {}

                    _state.value = _state.value.copy(
                        isAiRefining = false,
                        files = updatedFiles,
                        directoryTree = tree,
                        selectedFile = reSelected,
                        editedContent = reSelected?.content ?: "",
                        hasUnsavedChanges = false,
                        previewHtml = updatedHtml ?: _state.value.previewHtml,
                        notification = "Applied updates to project files!"
                    )
                } else {
                    _state.value = _state.value.copy(
                        isAiRefining = false
                    )
                }
            } else {
                val errorMsg = chatResult.exceptionOrNull()?.localizedMessage ?: "Unknown AI error"
                val errText = "${ERROR_PREFIX}Could not contact AI provider: $errorMsg. Please check your API key in Settings → AI & Models."
                persistMessage("AI", errText)
                _state.value = _state.value.copy(
                    isAiRefining = false,
                    notification = "AI request failed."
                )
            }
        }
    }

    private fun buildRefineSystemPrompt(record: BuildRecord, projectContext: String, linuxInstalled: Boolean): String {
        val terminalStatus = if (linuxInstalled) {
            "The embedded Linux environment is installed: real npm, node, pkg, apt, git, curl, python and bash commands work."
        } else {
            "The embedded Linux environment is NOT installed yet. Only basic file commands (ls, cat, mkdir, rm -r, touch, echo), " +
                "curl, wget, unzip and downloading public GitHub repositories with git clone work; npm, node, pkg and python do not.\n" +
                "If the request needs npm, node, python or other packages, include the exact tag " +
                "${AiResponseParser.LINUX_SUBSYSTEM_TAG} in your reply so Kodrix can offer to install the environment, " +
                "and do not output COMMAND lines that need it."
        }
        return listOf(
            "You are an expert AI software developer and project assistant for the ${record.platform} application '${record.appName}'.",
            "",
            projectContext,
            "",
            "EMBEDDED LINUX TERMINAL:",
            terminalStatus,
            "To run a terminal command, put it on its own line starting with COMMAND:, for example:",
            "COMMAND: npm install axios",
            "",
            "INSTRUCTIONS:",
            "1. If the user is greeting you, asking a question, or chatting without requesting changes, reply conversationally and do not output any files.",
            "2. If the user requests modifications, bug fixes, design adjustments or new features, first write a 1-2 sentence summary of what you changed, " +
                "then output every modified or new file in full using this exact format:",
            BaseAiProvider.FILE_FORMAT_INSTRUCTIONS,
            "3. Only rewrite files whose full contents are shown above. Files marked [${ProjectContextBuilder.OMITTED_NOTE}] have not been shown to you: " +
                "do not output them; tell the user which file you would change and how.",
            "4. Never output partial files or placeholders such as \"rest of the code unchanged\".",
            "5. If package installations or other terminal actions are needed, output COMMAND: lines."
        ).joinToString("\n")
    }

    /** Chat transcript (sender to text, oldest first) as provider turns, without Kodrix's own error notices. */
    private fun toChatTurns(transcript: List<Pair<String, String>>): List<ChatTurn> {
        val turns = transcript
            .filterNot { (sender, text) -> sender != "USER" && text.startsWith(ERROR_PREFIX) }
            .map { (sender, text) -> if (sender == "USER") ChatTurn.user(text) else ChatTurn.assistant(text) }
        return ChatTurn.recent(turns, MAX_HISTORY_TURNS, MAX_HISTORY_CHARS)
    }

    fun dismissLinuxSubsystemPrompt() {
        _state.value = _state.value.copy(showLinuxSubsystemPrompt = false)
    }

    /** Accepts the AI's request to install the embedded Linux environment. */
    fun startLinuxSubsystemSetup() {
        _state.value = _state.value.copy(
            showLinuxSubsystemPrompt = false,
            activeTab = WorkspaceBottomNav.TERMINAL
        )
        executeTerminalCommand("setup-termux")
    }

    fun exportAsZip(context: Context) {
        val record = _state.value.record ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val projectDir = projectRepo.getProjectDir(record.appName)
                val zipFile = File(context.cacheDir, "${record.appName}-export.zip")
                projectRepo.exportProjectAsZip(projectDir, zipFile)

                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "${record.appName} Source Code")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }

                context.startActivity(Intent.createChooser(shareIntent, "Export Project ZIP").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(notification = "Export error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun openInBrowser(context: Context) {
        try {
            val url = _state.value.previewUrl
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _state.value = _state.value.copy(notification = "Browser launch error: ${e.localizedMessage}")
        }
    }

    fun clearNotification() {
        _state.value = _state.value.copy(notification = null)
    }

    override fun onCleared() {
        terminalSession?.destroy()
        _state.value.record?.let { nodeService.stopLocalDevServer(projectRepo.getProjectDir(it.appName)) }
        super.onCleared()
    }

    companion object {
        /** Marks Kodrix's own error notices in the chat so they are not sent back to the model. */
        private const val ERROR_PREFIX = "Error: "
        private const val MAX_PROJECT_CONTEXT_CHARS = 60_000
        private const val MAX_HISTORY_TURNS = 20
        private const val MAX_HISTORY_CHARS = 24_000
    }
}
