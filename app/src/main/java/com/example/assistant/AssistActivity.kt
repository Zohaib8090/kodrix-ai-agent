package com.example.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
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
        val sendImages = attachments.flatMap { it.dataUrls }
        attachments.clear()
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
                val result = built.loop.run(text, history.toList(), sendImages)
                history.clear()
                // Pictures are not kept in history; they would be re-sent (and re-billed) on every later turn.
                history.addAll(result.messages.filter { it.role != "system" }.takeLast(24).map { it.copy(images = emptyList()) })
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
        val shouldSpeak = ttsReady && text.isNotBlank() && (live || AgentConfig(this).speakReplies)
        if (shouldSpeak) {
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onError(utteranceId: String?) { continueLive() }
                override fun onDone(utteranceId: String?) { continueLive() }
            })
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer")
        } else continueLive()
    }

    /** In Live mode, start listening again once the answer has been spoken. */
    private fun continueLive() {
        if (!live || isFinishing) return
        runOnUiThread { if (live && !isFinishing) startListening() }
    }

    override fun onDestroy() {
        stt.release()
        tts?.shutdown()
        super.onDestroy()
    }

    private val attachments = mutableStateListOf<Attachment>()
    private var live by mutableStateOf(false)
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        lifecycleScope.launch {
            uris.forEach { u ->
                val a = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MediaPrep.fromUri(this@AssistActivity, u) }
                if (a != null) attachments += a else ui = ui.copy(status = AssistStatus.Error, answer = "I couldn't read that file.")
            }
        }
    }

    private fun openFullApp() {
        startActivity(Intent(this, com.example.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    @Composable
    private fun AssistScreen() {
        val pill = Color(0xFF1B1B1D)
        val chip = Color(0xFF232325)
        val micBlue = Color(0xFF2F4A7D)
        val onPill = Color(0xFFE8E8EA)
        val muted = Color(0xFF9A9AA0)
        val screenshot by ScreenContext.screenshot.collectAsState()
        var typed by remember { mutableStateOf("") }
        var dragTotal by remember { mutableFloatStateOf(0f) }

        MaterialTheme(colorScheme = darkColorScheme()) {
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 12.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Answer card
                    if (ui.answer.isNotBlank() || ui.transcript.isNotBlank() || ui.steps.isNotEmpty() || ui.status == AssistStatus.Thinking) {
                        Surface(shape = RoundedCornerShape(24.dp), color = pill) {
                            Column(Modifier.padding(16.dp).heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (ui.transcript.isNotBlank()) Text(ui.transcript, color = muted, fontSize = 14.sp)
                                if (ui.status == AssistStatus.Thinking) Text(ui.steps.lastOrNull()?.let { "$it…" } ?: "Thinking…", color = muted, fontSize = 13.sp)
                                if (ui.answer.isNotBlank()) Text(ui.answer, color = if (ui.status == AssistStatus.Error) Color(0xFFFFB4AB) else onPill, fontSize = 16.sp)
                            }
                        }
                    }
                    // Attachments
                    if (attachments.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            attachments.toList().forEach { a ->
                                Surface(shape = RoundedCornerShape(16.dp), color = chip) {
                                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(a.label, color = onPill, fontSize = 13.sp)
                                        IconButton(onClick = { attachments.remove(a) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Remove", tint = muted, modifier = Modifier.size(16.dp)) }
                                    }
                                }
                            }
                        }
                    }
                    // "Share screen" chip: Kodrix has no live-streaming model, so it offers the screenshot Android provides instead.
                    screenshot?.let { shot ->
                        val already = attachments.any { it.label == "Screen" }
                        if (!already) Surface(shape = RoundedCornerShape(50), color = chip, modifier = Modifier.clickable {
                            attachments += Attachment("Screen", listOf(MediaPrep.toDataUrl(shot)), false)
                        }) {
                            Row(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ScreenShare, null, tint = onPill)
                                Spacer(Modifier.width(14.dp))
                                Text("Ask about this screen", color = onPill, fontSize = 16.sp)
                            }
                        }
                    }
                    // The pill
                    Surface(shape = RoundedCornerShape(36.dp), color = pill, modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Handle: drag up to open the full app.
                            Box(
                                Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragStart = { dragTotal = 0f },
                                        onDragEnd = { if (dragTotal < -60f) openFullApp() },
                                        onVerticalDrag = { _, dy -> dragTotal += dy },
                                    )
                                }.clickable { openFullApp() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.width(48.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF5A5A60)))
                            }
                            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                }) { Icon(Icons.Default.Add, "Attach photo or video", tint = onPill, modifier = Modifier.size(30.dp)) }
                                Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                    if (typed.isEmpty()) Text(if (ui.status == AssistStatus.Listening) "Listening…" else "Ask Kodrix", color = onPill, fontSize = 20.sp)
                                    BasicTextField(
                                        value = typed, onValueChange = { typed = it },
                                        textStyle = TextStyle(color = onPill, fontSize = 20.sp),
                                        cursorBrush = SolidColor(onPill),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(onSend = { if (typed.isNotBlank()) { submit(typed); typed = "" } }),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (typed.isNotBlank()) {
                                    FilledIconButton(onClick = { submit(typed); typed = "" }, enabled = ui.status != AssistStatus.Thinking,
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = micBlue), modifier = Modifier.size(56.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = onPill)
                                    }
                                } else {
                                    FilledIconButton(onClick = { live = false; if (ui.status == AssistStatus.Listening) stt.stop() else startListening() },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = micBlue), modifier = Modifier.size(56.dp)) {
                                        Icon(if (ui.status == AssistStatus.Listening) Icons.Default.Stop else Icons.Default.Mic, "Speak", tint = onPill)
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                // Live: hands-free conversation. It listens, answers out loud, then listens again.
                                IconButton(onClick = { live = !live; if (live) startListening() else { stt.cancel(); tts?.stop() } }) {
                                    Icon(Icons.Default.GraphicEq, "Live conversation", tint = if (live) Color(0xFF8AB4F8) else onPill, modifier = Modifier.size(30.dp))
                                }
                            }
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
