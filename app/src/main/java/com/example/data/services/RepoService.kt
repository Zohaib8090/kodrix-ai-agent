package com.example.data.services

import com.example.data.remote.CreateRepoRequest
import com.example.data.remote.GitHubApiService
import com.example.data.remote.GitHubRepo
import com.example.data.remote.GitHubUser
import com.example.data.remote.RepoPublicKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RepoService(private val apiService: GitHubApiService) {

    suspend fun verifyUser(): Result<GitHubUser> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getUser()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val err = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("GitHub verification failed: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ensureRepoExists(owner: String, repo: String): Result<GitHubRepo> = withContext(Dispatchers.IO) {
        try {
            val checkResponse = apiService.getRepo(owner, repo)
            if (checkResponse.isSuccessful && checkResponse.body() != null) {
                return@withContext Result.success(checkResponse.body()!!)
            }

            if (checkResponse.code() == 404) {
                // Create repository
                val createResponse = apiService.createRepo(
                    CreateRepoRequest(
                        name = repo,
                        description = "Created by No-Code App Builder for automated Android & Web builds",
                        private = true,
                        autoInit = true
                    )
                )
                if (createResponse.isSuccessful && createResponse.body() != null) {
                    return@withContext Result.success(createResponse.body()!!)
                } else {
                    val err = createResponse.errorBody()?.string() ?: "HTTP ${createResponse.code()}"
                    return@withContext Result.failure(Exception("Failed to create repository: $err"))
                }
            }

            val err = checkResponse.errorBody()?.string() ?: "HTTP ${checkResponse.code()}"
            Result.failure(Exception("Failed to check repository: $err"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRepoPublicKey(owner: String, repo: String): Result<RepoPublicKey> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPublicKey(owner, repo)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val err = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception("Failed to retrieve public key: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
