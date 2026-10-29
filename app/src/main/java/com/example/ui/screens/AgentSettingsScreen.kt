package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.agent.AgentConfig
import com.example.agent.McpServerConfig

/** Connectors (MCP servers), the stronger fallback model, and voice options for the chat agent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val config = remember { AgentConfig(context) }
    var servers by remember { mutableStateOf(config.mcpServers) }
    var strong by remember { mutableStateOf(config.strongModel) }
    var lang by remember { mutableStateOf(config.voiceLanguage) }
    var speak by remember { mutableStateOf(config.speakReplies) }
    var bypass by remember { mutableStateOf(config.bypassPermissions) }
    var showBypassWarning by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    if (showBypassWarning) {
        AlertDialog(
            onDismissRequest = { showBypassWarning = false },
            title = { Text("Turn on bypass permissions?") },
            text = { Text("Kodrix will run risky actions without asking: deleting or overwriting memory, sending or deleting through your connectors, and phone actions. A wrong guess by the AI can cause real changes. Memory deletions still go to the recycle bin.") },
            confirmButton = { TextButton(onClick = { bypass = true; config.bypassPermissions = true; showBypassWarning = false }) { Text("Turn on") } },
            dismissButton = { TextButton(onClick = { showBypassWarning = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connectors & agent") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Permissions", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bypass permissions")
                        Text("Off: Kodrix asks before risky actions. On: it never asks.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = bypass, onCheckedChange = { if (it) showBypassWarning = true else { bypass = false; config.bypassPermissions = false } }, modifier = Modifier.testTag("bypass_switch"))
                }
            }
            item { Text("Models", style = MaterialTheme.typography.titleMedium) }
            item {
                OutlinedTextField(
                    value = strong, onValueChange = { strong = it; config.strongModel = it },
                    label = { Text("Stronger model (used when the cheap one struggles)") },
                    supportingText = { Text("Model ID from your provider, e.g. copy it from Settings > AI and Models. Blank = none.") },
                    modifier = Modifier.fillMaxWidth().testTag("strong_model"), singleLine = true,
                )
            }
            item { Text("Voice", style = MaterialTheme.typography.titleMedium) }
            item {
                OutlinedTextField(
                    value = lang, onValueChange = { lang = it; config.voiceLanguage = it },
                    label = { Text("Speech language (optional)") },
                    supportingText = { Text("e.g. en-US, hi-IN, ur-PK. Blank = phone default.") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Speak replies when I use my voice", Modifier.weight(1f))
                    Switch(checked = speak, onCheckedChange = { speak = it; config.speakReplies = it })
                }
            }
            item { Text("Connectors (MCP servers)", style = MaterialTheme.typography.titleMedium) }
            item { Text("Add the URL of an MCP server (Streamable HTTP). Its tools become available in chat. Changes apply to new chats.", style = MaterialTheme.typography.bodySmall) }
            items(servers) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall)
                            Text(s.url, style = MaterialTheme.typography.bodySmall)
                            if (s.bearerToken.isNotBlank()) Text("Token saved", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { servers = servers - s; config.mcpServers = servers }) { Icon(Icons.Default.Delete, "Remove ${s.name}") }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. spotify)") }, modifier = Modifier.fillMaxWidth().testTag("mcp_name"), singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text("Server URL (https://…/mcp)") }, modifier = Modifier.fillMaxWidth().testTag("mcp_url"), singleLine = true)
                    OutlinedTextField(token, { token = it }, label = { Text("Bearer token (optional)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        onClick = {
                            val u = url.trim()
                            val n = name.trim().ifBlank { "server${servers.size + 1}" }
                            error = when {
                                !(u.startsWith("https://") || u.startsWith("http://")) -> "The URL must start with https://"
                                servers.any { it.name.equals(n, true) } -> "A connector named $n already exists."
                                else -> null
                            }
                            if (error == null) {
                                servers = servers + McpServerConfig(n, u, token.trim()); config.mcpServers = servers
                                name = ""; url = ""; token = ""
                            }
                        },
                        enabled = url.isNotBlank(),
                        modifier = Modifier.testTag("mcp_add"),
                    ) { Text("Add connector") }
                }
            }
        }
    }
}
