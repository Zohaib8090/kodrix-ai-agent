package com.example.data.services

import android.content.Context
import com.example.data.model.BuildArtifact
import com.example.data.model.DeploymentTarget
import com.example.data.remote.GitHubApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class ArtifactService(
    private val context: Context,
    private val apiService: GitHubApiService,
    private val okHttpClient: OkHttpClient
) {

    suspend fun fetchArtifact(
        owner: String,
        repo: String,
        runId: Long,
        token: String,
        platform: String,
        deploymentTarget: DeploymentTarget = DeploymentTarget.GITHUB_PAGES,
        onProgress: (Float) -> Unit = {}
    ): Result<BuildArtifact> = withContext(Dispatchers.IO) {
        if (platform.equals("WEB", ignoreCase = true)) {
            val deployUrl = resolveWebDeployUrl(owner, repo, deploymentTarget)
            return@withContext Result.success(BuildArtifact.WebArtifact(deployUrl))
        }

        // Android platform: download ZIP and extract APK
        try {
            onProgress(0.1f)
            val artifactsResp = apiService.getRunArtifacts(owner, repo, runId)
            if (!artifactsResp.isSuccessful || artifactsResp.body() == null) {
                return@withContext Result.failure(Exception("Failed to list run artifacts: ${artifactsResp.code()}"))
            }

            val artifacts = artifactsResp.body()!!.artifacts
            val targetArtifact = artifacts.firstOrNull { it.name.contains("apk", ignoreCase = true) || it.name.contains("debug", ignoreCase = true) }
                ?: artifacts.firstOrNull()

            if (targetArtifact == null) {
                return@withContext Result.failure(Exception("No artifact found in GitHub Actions run"))
            }

            val artifactsDir = File(context.filesDir, "artifacts").apply { mkdirs() }
            val destinationApk = File(artifactsDir, "${repo}-debug.apk")

            // Download real artifact zip from GitHub API
            val request = Request.Builder()
                .url(targetArtifact.archiveDownloadUrl)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()

            onProgress(0.3f)
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                return@withContext Result.failure(Exception("Failed to download artifact from GitHub: HTTP ${response.code}"))
            }

            val body = response.body!!
            val contentLength = body.contentLength()
            val tempZip = File(context.cacheDir, "artifact_$runId.zip")

            body.byteStream().use { input ->
                FileOutputStream(tempZip).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            onProgress(0.3f + 0.5f * (totalRead.toFloat() / contentLength))
                        }
                    }
                }
            }

            // Unzip to find real APK
            var foundApk = false
            ZipInputStream(tempZip.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                        FileOutputStream(destinationApk).use { fos ->
                            zip.copyTo(fos)
                        }
                        foundApk = true
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            tempZip.delete()

            if (!foundApk) {
                return@withContext Result.failure(Exception("No APK file found inside the downloaded artifact ZIP"))
            }

            onProgress(1.0f)
            Result.success(BuildArtifact.ApkArtifact(destinationApk.absolutePath))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveWebDeployUrl(owner: String, repo: String, target: DeploymentTarget): String {
        return when (target) {
            DeploymentTarget.GITHUB_PAGES -> "https://${owner.lowercase()}.github.io/${repo.lowercase()}/"
            DeploymentTarget.VERCEL -> "https://${repo.lowercase()}-${owner.lowercase()}.vercel.app/"
            DeploymentTarget.NETLIFY -> "https://${repo.lowercase()}.netlify.app/"
        }
    }
}
