package com.example.data.ai

import com.example.data.model.AppContext
import com.example.data.model.CodeArtifact
import com.example.data.model.SourceFile
import com.example.data.model.VerifyResult

/** One message of a multi-turn conversation sent to a provider. */
data class ChatTurn(val role: Role, val content: String) {
    enum class Role { USER, ASSISTANT }

    companion object {
        fun user(content: String) = ChatTurn(Role.USER, content)
        fun assistant(content: String) = ChatTurn(Role.ASSISTANT, content)

        /**
         * Drops blank turns and leading assistant turns, and merges consecutive turns from the same
         * role, so the result always starts with the user and alternates (Gemini and Claude expect this).
         */
        fun normalize(turns: List<ChatTurn>): List<ChatTurn> {
            val result = mutableListOf<ChatTurn>()
            for (turn in turns) {
                if (turn.content.isBlank()) continue
                if (result.isEmpty() && turn.role == Role.ASSISTANT) continue
                val last = result.lastOrNull()
                if (last != null && last.role == turn.role) {
                    result[result.lastIndex] = last.copy(content = last.content + "\n\n" + turn.content)
                } else {
                    result.add(turn)
                }
            }
            return result
        }

        /**
         * The newest turns that fit in [maxTurns] and [maxChars] (the latest turn is always kept),
         * normalized for sending.
         */
        fun recent(turns: List<ChatTurn>, maxTurns: Int, maxChars: Int): List<ChatTurn> {
            val kept = ArrayDeque<ChatTurn>()
            var used = 0
            for (turn in turns.asReversed()) {
                if (kept.size >= maxTurns) break
                if (kept.isNotEmpty() && used + turn.content.length > maxChars) break
                kept.addFirst(turn)
                used += turn.content.length
            }
            return normalize(kept.toList())
        }
    }
}

interface AiProvider {
    val providerId: String
    val name: String
    val supportsVision: Boolean

    suspend fun generateCode(prompt: String, context: AppContext): Result<CodeArtifact>
    suspend fun fixError(log: String, sourceFiles: List<SourceFile>, screenshot: ByteArray? = null): Result<CodeArtifact>
    suspend fun chat(systemPrompt: String, userPrompt: String): Result<String>
    /** Multi-turn chat; [history] ends with the newest user message. */
    suspend fun chat(systemPrompt: String, history: List<ChatTurn>): Result<String>
    suspend fun verify(): VerifyResult
    suspend fun fetchAvailableModels(): Result<List<String>>
}
