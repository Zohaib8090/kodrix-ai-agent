package com.example.data.services

import com.example.data.ai.AiProviderFactory
import com.example.data.ai.ChatTurn
import com.example.data.local.PreferenceStorage
import com.example.data.model.AppContext
import com.example.data.model.AuthStyle
import com.example.data.model.CodeArtifact
import com.example.data.model.ProviderConfig
import com.example.data.model.SourceFile
import com.example.data.model.VerifyResult

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

    suspend fun chat(
        providerId: String,
        systemPrompt: String,
        userPrompt: String
    ): Result<String> = chat(providerId, systemPrompt, listOf(ChatTurn.user(userPrompt)))

    /** Multi-turn chat; [history] is oldest first and ends with the newest user message. */
    suspend fun chat(
        providerId: String,
        systemPrompt: String,
        history: List<ChatTurn>
    ): Result<String> {
        // Prefer the explicitly selected provider if it can actually be called
        val selected = getProviderConfig(providerId)
        val usableConfig = when {
            selected != null && isUsable(selected) -> selected
            else -> getUsableProviders().firstOrNull()
                ?: preferenceStorage.getProviders().firstOrNull()
                ?: return Result.failure(Exception("No AI provider configured. Please add an API key in Settings → AI & Models."))
        }

        val provider = AiProviderFactory.create(usableConfig)
        return provider.chat(systemPrompt, history)
    }

    fun getUsableProviders(): List<ProviderConfig> {
        return preferenceStorage.getProviders().filter { isUsable(it) }
    }

    /** Enabled, and either keyless (local servers such as Ollama) or holding a real API key. */
    private fun isUsable(config: ProviderConfig): Boolean =
        config.isEnabled && (config.authStyle == AuthStyle.NONE ||
            (config.apiKey.isNotBlank() && !PreferenceStorage.isPlaceholderApiKey(config.apiKey)))
}
