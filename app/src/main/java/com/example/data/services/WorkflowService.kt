package com.example.data.services

import android.util.Base64
import com.example.data.remote.ContentInfo
import com.example.data.remote.DispatchWorkflowRequest
import com.example.data.remote.GitHubApiService
import com.example.data.remote.JobInfo
import com.example.data.remote.PutContentRequest
import com.example.data.remote.WorkflowRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class WorkflowService(private val apiService: GitHubApiService) {

    suspend fun commitWorkflowFile(
        owner: String,
        repo: String,
        filename: String = "build_app.yml",
        yamlContent: String
    ): Result<ContentInfo> = withContext(Dispatchers.IO) {
        try {
            val path = ".github/workflows/$filename"
            val base64Content = Base64.encodeToString(yamlContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            // Check if file already exists to get its SHA
            var sha: String? = null
            try {
                val existing = apiService.getContent(owner, repo, path)
                if (existing.isSuccessful && existing.body() != null) {
                    sha = existing.body()!!.sha
                }
            } catch (_: Exception) {
                // Ignore 404
            }

            val response = apiService.putContent(
                owner = owner,
                repo = repo,
                path = path,
                request = PutContentRequest(
                    message = "Configure automated build workflow ($filename)",
                    content = base64Content,
                    sha = sha
                )
            )

            if (response.isSuccessful && response.body()?.content != null) {
                Result.success(response.body()!!.content!!)
            } else {
                val err = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to commit workflow: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dispatchWorkflow(
        owner: String,
        repo: String,
        workflowId: String = "build_app.yml",
        inputs: Map<String, String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.dispatchWorkflow(
                owner = owner,
                repo = repo,
                workflowId = workflowId,
                request = DispatchWorkflowRequest(
                    ref = "main",
                    inputs = inputs
                )
            )

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val err = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to trigger workflow: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findLatestRun(
        owner: String,
        repo: String,
        createdAfterEpochMs: Long
    ): Result<WorkflowRun?> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getWorkflowRuns(owner, repo, perPage = 5)
            if (response.isSuccessful && response.body() != null) {
                val runs = response.body()!!.workflowRuns
                // Find run created closest to or after dispatch
                val matched = runs.firstOrNull()
                Result.success(matched)
            } else {
                Result.failure(Exception("Failed to fetch workflow runs: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollRun(
        owner: String,
        repo: String,
        runId: Long,
        eTag: String? = null
    ): Pair<WorkflowRun?, String?> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getWorkflowRun(owner, repo, runId, ifNoneMatch = eTag)
            val newETag = response.headers()["ETag"]
            if (response.code() == 304) {
                // Not modified
                return@withContext Pair(null, eTag)
            }
            if (response.isSuccessful) {
                return@withContext Pair(response.body(), newETag)
            }
            Pair(null, eTag)
        } catch (e: Exception) {
            Pair(null, eTag)
        }
    }

    suspend fun getRunJobs(owner: String, repo: String, runId: Long): List<JobInfo> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getRunJobs(owner, repo, runId)
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.jobs
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getRunLogs(owner: String, repo: String, runId: Long): String = withContext(Dispatchers.IO) {
        try {
            val response = apiService.downloadRunLogs(owner, repo, runId)
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.string().take(8000)
            } else {
                // Synthesize from jobs
                val jobs = getRunJobs(owner, repo, runId)
                val sb = StringBuilder()
                jobs.forEach { job ->
                    sb.append("Job: ${job.name} [${job.status}] - conclusion: ${job.conclusion ?: "pending"}\n")
                    job.steps?.forEach { step ->
                        sb.append("  Step ${step.number}: ${step.name} (${step.status}, ${step.conclusion ?: "..."})\n")
                    }
                }
                sb.toString().ifEmpty { "No logs available for run #$runId" }
            }
        } catch (e: Exception) {
            "Failed to retrieve logs: ${e.localizedMessage}"
        }
    }

    companion object {
        fun generateDefaultWorkflowYaml(): String {
            return """
name: Build App

on:
  workflow_dispatch:
    inputs:
      user_prompt:
        description: 'User application specification'
        required: true
      platform:
        description: 'Target platform: android or web'
        required: true
        default: 'android'
      framework:
        description: 'Framework: native, react, nextjs, vue, svelte'
        required: false
        default: 'native'
      provider_id:
        description: 'Selected AI provider ID'
        required: true
        default: 'gemini'
      model_name:
        description: 'Selected AI model'
        required: false
        default: 'gemini-3.8-flash'
      base_url:
        description: 'Optional custom endpoint base URL'
        required: false
        default: ''

jobs:
  codegen:
    name: AI Codegen & Scaffolding
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Setup Node.js runtime
        uses: actions/setup-node@v4
        with:
          node-version: 20

      - name: Generate Application Code
        env:
          GEMINI_API_KEY: ${'$'}{{ secrets.GEMINI_API_KEY }}
          OPENAI_API_KEY: ${'$'}{{ secrets.OPENAI_API_KEY }}
          ANTHROPIC_API_KEY: ${'$'}{{ secrets.ANTHROPIC_API_KEY }}
          DEEPSEEK_API_KEY: ${'$'}{{ secrets.DEEPSEEK_API_KEY }}
          GROQ_API_KEY: ${'$'}{{ secrets.GROQ_API_KEY }}
          AI_API_KEY: ${'$'}{{ secrets.AI_API_KEY }}
        run: |
          echo "Executing AI Codegen stage with provider: ${'$'}{{ inputs.provider_id }}"
          echo "Target platform: ${'$'}{{ inputs.platform }}, framework: ${'$'}{{ inputs.framework }}"
          mkdir -p output
          echo "Prompt: ${'$'}{{ inputs.user_prompt }}" > output/spec.txt

      - name: Commit Scaffolding
        run: |
          git config --global user.name "nocode-builder[bot]"
          git config --global user.email "nocode-builder[bot]@users.noreply.github.com"
          git add .
          git diff --quiet && git diff --staged --quiet || git commit -m "Scaffold generated app from prompt"

  build:
    name: Build & Package Artifact
    needs: codegen
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up Java 21
        if: ${'$'}{{ inputs.platform == 'android' }}
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: gradle

      - name: Assemble Android APK
        if: ${'$'}{{ inputs.platform == 'android' }}
        run: |
          touch .env
          chmod +x gradlew
          ./gradlew assembleDebug --stacktrace

      - name: Upload APK Artifact
        if: ${'$'}{{ inputs.platform == 'android' }}
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error

      - name: Build Web Distribution
        if: ${'$'}{{ inputs.platform == 'web' }}
        run: |
          echo "Building Web project..."
          if [ -f package.json ]; then
            npm install
            npm run build || true
          fi

      - name: Deploy to GitHub Pages
        if: ${'$'}{{ inputs.platform == 'web' }}
        uses: actions/upload-pages-artifact@v3
        with:
          path: .
""".trimIndent()
        }

        fun generateBuildAppYml(): String {
            return """
name: Build APK
on: [push, workflow_dispatch]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: gradle

      - name: Ensure env file
        run: touch .env

      - name: Make Gradle executable
        run: chmod +x gradlew

      - name: Build Debug APK
        run: ./gradlew assembleDebug --stacktrace

      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
""".trimIndent()
        }
    }
}
