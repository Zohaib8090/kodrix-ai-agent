package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
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
    var holdMenuRecord by remember { mutableStateOf<BuildRecord?>(null) }
    var renameDialogRecord by remember { mutableStateOf<BuildRecord?>(null) }
    var deleteDialogRecord by remember { mutableStateOf<BuildRecord?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    // Clear All History Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Projects", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all past projects? This action cannot be undone.", fontFamily = InterFontFamily) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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

    // Rename Project Dialog
    if (renameDialogRecord != null) {
        val targetRecord = renameDialogRecord!!
        AlertDialog(
            onDismissRequest = { renameDialogRecord = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rename Project", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column {
                    Text("Enter a new name for your project:", fontFamily = InterFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        label = { Text("Project Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInputText.isNotBlank()) {
                            viewModel.renameProject(targetRecord, renameInputText.trim())
                            renameDialogRecord = null
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogRecord = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Single Project Confirmation Dialog
    if (deleteDialogRecord != null) {
        val targetRecord = deleteDialogRecord!!
        AlertDialog(
            onDismissRequest = { deleteDialogRecord = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Project", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    "Are you sure you want to delete '${targetRecord.appName}'? All project source files and history will be permanently deleted.",
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRecord(targetRecord)
                        deleteDialogRecord = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogRecord = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Long-Press Project Options Bottom Sheet
    if (holdMenuRecord != null) {
        val selectedRecord = holdMenuRecord!!
        ModalBottomSheet(
            onDismissRequest = { holdMenuRecord = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp, top = 8.dp)
            ) {
                // Header with Project Details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selectedRecord.platform == "ANDROID") Icons.Default.Android else Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedRecord.appName,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${selectedRecord.platform} • ${selectedRecord.status}",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Option 1: Rename Project
                ListItem(
                    headlineContent = { Text("Rename Project", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                    supportingContent = { Text("Change the display and folder name", fontFamily = InterFontFamily, fontSize = 11.sp) },
                    leadingContent = {
                        Icon(Icons.Outlined.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            renameInputText = selectedRecord.appName
                            renameDialogRecord = selectedRecord
                            holdMenuRecord = null
                        }
                )

                // Option 2: Open in Workspace
                ListItem(
                    headlineContent = { Text("Open Workspace & Code", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                    supportingContent = { Text("Launch file editor, preview & AI chat", fontFamily = InterFontFamily, fontSize = 11.sp) },
                    leadingContent = {
                        Icon(Icons.Default.Code, contentDescription = "Workspace", tint = MaterialTheme.colorScheme.secondary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            holdMenuRecord = null
                            onNavigateToTracker(selectedRecord.id)
                        }
                )

                // Option 3: Delete Project
                ListItem(
                    headlineContent = {
                        Text(
                            "Delete Project",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    supportingContent = {
                        Text("Permanently remove files and record", fontFamily = InterFontFamily, fontSize = 11.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                    },
                    leadingContent = {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            deleteDialogRecord = selectedRecord
                            holdMenuRecord = null
                        }
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "My Projects",
                            fontFamily = InterFontFamily,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${records.size} project${if (records.size == 1) "" else "s"} • Hold any card for options",
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
                                contentDescription = "Clear All",
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
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Projects Yet",
                        fontFamily = InterFontFamily,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Generated applications and websites will appear here. Tap any project to open the live workspace, or hold a project to rename or delete it.",
                        fontFamily = InterFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                        onHold = { holdMenuRecord = record },
                        onRename = {
                            renameInputText = record.appName
                            renameDialogRecord = record
                        },
                        onDelete = { deleteDialogRecord = record },
                        context = context
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryRecordCard(
    record: BuildRecord,
    onOpenTracker: () -> Unit,
    onHold: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    context: Context
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val formattedDate = remember(record.timestamp) { dateFormat.format(Date(record.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onOpenTracker,
                onLongClick = onHold
            )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
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
                    Column(modifier = Modifier.weight(1f)) {
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    BuildStatusChip(statusText = record.status)
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onHold,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Project Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
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

                    Button(
                        onClick = onOpenTracker,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Workspace & Code", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Rename project",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete project",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
