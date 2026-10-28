package com.example.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.agent.AgentConfig
import com.example.agent.AgentEvent
import com.example.agent.AgentFactory
import com.example.agent.AgentSetupException
import com.example.agent.BuiltAgent
import com.example.agent.ChatMessage
import com.example.voice.AndroidSpeechToText
import com.example.voice.SttEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

enum class AssistStatus { Idle, Listening, Thinking, Done, Error }

data class PendingConfirm(val tool: String, val args: String, val answer: CompletableDeferred<Boolean>)

data class AssistUi(
    val status: AssistStatus = AssistStatus.Idle,
    val transcript: String = "",
    val answer: String = "",
    val steps: List<String> = emptyList(),
    val pending: PendingConfirm? = null,
)

/**
 * The overlay shown when the user invokes the assistant. Voice in (SpeechRecognizer), agent loop in the
 * middle, spoken + written answer out. Typing is available as a fallback.
 */
class AssistActivity : ComponentActivity() {

    private var ui by mutableStateOf(AssistUi())
    private lateinit var stt: AndroidSpeechToText
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var agent: BuiltAgent? = null
    private var agentJob: Job? = null
    private val history = mutableListOf<ChatMessage>()

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening() else ui = ui.copy(status = AssistStatus.Error, answer = "Microphone permission is needed to hear you. You can still type.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cfg = AgentConfig(this)
        stt = AndroidSpeechToText(this, cfg.voiceLanguage.ifBlank { null })
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = if (cfg.voiceLanguage.isNotBlank()) Locale.forLanguageTag(cfg.voiceLanguage) else Locale.getDefault()
        }
        setContent { AssistScreen() }
        startListening()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        startListening()
    }

    private fun startListening() {
        tts?.stop()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        ui = ui.copy(status = AssistStatus.Listening, transcript = "", answer = "")
        stt.start { event ->
            when (event) {
                is SttEvent.Ready -> {}
                is SttEvent.Partial -> ui = ui.copy(transcript = event.text)
                is SttEvent.Final -> {
                    ui = ui.copy(transcript = event.text)
                    submit(event.text)
                }
                is SttEvent.Failure -> ui = ui.copy(
                    status = if (event.recoverable) AssistStatus.Idle else AssistStatus.Error,
                    answer = event.message,
                )
            }
        }
    }

    private fun submit(text: String) {
        if (text.isBlank()) return
        agentJob?.cancel()
        ui = ui.copy(status = AssistStatus.Thinking, transcript = text, answer = "", steps = emptyList())
        agentJob = lifecycleScope.launch {
            try {
                val built = agent ?: AgentFactory.build(
                    this@AssistActivity,
                    confirm = { tool, args ->
                        val d = CompletableDeferred<Boolean>()
                        ui = ui.copy(pending = PendingConfirm(tool, args, d))
                        val ok = d.await()
                        ui = ui.copy(pending = null)
                        ok
                    },
                    onEvent = { e ->
                        if (e is AgentEvent.ToolStart) ui = ui.copy(steps = ui.steps + "Running ${e.name}")
                        if (e is AgentEvent.Escalated) ui = ui.copy(steps = ui.steps + "Switching to ${e.toLabel}")
                    },
                ).also { agent = it }
                val result = built.loop.run(text, history.toList())
                history.clear()
                history.addAll(result.messages.filter { it.role != "system" }.takeLast(24))
                ui = ui.copy(status = if (result.ok) AssistStatus.Done else AssistStatus.Error, answer = result.text)
                speak(result.text)
            } catch (e: AgentSetupException) {
                ui = ui.copy(status = AssistStatus.Error, answer = e.message.orEmpty())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ui = ui.copy(status = AssistStatus.Error, answer = "Something went wrong: ${e.message}")
            }
        }
    }

    private fun speak(text: String) {
        if (ttsReady && AgentConfig(this).speakReplies && text.isNotBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer")
        }
    }

    override fun onDestroy() {
        stt.release()
        tts?.shutdown()
        super.onDestroy()
    }

    @Composable
    private fun AssistScreen() {
        val dark = isSystemInDarkTheme()
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            Box(
                Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            when (ui.status) {
                                AssistStatus.Idle -> "Tap the mic and speak"
                                AssistStatus.Listening -> "Listening…"
                                AssistStatus.Thinking -> "Working on it…"
                                AssistStatus.Done -> "Done"
                                AssistStatus.Error -> "Problem"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (ui.transcript.isNotBlank()) Text("“${ui.transcript}”", style = MaterialTheme.typography.titleMedium)
                        ui.steps.takeLast(4).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        if (ui.answer.isNotBlank()) {
                            Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                                Text(ui.answer, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        var typed by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = typed, onValueChange = { typed = it }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), placeholder = { Text("or type here") },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { startListening() }, enabled = ui.status != AssistStatus.Thinking) { Text("🎤 Speak") }
                            Button(onClick = { submit(typed); typed = "" }, enabled = typed.isNotBlank() && ui.status != AssistStatus.Thinking) { Text("Send") }
                            TextButton(onClick = { finish() }) { Text("Close") }
                        }
                    }
                }
            }
            ui.pending?.let { p ->
                AlertDialog(
                    onDismissRequest = { p.answer.complete(false) },
                    title = { Text("Allow this action?") },
                    text = { Text("${p.tool}\n${p.args.take(300)}") },
                    confirmButton = { Button(onClick = { p.answer.complete(true) }) { Text("Allow") } },
                    dismissButton = { TextButton(onClick = { p.answer.complete(false) }) { Text("Deny") } },
                )
            }
        }
    }
}
