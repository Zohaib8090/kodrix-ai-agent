package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface GitHubApiService {

    @GET("user")
    suspend fun getUser(): Response<GitHubUser>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubRepo>

    @POST("user/repos")
    suspend fun createRepo(
        @Body request: CreateRepoRequest
    ): Response<GitHubRepo>

    @GET("repos/{owner}/{repo}/actions/secrets/public-key")
    suspend fun getPublicKey(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<RepoPublicKey>

    @PUT("repos/{owner}/{repo}/actions/secrets/{secret_name}")
    suspend fun putSecret(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("secret_name") secretName: String,
        @Body request: CreateOrUpdateSecretRequest
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String = "main"
    ): Response<ContentInfo>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun putContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: PutContentRequest
    ): Response<ContentResponse>

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String,
        @Body request: DispatchWorkflowRequest
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("event") event: String? = "workflow_dispatch",
        @Query("per_page") perPage: Int = 10,
        @Header("If-None-Match") ifNoneMatch: String? = null
    ): Response<WorkflowRunsResponse>

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}")
    suspend fun getWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
        @Header("If-None-Match") ifNoneMatch: String? = null
    ): Response<WorkflowRun>

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun getRunJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<JobsResponse>

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun getRunArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<ArtifactsResponse>

    @Streaming
    @GET("repos/{owner}/{repo}/actions/artifacts/{artifact_id}/zip")
    suspend fun downloadArtifactZip(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("artifact_id") artifactId: Long
    ): Response<ResponseBody>

    @Streaming
    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/logs")
    suspend fun downloadRunLogs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<ResponseBody>
}
