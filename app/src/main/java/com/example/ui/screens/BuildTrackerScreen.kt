package com.example.ui.screens
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BuildArtifact
import com.example.data.model.BuildPhase
import com.example.ui.common.AiDiffProposalDialog
import com.example.ui.common.BuildPhaseVerticalStepper
import com.example.ui.common.BuildStatusChip
import com.example.ui.common.InAppWebPreview
import com.example.ui.theme.*
import com.example.ui.viewmodel.BuildTrackerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildTrackerScreen(
    buildId: String,
    viewModel: BuildTrackerViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showLogs by remember { mutableStateOf(false) }

    LaunchedEffect(buildId) {
        viewModel.loadAndStartPipeline(buildId)
    }

    if (state.diffProposal != null) {
        AiDiffProposalDialog(
            diffProposal = state.diffProposal!!,
            retryCount = state.retryCount,
            maxRetries = state.maxRetries,
            onConfirmRecommit = { viewModel.applyProposalAndRetry() },
            onDismiss = { viewModel.dismissDiffDialog() }
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
                            text = state.buildRecord?.appName ?: "Build Tracker",
                            fontFamily = InterFontFamily,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Build Run #${state.runId ?: "Initializing"}",
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
                    if (state.runUrl != null) {
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.runUrl))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.testTag("open_github_run_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "View in GitHub",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Overview Banner
            item {
                Card(
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
                            BuildStatusChip(
                                statusText = when (state.phase) {
                                    is BuildPhase.Completed -> "Completed"
                                    is BuildPhase.Failed -> "Failed"
                                    is BuildPhase.Queued -> "Queued"
                                    is BuildPhase.Idle -> "Idle"
                                    else -> "In Progress"
                                }
                            )

                            Text(
                                text = state.buildRecord?.platform ?: "ANDROID",
                                fontFamily = InterFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = state.buildRecord?.prompt ?: "",
                            fontFamily = InterFontFamily,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (state.retryCount > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Self-healing attempts: ${state.retryCount} of ${state.maxRetries}",
                                fontFamily = InterFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 2. Vertical Stepper Pipeline Progress
            item {
                BuildPhaseVerticalStepper(currentPhase = state.phase)
            }

            // 3. Artifact Delivery Panel (When Completed)
            if (state.phase is BuildPhase.Completed) {
                val artifact = (state.phase as BuildPhase.Completed).artifact
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusCompleted.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(StatusCompleted),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (artifact is BuildArtifact.ApkArtifact) Icons.Default.Android else Icons.Default.Language,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = if (artifact is BuildArtifact.ApkArtifact) "Android APK Ready for Install!" else "Web Application Live & Deployed!",
                                fontFamily = InterFontFamily,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (artifact is BuildArtifact.ApkArtifact)
                                    "Saved locally: ${artifact.localPath.substringAfterLast('/')}"
                                else
                                    (artifact as BuildArtifact.WebArtifact).deployUrl,
                                fontFamily = InterFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            if (artifact is BuildArtifact.ApkArtifact) {
                                Button(
                                    onClick = { viewModel.installApk(artifact.localPath) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("install_apk_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StatusCompleted,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Install APK on Device", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold)
                                }
                            } else if (artifact is BuildArtifact.WebArtifact) {
                                Button(
                                    onClick = { viewModel.openWebUrl(artifact.deployUrl) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("open_browser_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open in Browser", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // In-app live web preview of deployed app
                                InAppWebPreview(
                                    targetUrl = artifact.deployUrl,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Failure & Retry Panel (When Failed)
            if (state.phase is BuildPhase.Failed) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusFailed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = StatusFailed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Build Pipeline Error",
                                    fontFamily = InterFontFamily,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusFailed
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = (state.phase as BuildPhase.Failed).log,
                                fontFamily = InterFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.retryBuild() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("retry_build_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusFailed)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Re-run Pipeline", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // 5. Diagnostics & Runner Logs Drawer
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Runner Logs & Diagnostics",
                                    fontFamily = InterFontFamily,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(onClick = { showLogs = !showLogs }) {
                                Text(
                                    text = if (showLogs) "Hide" else "Show",
                                    fontFamily = InterFontFamily,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        AnimatedVisibility(visible = showLogs) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = state.logs.ifEmpty { "Executing runner tasks..." },
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFFE0E0E0)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
