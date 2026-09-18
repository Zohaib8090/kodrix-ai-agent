package com.example.data.services

import com.example.data.crypto.SodiumCrypto
import com.example.data.remote.CreateOrUpdateSecretRequest
import com.example.data.remote.GitHubApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecretsService(private val apiService: GitHubApiService) {

    suspend fun pushSecret(
        owner: String,
        repo: String,
        secretName: String,
        plaintextValue: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (plaintextValue.isBlank()) {
                return@withContext Result.success(Unit)
            }

            // 1. Get repository public key for Actions secrets
            val pubKeyResponse = apiService.getPublicKey(owner, repo)
            if (!pubKeyResponse.isSuccessful || pubKeyResponse.body() == null) {
                val err = pubKeyResponse.errorBody()?.string() ?: "HTTP ${pubKeyResponse.code()}"
                return@withContext Result.failure(Exception("Failed to fetch repository public key: $err"))
            }

            val pubKey = pubKeyResponse.body()!!

            // 2. Encrypt using Libsodium sealed box
            val encryptedBase64 = SodiumCrypto.sealSecret(plaintextValue, pubKey.key)

            // 3. PUT secret to GitHub
            val putResponse = apiService.putSecret(
                owner = owner,
                repo = repo,
                secretName = secretName,
                request = CreateOrUpdateSecretRequest(
                    encryptedValue = encryptedBase64,
                    keyId = pubKey.keyId
                )
            )

            if (putResponse.isSuccessful) {
                Result.success(Unit)
            } else {
                val err = putResponse.errorBody()?.string() ?: "HTTP ${putResponse.code()}"
                Result.failure(Exception("Failed to store secret $secretName: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
