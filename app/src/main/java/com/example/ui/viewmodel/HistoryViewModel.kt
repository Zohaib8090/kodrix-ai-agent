package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BuildRecord
import com.example.data.local.PreferenceStorage
import com.example.data.services.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val prefs = PreferenceStorage(application)
    private val projectRepo = ProjectRepository(application)

    val historyRecords: StateFlow<List<BuildRecord>> = db.buildRecordDao()
        .getAllRecords()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processMessage = MutableStateFlow<String?>(null)
    val processMessage: StateFlow<String?> = _processMessage.asStateFlow()

    private val _processError = MutableStateFlow<String?>(null)
    val processError: StateFlow<String?> = _processError.asStateFlow()

    fun clearProcessError() {
        _processError.value = null
    }

    fun renameProject(record: BuildRecord, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == record.appName) return
        viewModelScope.launch {
            projectRepo.renameProject(record.appName, trimmed)
            val updated = record.copy(appName = trimmed)
            db.buildRecordDao().update(updated)
        }
    }

    fun deleteRecord(record: BuildRecord) {
        viewModelScope.launch {
            projectRepo.deleteProject(record.appName)
            db.buildRecordDao().deleteById(record.id)
        }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            val record = db.buildRecordDao().getRecordDirect(id)
            if (record != null) {
                projectRepo.deleteProject(record.appName)
            }
            db.buildRecordDao().deleteById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            db.buildRecordDao().clearAll()
        }
    }

    fun importZipProject(uri: Uri, context: Context, customProjectName: String? = null, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processMessage.value = "Extracting and importing project ZIP..."
            _processError.value = null

            try {
                var extractedName = customProjectName?.trim()?.ifEmpty { null }
                if (extractedName == null) {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
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
}
