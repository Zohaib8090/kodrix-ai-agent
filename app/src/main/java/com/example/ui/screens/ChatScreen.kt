package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.agent.AgentConfig
import com.example.ui.viewmodel.ChatViewModel
import com.example.voice.AndroidSpeechToText
import com.example.voice.SttEvent
import kotlinx.coroutines.launch

/** Home screen: a plain chat with the agent. Terminal, web, memory and MCP tools run silently behind it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToBuild: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAgentSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lines = viewModel.lines

    // ---- voice ----
    var listening by remember { mutableStateOf(false) }
    var voiceNote by remember { mutableStateOf<String?>(null) }
    val stt = remember { AndroidSpeechToText(context, AgentConfig(context).voiceLanguage.ifBlank { null }) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { ttsReady = it == TextToSpeech.SUCCESS }
        onDispose { stt.release(); tts?.shutdown() }
    }

    fun startListening() {
        tts?.stop()
        listening = true
        voiceNote = null
        stt.start { e ->
            when (e) {
                is SttEvent.Ready -> {}
                is SttEvent.Partial -> input = e.text
                is SttEvent.Final -> { listening = false; input = ""; viewModel.send(e.text, viaVoice = true) }
                is SttEvent.Failure -> { listening = false; voiceNote = e.message }
            }
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else voiceNote = "Microphone permission is needed to use your voice. You can still type."
    }
    fun onMic() {
        if (listening) { stt.stop(); return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Speak the answer only when the question was spoken.
    LaunchedEffect(lines.size) {
        val last = lines.lastOrNull()
        if (last != null && !last.fromUser && !last.isError && viewModel.lastViaVoice && ttsReady && AgentConfig(context).speakReplies) {
            tts?.speak(last.text, TextToSpeech.QUEUE_FLUSH, null, "reply")
        }
    }
    LaunchedEffect(lines.size, viewModel.thinking) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size)
    }

    viewModel.pending?.let { p ->
        AlertDialog(
            onDismissRequest = { viewModel.answerApproval(false) },
            title = { Text("Allow this action?") },
            text = { Text("${p.tool}\n${p.args.take(400)}") },
            confirmButton = { TextButton(onClick = { viewModel.answerApproval(true) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { viewModel.answerApproval(false) }) { Text("Deny") } },
        )
    }

    fun go(action: () -> Unit) { scope.launch { drawerState.close(); action() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(300.dp)) {
                Spacer(Modifier.height(24.dp))
                Text("Kodrix AI", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                val pad = NavigationDrawerItemDefaults.ItemPadding
                NavigationDrawerItem(icon = { Icon(Icons.Default.Add, null) }, label = { Text("New chat") }, selected = false,
                    onClick = { go { viewModel.newChat() } }, modifier = Modifier.padding(pad))
                NavigationDrawerItem(icon = { Icon(Icons.Default.Build, null) }, label = { Text("Build") }, selected = false,
                    onClick = { go(onNavigateToBuild) }, modifier = Modifier.padding(pad).testTag("drawer_build"))
                NavigationDrawerItem(icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Projects") }, selected = false,
                    onClick = { go(onNavigateToHistory) }, modifier = Modifier.padding(pad))
                NavigationDrawerItem(icon = { Icon(Icons.Default.Hub, null) }, label = { Text("Connectors & agent") }, selected = false,
                    onClick = { go(onNavigateToAgentSettings) }, modifier = Modifier.padding(pad).testTag("drawer_agent"))
                NavigationDrawerItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") }, selected = false,
                    onClick = { go(onNavigateToSettings) }, modifier = Modifier.padding(pad))
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Kodrix") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.testTag("menu_button")) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                )
            },
            modifier = Modifier.imePadding(),
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                if (lines.isEmpty() && !viewModel.thinking) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Ask me anything, or tap the mic and talk.\nI can search the web, use your connectors, and run commands for you in the background.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        items(lines) { line ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = when {
                                        line.fromUser -> MaterialTheme.colorScheme.primaryContainer
                                        line.isError -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    modifier = Modifier.widthIn(max = 320.dp),
                                ) { SelectionContainer { Text(line.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) } }
                            }
                        }
                        if (viewModel.thinking) {
                            item {
                                Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Thinking…", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                voiceNote?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(if (listening) "Listening…" else "Message") },
                        modifier = Modifier.weight(1f).testTag("chat_input"),
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (input.isBlank() || listening) {
                        FilledIconButton(onClick = { onMic() }, enabled = !viewModel.thinking, modifier = Modifier.testTag("chat_mic")) {
                            Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, if (listening) "Stop listening" else "Speak")
                        }
                    } else {
                        FilledIconButton(onClick = { viewModel.send(input); input = "" }, enabled = !viewModel.thinking, modifier = Modifier.testTag("chat_send")) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send")
                        }
                    }
                }
            }
        }
    }
}
