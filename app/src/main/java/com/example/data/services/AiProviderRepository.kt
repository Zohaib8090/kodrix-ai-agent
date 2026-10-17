package com.example.data.services

import com.example.data.ai.AiProvider
import com.example.data.ai.AiProviderFactory
import com.example.data.local.PreferenceStorage
import com.example.data.model.AppContext
import com.example.data.model.CodeArtifact
import com.example.data.model.ProviderConfig
import com.example.data.model.SourceFile
import com.example.data.model.VerifyResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

class AiProviderRepository(
    private val preferenceStorage: PreferenceStorage
) {

    fun getProviders(): List<ProviderConfig> {
        return preferenceStorage.getProviders()
    }

    fun getProviderConfig(providerId: String): ProviderConfig? {
        return preferenceStorage.getProviders().firstOrNull { it.id == providerId }
    }

    fun saveProviderConfig(config: ProviderConfig) {
        preferenceStorage.updateProvider(config)
    }

    fun removeProvider(providerId: String) {
        preferenceStorage.removeProvider(providerId)
    }

    suspend fun verifyProvider(config: ProviderConfig): VerifyResult {
        val provider = AiProviderFactory.create(config)
        val result = provider.verify()

        // Update stored config with result & timestamp
        val updated = config.copy(
            isValid = result.isValid,
            statusMessage = result.displayMessage,
            lastVerifiedAt = System.currentTimeMillis()
        )
        preferenceStorage.updateProvider(updated)
        return result
    }

    suspend fun fetchModels(providerId: String): Result<List<String>> {
        val config = getProviderConfig(providerId)
            ?: return Result.failure(Exception("Provider not found"))
        val provider = AiProviderFactory.create(config)
        val result = provider.fetchAvailableModels()
        if (result.isSuccess) {
            val models = result.getOrThrow()
            val updated = config.copy(availableModels = models)
            preferenceStorage.updateProvider(updated)
        }
        return result
    }

    suspend fun generateCode(
        providerId: String,
        prompt: String,
        context: AppContext
    ): Result<CodeArtifact> {
        val config = getProviderConfig(providerId)
            ?: preferenceStorage.getProviders().firstOrNull { it.isValid }
            ?: preferenceStorage.getProviders().first()

        val provider = AiProviderFactory.create(config)
        return provider.generateCode(prompt, context)
    }

    suspend fun fixError(
        providerId: String,
        log: String,
        sourceFiles: List<SourceFile>,
        screenshot: ByteArray? = null
    ): Result<CodeArtifact> {
        val config = getProviderConfig(providerId)
            ?: preferenceStorage.getProviders().firstOrNull { it.isValid }
            ?: preferenceStorage.getProviders().first()

        val provider = AiProviderFactory.create(config)

        // Capability constraint: only pass screenshot if provider supports vision
        val effectiveScreenshot = if (provider.supportsVision) screenshot else null
        return provider.fixError(log, sourceFiles, effectiveScreenshot)
    }

    private fun resolveUsableConfig(providerId: String): ProviderConfig? {
        // Prefer the explicitly selected provider if it has a real API key
        val selected = getProviderConfig(providerId)
        return when {
            selected != null && selected.apiKey.isNotBlank() && selected.apiKey != "dummy_api_key" -> selected
            else -> preferenceStorage.getProviders()
                .firstOrNull { it.isEnabled && it.apiKey.isNotBlank() && it.apiKey != "dummy_api_key" }
                ?: preferenceStorage.getProviders().firstOrNull()
        }
    }

    private val noProviderMessage = "No AI provider configured. Please add an API key in Settings → AI & Models."

    suspend fun chat(
        providerId: String,
        systemPrompt: String,
        userPrompt: String
    ): Result<String> {
        val usableConfig = resolveUsableConfig(providerId)
            ?: return Result.failure(Exception(noProviderMessage))
        val provider = AiProviderFactory.create(usableConfig)
        return provider.chat(systemPrompt, userPrompt)
    }

    /** Streams the reply as text deltas; failures are thrown to the collector. */
    fun chatStream(
        providerId: String,
        systemPrompt: String,
        userPrompt: String
    ): Flow<String> {
        val usableConfig = resolveUsableConfig(providerId)
            ?: return flow { throw IOException(noProviderMessage) }
        return AiProviderFactory.create(usableConfig).chatStream(systemPrompt, userPrompt)
    }

    fun getUsableProviders(): List<ProviderConfig> {
        return preferenceStorage.getProviders()
            .filter { it.isEnabled && it.apiKey.isNotBlank() && it.apiKey != "dummy_api_key" }
    }
}
