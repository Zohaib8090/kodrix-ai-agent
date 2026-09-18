package com.example.ui.screens
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.BuildRecord
import com.example.ui.common.BuildStatusChip
import com.example.ui.theme.*
import com.example.ui.viewmodel.HistoryViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTracker: (String) -> Unit
) {
    val records by viewModel.historyRecords.collectAsState()
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Build History") },
            text = { Text("Are you sure you want to delete all past build records? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusFailed)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Projects & Builds",
                            fontFamily = InterFontFamily,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${records.size} projects",
                            fontFamily = InterFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (records.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear History",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HistoryToggleOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Build Records Yet",
                        fontFamily = InterFontFamily,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Generated applications and runner builds will appear here.",
                        fontFamily = InterFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    HistoryRecordCard(
                        record = record,
                        onOpenTracker = { onNavigateToTracker(record.id) },
                        onDelete = { viewModel.deleteRecord(record.id) },
                        context = context
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryRecordCard(
    record: BuildRecord,
    onOpenTracker: () -> Unit,
    onDelete: () -> Unit,
    context: Context
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val formattedDate = remember(record.timestamp) { dateFormat.format(Date(record.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTracker() }
            .testTag("history_item_${record.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (record.platform == "ANDROID") Icons.Default.Android else Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = record.appName,
                            fontFamily = InterFontFamily,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$formattedDate • ${record.platform}",
                            fontFamily = InterFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                BuildStatusChip(statusText = record.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = record.prompt,
                fontFamily = InterFontFamily,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!record.localApkPath.isNullOrBlank()) {
                        Button(
                            onClick = {
                                try {
                                    val apkFile = File(record.localApkPath)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/vnd.android.package-archive")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Install", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }

                    if (!record.webDeployUrl.isNullOrBlank()) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(record.webDeployUrl)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Launch", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenTracker,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("View Run", fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete record",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
