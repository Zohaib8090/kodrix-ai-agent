package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BuildRecord
import com.example.data.local.PreferenceStorage
import com.example.data.model.AppContext
import com.example.data.model.CodeArtifact
import com.example.data.model.PlatformType
import com.example.data.model.ProviderConfig
import com.example.data.model.SourceFile
import com.example.data.model.WebFramework
import com.example.data.services.AiProviderRepository
import com.example.data.services.NodeService
import com.example.data.services.ProjectFileNode
import com.example.data.services.ProjectRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
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
    val chatMessages: List<WorkspaceChatMessage> = emptyList(),
    val isAiRefining: Boolean = false,
    val notification: String? = null,
    val activeProviderId: String = "gemini",
    val activeProviderName: String = "Gemini",
    val availableProviders: List<ProviderConfig> = emptyList(),
    val hasUnsavedChanges: Boolean = false,
    val mainFolderPath: String = "my_projects"
)

class ProjectWorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val db = AppDatabase.getInstance(application)
    private val prefs = PreferenceStorage(application)
    private val aiRepo = AiProviderRepository(prefs)
    private val projectRepo = ProjectRepository(application)
    private val nodeService = NodeService(application)

    private val _state = MutableStateFlow(ProjectWorkspaceUiState())
    val state: StateFlow<ProjectWorkspaceUiState> = _state.asStateFlow()

    fun loadProject(projectId: String) {
        viewModelScope.launch {
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

            val initialMessages = listOf(
                WorkspaceChatMessage(
                    sender = "USER",
                    message = record.prompt
                )
            )

            _state.value = _state.value.copy(
                record = record,
                projectName = record.appName,
                platform = record.platform,
                activeProviderId = record.codegenProviderId,
                activeProviderName = providerName,
                availableProviders = allProviders,
                chatMessages = initialMessages,
                mainFolderPath = "my_projects/${record.appName}"
            )

            val projectDir = projectRepo.getProjectDir(record.appName)
            val existingFiles = projectRepo.readProjectFiles(projectDir)
            val needsGeneration = existingFiles.isEmpty() || record.status.equals("InProgress", ignoreCase = true)

            if (needsGeneration) {
                // Perform real AI code generation on device using the configured AI key
                generateProjectCode(record, projectDir)
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
            viewModelScope.launch {
                val projectDir = projectRepo.getProjectDir(currentRecord.appName)
                generateProjectCode(currentRecord.copy(prompt = trimmed), projectDir)
            }
        } else {
            refineWithAi(trimmed)
        }
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

        val savedDir = if (artifact != null) {
            projectRepo.saveArtifactToDisk(record.appName, artifact)
        } else {
            projectDir
        }

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

        val completionMsg = WorkspaceChatMessage(
            sender = "AI",
            message = "✨ I've built your application '${record.appName}'!\n\n" +
                    "Generated ${freshFiles.size} project file(s) in 'my_projects/${record.appName}/':\n" +
                    freshFiles.joinToString("\n") { "• ${it.path}" } +
                    "\n\nUse the bottom navigation to view the live Preview, inspect Code files in the directory, or chat with me for modifications."
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
            chatMessages = _state.value.chatMessages + completionMsg,
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

        viewModelScope.launch {
            val userMsg = WorkspaceChatMessage(sender = "USER", message = userInstruction)
            _state.value = _state.value.copy(
                chatMessages = _state.value.chatMessages + userMsg,
                isAiRefining = true
            )

            val existingSummary = currentFiles.take(8).joinToString("\n---\n") { "${it.path}:\n${it.content.take(1500)}" }

            val systemPrompt = """
                You are an expert AI software developer and project assistant for the ${currentRecord.platform} application '${currentRecord.appName}'.
                
                The project currently has these source files:
                $existingSummary

                CRITICAL INSTRUCTIONS:
                1. If the user is greeting (e.g. 'hi', 'hello', 'hey'), asking a general question, asking for project guidance, or having a conversation without requesting code edits:
                   Respond conversationally, politely, and helpfully. DO NOT generate empty code files or template files.
                
                2. If the user is requesting modifications, bug fixes, design adjustments, or new features:
                   First write a 1-2 sentence friendly summary of what you modified.
                   Then output each modified or new file using the EXACT format:
                   FILE: relative/path/to/file.ext
                   ```[language]
                   [full complete code for the file]
                   ```
                   ENDFILE
            """.trimIndent()

            val chatResult = aiRepo.chat(
                providerId = activePid,
                systemPrompt = systemPrompt,
                userPrompt = userInstruction
            )

            if (chatResult.isSuccess) {
                val rawAiResponse = chatResult.getOrThrow()
                val parsedFiles = parseFilesFromAiResponse(rawAiResponse)
                val conversationalText = extractConversationalText(rawAiResponse, parsedFiles.isNotEmpty())

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

                    val explanationPrefix = if (conversationalText.isNotBlank()) "$conversationalText\n\n" else ""
                    val aiMsg = WorkspaceChatMessage(
                        sender = "AI",
                        message = "${explanationPrefix}✨ Updated ${parsedFiles.size} file(s) in 'my_projects/${currentRecord.appName}/':\n" +
                                parsedFiles.joinToString("\n") { "• ${it.path}" } +
                                "\n\nCheck the Preview tab to see the live updates, or the Code tab to inspect the source code."
                    )

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
                        chatMessages = _state.value.chatMessages + aiMsg,
                        files = updatedFiles,
                        directoryTree = tree,
                        selectedFile = reSelected,
                        editedContent = reSelected?.content ?: "",
                        hasUnsavedChanges = false,
                        previewHtml = updatedHtml ?: _state.value.previewHtml,
                        notification = "Applied updates to project files!"
                    )
                } else {
                    // Conversational response without file updates
                    val cleanReply = conversationalText.ifBlank { rawAiResponse.trim() }
                    val aiMsg = WorkspaceChatMessage(
                        sender = "AI",
                        message = cleanReply.ifBlank { "Hello! How can I assist you with '${currentRecord.appName}' today?" }
                    )
                    _state.value = _state.value.copy(
                        isAiRefining = false,
                        chatMessages = _state.value.chatMessages + aiMsg
                    )
                }
            } else {
                val errorMsg = chatResult.exceptionOrNull()?.localizedMessage ?: "Unknown AI error"
                val aiMsg = WorkspaceChatMessage(
                    sender = "AI",
                    message = "Could not contact AI provider: $errorMsg. Please check your API key in Settings → AI & Models."
                )
                _state.value = _state.value.copy(
                    isAiRefining = false,
                    chatMessages = _state.value.chatMessages + aiMsg,
                    notification = "AI request failed."
                )
            }
        }
    }

    private fun parseFilesFromAiResponse(raw: String): List<SourceFile> {
        val parsedFiles = mutableListOf<SourceFile>()
        val primaryRegex = Regex("""FILE:\s*([^\r\n]+)\s*\r?\n```[a-z0-9_-]*\r?\n([\s\S]*?)```(?:\s*\r?\nENDFILE)?""", RegexOption.IGNORE_CASE)
        for (match in primaryRegex.findAll(raw)) {
            val path = match.groupValues[1].trim()
            val content = match.groupValues[2]
            if (path.isNotBlank() && content.isNotBlank()) {
                parsedFiles.add(SourceFile(path, content))
            }
        }
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
        return parsedFiles
    }

    private fun extractConversationalText(raw: String, hasCodeFiles: Boolean): String {
        if (!hasCodeFiles) {
            return raw.trim()
        }
        var cleaned = raw
        val fileBlockRegex = Regex("""FILE:\s*[^\r\n]+\s*\r?\n```[a-z0-9_-]*\r?\n[\s\S]*?```(?:\s*\r?\nENDFILE)?""", RegexOption.IGNORE_CASE)
        cleaned = fileBlockRegex.replace(cleaned, "")
        val secondaryRegex = Regex("""(?:###\s*|\*\*File:?\*\*\s*|File:\s*)[a-zA-Z0-9_./\\-]+\.[a-zA-Z0-9]+\s*\r?\n```[a-z0-9_-]*\r?\n[\s\S]*?```""", RegexOption.IGNORE_CASE)
        cleaned = secondaryRegex.replace(cleaned, "")
        return cleaned.trim()
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
}
