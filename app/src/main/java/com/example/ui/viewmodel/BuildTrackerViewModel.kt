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
import com.example.data.model.BuildArtifact
import com.example.data.model.BuildPhase
import com.example.data.model.CodeArtifact
import com.example.data.model.DiffProposal
import com.example.data.model.PlatformType
import com.example.data.model.SourceFile
import com.example.data.model.WebFramework
import com.example.data.remote.ApiClient
import com.example.data.remote.WorkflowRun
import com.example.data.services.AiProviderRepository
import com.example.data.services.ArtifactService
import com.example.data.services.NodeService
import com.example.data.services.ProjectRepository
import com.example.data.services.SecretsService
import com.example.data.services.WorkflowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class TrackerUiState(
    val buildRecord: BuildRecord? = null,
    val phase: BuildPhase = BuildPhase.Idle,
    val runId: Long? = null,
    val runUrl: String? = null,
    val progress: Float = 0f,
    val logs: String = "",
    val diffProposal: DiffProposal? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val artifact: BuildArtifact? = null,
    val installError: String? = null
)

class BuildTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val db = AppDatabase.getInstance(application)
    private val prefs = PreferenceStorage(application)
    private val aiRepo = AiProviderRepository(prefs)
    private val projectRepo = ProjectRepository(application)
    private val nodeService = NodeService(application)

    private val _state = MutableStateFlow(TrackerUiState())
    val state: StateFlow<TrackerUiState> = _state.asStateFlow()

    private var activeJobRunning = false

    fun loadAndStartPipeline(buildId: String) {
        viewModelScope.launch {
            val record = db.buildRecordDao().getRecordDirect(buildId)
            if (record == null) {
                _state.value = _state.value.copy(
                    phase = BuildPhase.Failed("Build record not found for ID: $buildId")
                )
                return@launch
            }

            _state.value = _state.value.copy(
                buildRecord = record,
                retryCount = record.retryCount
            )

            if (!activeJobRunning) {
                activeJobRunning = true
                runPipeline(record)
            }
        }
    }

    private suspend fun runPipeline(record: BuildRecord) {
        val isWeb = record.platform.equals("WEB", ignoreCase = true)
        val owner = prefs.githubUsername.trim()
        val repo = prefs.githubRepo.trim()
        val token = prefs.githubToken.trim()
        val hasGithub = owner.isNotBlank() && repo.isNotBlank() && token.isNotBlank()

        try {
            // Step 1: AI Code Generation & Architecture Synthesis
            updatePhase(BuildPhase.InProgress("Synthesizing architecture & code with AI (${record.codegenProviderId.uppercase()})..."), "AI Codegen", record.id)
            val appContext = AppContext(
                appName = record.appName,
                platform = if (isWeb) PlatformType.WEB else PlatformType.ANDROID,
                framework = try { WebFramework.valueOf(record.framework) } catch (_: Exception) { WebFramework.REACT_VITE },
                features = record.featuresJson.split(",").filter { it.isNotBlank() },
                prompt = record.prompt
            )

            val codeResult = aiRepo.generateCode(record.codegenProviderId, record.prompt, appContext)
            val artifact = codeResult.getOrNull()
            val projectDir = if (artifact != null) {
                projectRepo.saveArtifactToDisk(record.appName, artifact)
            } else {
                projectRepo.getProjectDir(record.appName)
            }

            if (isWeb) {
                // Spin up local HTTP server to serve the generated website on port 5173
                nodeService.startLocalDevServer(projectDir, port = 5173)
            }

            // If GitHub is not configured:
            if (!hasGithub) {
                if (isWeb) {
                    val webArtifact = BuildArtifact.WebArtifact(
                        deployUrl = "http://localhost:5173",
                        previewHtml = artifact?.previewHtml
                    )
                    updatePhase(BuildPhase.Completed(webArtifact), "Live Preview Ready", record.id)
                    _state.value = _state.value.copy(
                        artifact = webArtifact,
                        logs = "Website generated successfully! Served on local preview server at http://localhost:5173."
                    )
                    val updatedRecord = record.copy(
                        status = "Completed",
                        currentStep = "Finished",
                        webDeployUrl = "http://localhost:5173"
                    )
                    db.buildRecordDao().update(updatedRecord)
                    _state.value = _state.value.copy(buildRecord = updatedRecord)
                    return
                } else {
                    val msg = "GitHub configuration required for compiling Android APK in the cloud. Please configure your GitHub Username, Repository, and Personal Access Token (PAT) in Settings (gear icon in the top bar)."
                    updatePhase(BuildPhase.Failed(msg), "GitHub Setup Required", record.id)
                    _state.value = _state.value.copy(logs = msg)
                    val updatedRecord = record.copy(status = "Failed", currentStep = "Setup Required", logSummary = msg)
                    db.buildRecordDao().update(updatedRecord)
                    _state.value = _state.value.copy(buildRecord = updatedRecord)
                    return
                }
            }

            val apiService = ApiClient.createGitHubService { token }
            val okHttpClient = ApiClient.createGenericOkHttpClient(20)
            val workflowService = WorkflowService(apiService)
            val secretsService = SecretsService(apiService)
            val artifactService = ArtifactService(context, apiService, okHttpClient)

            // Step 2: Committing workflow file
            updatePhase(BuildPhase.CommittingWorkflow, "Committing workflow YAML", record.id)
            delay(1000)
            val commitResult = workflowService.commitWorkflowFile(
                owner = owner,
                repo = repo,
                filename = "build_app.yml",
                yamlContent = WorkflowService.generateDefaultWorkflowYaml()
            )

            // Step 3: Push Secrets (Libsodium Encrypted)
            updatePhase(BuildPhase.PushingSecrets, "Encrypting & pushing secrets", record.id)
            delay(800)
            val codegenConfig = aiRepo.getProviderConfig(record.codegenProviderId)
            if (codegenConfig != null && codegenConfig.apiKey.isNotBlank()) {
                val secretKeyName = when (codegenConfig.id) {
                    "gemini" -> "GEMINI_API_KEY"
                    "openai" -> "OPENAI_API_KEY"
                    "anthropic" -> "ANTHROPIC_API_KEY"
                    "deepseek" -> "DEEPSEEK_API_KEY"
                    "groq" -> "GROQ_API_KEY"
                    else -> "AI_API_KEY"
                }
                secretsService.pushSecret(owner, repo, secretKeyName, codegenConfig.apiKey)
            }

            // Step 4: Dispatch Workflow
            updatePhase(BuildPhase.Dispatching, "Dispatching workflow run", record.id)
            val dispatchTime = System.currentTimeMillis()
            val dispatchResult = workflowService.dispatchWorkflow(
                owner = owner,
                repo = repo,
                workflowId = "build_app.yml",
                inputs = mapOf(
                    "user_prompt" to record.prompt,
                    "platform" to record.platform.lowercase(),
                    "framework" to record.framework.lowercase(),
                    "provider_id" to record.codegenProviderId,
                    "model_name" to (codegenConfig?.defaultModel ?: "gemini-2.5-flash"),
                    "base_url" to (codegenConfig?.baseUrl ?: "")
                )
            )

            // Step 5: Queued & Finding Run ID
            updatePhase(BuildPhase.Queued, "Waiting for runner to pick up task", record.id)
            var foundRun: WorkflowRun? = null
            var pollAttempts = 0
            while (foundRun == null && pollAttempts < 8) {
                delay(2000L)
                val latest = workflowService.findLatestRun(owner, repo, dispatchTime).getOrNull()
                if (latest != null) {
                    foundRun = latest
                }
                pollAttempts++
            }

            val runId = foundRun?.id ?: System.currentTimeMillis()
            _state.value = _state.value.copy(
                runId = runId,
                runUrl = foundRun?.htmlUrl ?: "https://github.com/$owner/$repo/actions"
            )

            // Step 6: Real polling every 12 seconds, max 40 times
            var currentStatus = foundRun?.status ?: "queued"
            var currentConclusion = foundRun?.conclusion
            var pollIteration = 0
            val maxPolls = 40

            while (currentStatus != "completed" && pollIteration < maxPolls) {
                val stepName = when (currentStatus) {
                    "queued" -> "GitHub Actions: Build Queued (waiting for runner)"
                    "in_progress" -> {
                        val jobs = workflowService.getRunJobs(owner, repo, runId)
                        val activeJob = jobs.firstOrNull { it.status == "in_progress" } ?: jobs.firstOrNull()
                        val activeStep = activeJob?.steps?.firstOrNull { it.status == "in_progress" }?.name
                            ?: activeJob?.name
                        if (activeStep != null) "In Progress: $activeStep" else "In Progress: Compiling Build Artifact"
                    }
                    else -> "Status: $currentStatus"
                }

                updatePhase(BuildPhase.InProgress(stepName), stepName, record.id)

                val jobs = workflowService.getRunJobs(owner, repo, runId)
                if (jobs.isNotEmpty()) {
                    val activeJob = jobs.firstOrNull { it.status == "in_progress" } ?: jobs.first()
                    val activeStep = activeJob.steps?.firstOrNull { it.status == "in_progress" }?.name
                        ?: activeJob.name
                    _state.value = _state.value.copy(logs = "Running step: $activeStep (Poll ${pollIteration + 1}/$maxPolls)")
                }

                delay(12000L)
                pollIteration++

                val (runData, _) = workflowService.pollRun(owner, repo, runId)
                if (runData != null) {
                    currentStatus = runData.status
                    currentConclusion = runData.conclusion
                }
            }

            // Timeout check after polling
            if (currentStatus != "completed") {
                val timeoutMsg = "Build timed out after 8 minutes (runner did not complete). Last status: $currentStatus"
                updatePhase(BuildPhase.Failed(timeoutMsg), "Failed", record.id)
                _state.value = _state.value.copy(logs = timeoutMsg)
                val updatedRecord = record.copy(status = "Failed", currentStep = "Timeout")
                db.buildRecordDao().update(updatedRecord)
                _state.value = _state.value.copy(buildRecord = updatedRecord)
                return
            }

            // Step 7: Result Handling based on actual GitHub conclusion
            if (currentConclusion == "success") {
                updatePhase(BuildPhase.InProgress("Fetching Real Artifact from GitHub"), "Delivering Artifact", record.id)
                val artifactResult = artifactService.fetchArtifact(
                    owner = owner,
                    repo = repo,
                    runId = runId,
                    token = token,
                    platform = record.platform
                )

                if (artifactResult.isSuccess) {
                    val deliveredArtifact = artifactResult.getOrThrow()
                    val finalArtifact = if (deliveredArtifact is BuildArtifact.WebArtifact && artifact?.previewHtml != null) {
                        deliveredArtifact.copy(previewHtml = artifact.previewHtml)
                    } else deliveredArtifact

                    updatePhase(BuildPhase.Completed(finalArtifact), "Completed", record.id)
                    _state.value = _state.value.copy(artifact = finalArtifact)

                    val updatedRecord = record.copy(
                        status = "Completed",
                        currentStep = "Finished",
                        runId = runId,
                        localApkPath = if (finalArtifact is BuildArtifact.ApkArtifact) finalArtifact.localPath else null,
                        webDeployUrl = if (finalArtifact is BuildArtifact.WebArtifact) finalArtifact.deployUrl else null
                    )
                    db.buildRecordDao().update(updatedRecord)
                    _state.value = _state.value.copy(buildRecord = updatedRecord)
                } else {
                    val err = artifactResult.exceptionOrNull()?.message ?: "Failed to retrieve real artifact from build run"
                    updatePhase(BuildPhase.Failed(err), "Failed", record.id)
                    _state.value = _state.value.copy(logs = err)
                    val updatedRecord = record.copy(status = "Failed", currentStep = "Artifact Missing")
                    db.buildRecordDao().update(updatedRecord)
                    _state.value = _state.value.copy(buildRecord = updatedRecord)
                }
            } else {
                // Pipeline failure detected -> invoke AI fix loop
                handleFailureAndFix(record, runId, workflowService)
            }
        } catch (e: Exception) {
            handlePipelineException(record, e)
        } finally {
            activeJobRunning = false
        }
    }

    private suspend fun handleFailureAndFix(record: BuildRecord, runId: Long, workflowService: WorkflowService) {
        val errorLogs = workflowService.getRunLogs(prefs.githubUsername, prefs.githubRepo, runId)
        val currentRetries = _state.value.retryCount

        if (currentRetries < _state.value.maxRetries) {
            val projectDir = projectRepo.getProjectDir(record.appName)
            val realFiles = projectRepo.readProjectFiles(projectDir)
            val sourceFiles = if (realFiles.isNotEmpty()) realFiles else listOf(
                SourceFile("app/build.gradle.kts", "// gradle build error"),
                SourceFile("app/src/main/java/com/app/MainActivity.kt", "// sample activity")
            )

            val fixResult = aiRepo.fixError(
                providerId = record.fixProviderId,
                log = errorLogs,
                sourceFiles = sourceFiles
            )

            if (fixResult.isSuccess) {
                val code = fixResult.getOrThrow()
                val proposal = DiffProposal(
                    summary = "AI detected compiler error in build pipeline.",
                    changedFiles = code.files,
                    explanation = code.explanation
                )
                _state.value = _state.value.copy(
                    diffProposal = proposal,
                    logs = errorLogs,
                    retryCount = currentRetries + 1
                )
                updatePhase(BuildPhase.Failed("Build failed. AI self-healing proposal generated."), "Failed", record.id)
                return
            }
        }

        updatePhase(BuildPhase.Failed("Build failed with conclusion: failure\n$errorLogs"), "Failed", record.id)
        db.buildRecordDao().update(record.copy(status = "Failed", logSummary = errorLogs))
    }

    private suspend fun handlePipelineException(record: BuildRecord, e: Exception) {
        val msg = e.localizedMessage ?: "Unknown pipeline error"
        updatePhase(BuildPhase.Failed(msg), "Failed", record.id)
        db.buildRecordDao().update(record.copy(status = "Failed", logSummary = msg))
    }

    fun applyProposalAndRetry() {
        val proposal = _state.value.diffProposal ?: return
        val currentRecord = _state.value.buildRecord ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(diffProposal = null)
            val fixedArtifact = CodeArtifact(files = proposal.changedFiles, explanation = proposal.explanation)
            projectRepo.saveArtifactToDisk(currentRecord.appName, fixedArtifact)

            val updatedRecord = currentRecord.copy(
                retryCount = _state.value.retryCount,
                status = "InProgress"
            )
            db.buildRecordDao().update(updatedRecord)
            runPipeline(updatedRecord)
        }
    }

    fun dismissDiffDialog() {
        _state.value = _state.value.copy(diffProposal = null)
    }

    fun retryBuild() {
        val currentRecord = _state.value.buildRecord ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(diffProposal = null)
            runPipeline(currentRecord)
        }
    }

    private suspend fun updatePhase(phase: BuildPhase, step: String, buildId: String) {
        _state.value = _state.value.copy(phase = phase)
        db.buildRecordDao().getRecordDirect(buildId)?.let {
            db.buildRecordDao().update(it.copy(currentStep = step))
        }
    }

    fun installApk(apkPath: String) {
        try {
            val apkFile = File(apkPath)
            if (!apkFile.exists()) {
                _state.value = _state.value.copy(installError = "APK file does not exist at $apkPath")
                return
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _state.value = _state.value.copy(installError = "Install error: ${e.localizedMessage}")
        }
    }

    fun openWebUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _state.value = _state.value.copy(installError = "Failed to launch URL: ${e.localizedMessage}")
        }
    }
}
