package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.agent.memory.MemoryStore
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Shows everything the agent has saved. You can read, edit, delete (to a recycle bin) and restore. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MemoryStore(File(context.filesDir, "agent_memory")) }
    var tick by remember { mutableIntStateOf(0) }
    val files = remember(tick) { store.entries() }
    val trash = remember(tick) { store.trash() }
    var open by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var showTrash by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty the recycle bin?") },
            text = { Text("This permanently deletes ${trash.size} item(s).") },
            confirmButton = { TextButton(onClick = { store.emptyTrash(); confirmEmpty = false; tick++ }) { Text("Delete forever") } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }

    val current = open
    if (current != null) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(current.removePrefix("/memories/")) },
                navigationIcon = { IconButton(onClick = { open = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = { store.write(current, text); message = "Saved"; open = null; tick++ }) { Text("Save") }
                    IconButton(onClick = { store.delete(current); message = "Moved to recycle bin"; open = null; tick++ }) { Icon(Icons.Default.Delete, "Delete") }
                },
            )
        }) { pad ->
            OutlinedTextField(text, { text = it }, Modifier.padding(pad).padding(12.dp).fillMaxSize())
        }
        return
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Memory") },
            navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    "What Kodrix has saved about you. It can add notes freely, but asks before deleting, overwriting or shortening anything. Deleted items go to the recycle bin below.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            message?.let { item { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) } }
            if (files.isEmpty()) item { Text("Nothing saved yet.", style = MaterialTheme.typography.bodyMedium) }
            items(files) { e ->
                Card(Modifier.fillMaxWidth().clickable { text = store.read(e.path); open = e.path }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(e.path.removePrefix("/memories/"), style = MaterialTheme.typography.titleSmall)
                        Text("${e.bytes} bytes · ${fmt.format(Date(e.modified))}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                TextButton(onClick = { showTrash = !showTrash }) { Text(if (showTrash) "Hide recycle bin" else "Recycle bin (${trash.size})") }
            }
            if (showTrash) {
                items(trash) { e ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.path.substringAfter("__").replace('~', '/'), style = MaterialTheme.typography.titleSmall)
                                Text("${e.bytes} bytes · ${fmt.format(Date(e.modified))}", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { message = "Restored to ${store.restore(e.path)}"; tick++ }) { Icon(Icons.Default.Restore, "Restore") }
                        }
                    }
                }
                if (trash.isNotEmpty()) item { TextButton(onClick = { confirmEmpty = true }) { Text("Empty recycle bin") } }
            }
        }
    }
}
