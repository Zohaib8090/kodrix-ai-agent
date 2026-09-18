package com.example.data.services

import com.example.data.remote.ApiClient
import com.example.data.remote.DeviceCodeResponse
import com.example.data.remote.GitHubOAuthApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

class GitHubOAuthService(
    private val api: GitHubOAuthApi = ApiClient.createGitHubOAuthApi()
) {

    suspend fun requestDeviceCode(
        clientId: String,
        scope: String = "repo,workflow,read:org"
    ): Result<DeviceCodeResponse> {
        return try {
            val trimmedId = clientId.trim()
            if (trimmedId.isBlank()) {
                return Result.failure(IllegalArgumentException("GitHub Client ID cannot be empty"))
            }

            val response = api.requestDeviceCode(clientId = trimmedId, scope = scope)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val rawError = response.errorBody()?.string() ?: ""
                val errorMsg = parseErrorMessage(rawError, "Failed to initiate GitHub device flow (${response.code()})")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollForAccessToken(
        clientId: String,
        deviceCode: String,
        initialIntervalSeconds: Int = 5,
        expiresInSeconds: Int = 900,
        onProgress: (String) -> Unit = {}
    ): Result<String> {
        var intervalMs = (initialIntervalSeconds.coerceAtLeast(3)) * 1000L
        val startTime = System.currentTimeMillis()
        val maxDurationMs = (expiresInSeconds.coerceAtLeast(60)) * 1000L

        while (System.currentTimeMillis() - startTime < maxDurationMs) {
            delay(intervalMs)

            try {
                val response = api.pollAccessToken(
                    clientId = clientId.trim(),
                    deviceCode = deviceCode
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val token = body.accessToken
                    if (!token.isNullOrBlank()) {
                        return Result.success(token)
                    }

                    val error = body.error ?: ""
                    when (error) {
                        "authorization_pending" -> {
                            onProgress("Waiting for authorization on GitHub...")
                            // Keep polling
                        }
                        "slow_down" -> {
                            val extra = (body.interval ?: 5) * 1000L
                            intervalMs += extra
                            onProgress("Slowing down polling rate...")
                        }
                        "expired_token" -> {
                            return Result.failure(Exception("The verification code has expired. Please start again."))
                        }
                        "access_denied" -> {
                            return Result.failure(Exception("Access was denied on GitHub."))
                        }
                        else -> {
                            val desc = body.errorDescription ?: error
                            return Result.failure(Exception(desc.ifBlank { "Authorization failed" }))
                        }
                    }
                } else {
                    val rawError = response.errorBody()?.string() ?: ""
                    val errorMsg = parseErrorMessage(rawError, "Polling error (${response.code()})")
                    return Result.failure(Exception(errorMsg))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Transient network failure during poll, retry next tick
                onProgress("Connecting to GitHub...")
            }
        }

        return Result.failure(Exception("Authorization timed out. Please try again."))
    }

    private fun parseErrorMessage(raw: String, fallback: String): String {
        return try {
            val json = JSONObject(raw)
            when {
                json.has("error_description") -> json.getString("error_description")
                json.has("message") -> json.getString("message")
                json.has("error") -> json.getString("error")
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }
}
