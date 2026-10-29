package com.example.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.AgentFactory
import com.example.agent.AgentSetupException
import com.example.agent.BuiltAgent
import com.example.agent.ChatMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class ChatLine(val fromUser: Boolean, val text: String, val isError: Boolean = false)

data class PendingApproval(val tool: String, val args: String, val answer: CompletableDeferred<Boolean>)

/** Plain chat with the agent. Tool use (terminal, memory, MCP) happens silently in the background. */
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val lines = mutableStateListOf<ChatLine>()
    var thinking by mutableStateOf(false); private set
    var pending by mutableStateOf<PendingApproval?>(null); private set

    private var agent: BuiltAgent? = null
    private val history = mutableListOf<ChatMessage>()
    private var job: Job? = null

    var lastViaVoice by mutableStateOf(false); private set

    fun send(text: String, viaVoice: Boolean = false) {
        val t = text.trim()
        if (t.isEmpty() || thinking) return
        lastViaVoice = viaVoice
        lines += ChatLine(true, t)
        thinking = true
        job = viewModelScope.launch {
            try {
                val built = agent ?: AgentFactory.build(
                    getApplication(),
                    confirm = { tool, args ->
                        val d = CompletableDeferred<Boolean>()
                        pending = PendingApproval(tool, args, d)
                        val ok = d.await()
                        pending = null
                        ok
                    },
                    onEvent = {},
                    quietShell = true,
                    extraPrompt = "\n- You are in a text chat. Do terminal and tool work silently; reply with the result in plain language and do not describe commands or tool names unless the user asks. You may use Markdown sparingly.",
                ).also { agent = it }
                val result = built.loop.run(t, history.toList())
                history.clear()
                history.addAll(result.messages.filter { it.role != "system" }.takeLast(30))
                lines += ChatLine(false, result.text, isError = !result.ok)
            } catch (e: AgentSetupException) {
                lines += ChatLine(false, e.message.orEmpty(), isError = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lines += ChatLine(false, "Something went wrong: ${e.message}", isError = true)
            } finally {
                thinking = false
            }
        }
    }

    fun answerApproval(ok: Boolean) { pending?.answer?.complete(ok) }

    fun newChat() {
        job?.cancel(); pending?.answer?.complete(false)
        lines.clear(); history.clear(); agent = null; thinking = false
    }

    /** Used when the agent settings changed (new key/model). */
    fun resetAgent() { agent = null }
}
