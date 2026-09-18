package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUser(
    val login: String,
    val id: Long,
    val name: String?,
    @Json(name = "avatar_url") val avatarUrl: String?,
    @Json(name = "public_repos") val publicRepos: Int = 0,
    @Json(name = "total_private_repos") val totalPrivateRepos: Int = 0
)

@JsonClass(generateAdapter = true)
data class GitHubRepo(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val private: Boolean,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "html_url") val htmlUrl: String
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequest(
    val name: String,
    val description: String = "Auto-generated repository by No-Code App Builder",
    val private: Boolean = true,
    @Json(name = "auto_init") val autoInit: Boolean = true
)

@JsonClass(generateAdapter = true)
data class RepoPublicKey(
    @Json(name = "key_id") val keyId: String,
    val key: String
)

@JsonClass(generateAdapter = true)
data class CreateOrUpdateSecretRequest(
    @Json(name = "encrypted_value") val encryptedValue: String,
    @Json(name = "key_id") val keyId: String
)

@JsonClass(generateAdapter = true)
data class PutContentRequest(
    val message: String,
    val content: String, // Base64 encoded
    val branch: String = "main",
    val sha: String? = null
)

@JsonClass(generateAdapter = true)
data class ContentResponse(
    val content: ContentInfo?
)

@JsonClass(generateAdapter = true)
data class ContentInfo(
    val sha: String,
    val name: String,
    val path: String
)

@JsonClass(generateAdapter = true)
data class DispatchWorkflowRequest(
    val ref: String = "main",
    val inputs: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class WorkflowRunsResponse(
    @Json(name = "total_count") val totalCount: Int,
    @Json(name = "workflow_runs") val workflowRuns: List<WorkflowRun>
)

@JsonClass(generateAdapter = true)
data class WorkflowRun(
    val id: Long,
    val name: String?,
    @Json(name = "head_branch") val headBranch: String?,
    val status: String, // queued, in_progress, completed
    val conclusion: String?, // success, failure, cancelled, timed_out
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "updated_at") val updatedAt: String?,
    @Json(name = "html_url") val htmlUrl: String?
)

@JsonClass(generateAdapter = true)
data class JobsResponse(
    @Json(name = "total_count") val totalCount: Int,
    val jobs: List<JobInfo>
)

@JsonClass(generateAdapter = true)
data class JobInfo(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val steps: List<JobStep>?
)

@JsonClass(generateAdapter = true)
data class JobStep(
    val name: String,
    val status: String,
    val conclusion: String?,
    val number: Int
)

@JsonClass(generateAdapter = true)
data class ArtifactsResponse(
    @Json(name = "total_count") val totalCount: Int,
    val artifacts: List<ArtifactInfo>
)

@JsonClass(generateAdapter = true)
data class ArtifactInfo(
    val id: Long,
    val name: String,
    @Json(name = "size_in_bytes") val sizeInBytes: Long,
    @Json(name = "archive_download_url") val archiveDownloadUrl: String,
    val expired: Boolean
)
