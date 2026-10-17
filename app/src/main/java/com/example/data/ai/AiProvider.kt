package com.example.data.ai

import com.example.data.model.AppContext
import com.example.data.model.CodeArtifact
import com.example.data.model.SourceFile
import com.example.data.model.VerifyResult
import kotlinx.coroutines.flow.Flow

interface AiProvider {
    val providerId: String
    val name: String
    val supportsVision: Boolean

    suspend fun generateCode(prompt: String, context: AppContext): Result<CodeArtifact>
    suspend fun fixError(log: String, sourceFiles: List<SourceFile>, screenshot: ByteArray? = null): Result<CodeArtifact>
    suspend fun chat(systemPrompt: String, userPrompt: String): Result<String>
    /** Emits the reply incrementally as text deltas. Collection errors surface as flow exceptions. */
    fun chatStream(systemPrompt: String, userPrompt: String): Flow<String>
    suspend fun verify(): VerifyResult
    suspend fun fetchAvailableModels(): Result<List<String>>
}
