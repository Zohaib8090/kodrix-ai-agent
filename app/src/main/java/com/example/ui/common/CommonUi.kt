package com.example.ui.common

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.BuildPhase
import com.example.data.model.DiffProposal
import com.example.data.model.UiState
import com.example.ui.theme.*

/**
 * Reusable StateContent as specified in Section 6.5.
 */
@Composable
fun <T> StateContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            is UiState.Loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp).testTag("loading_indicator")
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Processing request...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is UiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error icon",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Operation Encountered an Issue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (state.canRetry) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.testTag("retry_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
            }
            is UiState.Success -> {
                content(state.data)
            }
        }
    }
}

/**
 * Vertical stepper for BuildPhase as mandated by 6.2 & 6.3.
 */
@Composable
fun BuildPhaseVerticalStepper(
    currentPhase: BuildPhase,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        "Committing Workflow" to "Store CI/CD configuration in repository",
        "Pushing Secrets" to "Libsodium-encrypt provider credentials",
        "Dispatching Workflow" to "Trigger GitHub Actions runner",
        "Runner Queued" to "Awaiting available build environment",
        "AI Codegen" to "Scaffold and commit source files",
        "Build & Package" to "Compile Android APK / Web bundle",
        "Artifact Delivery" to "Download APK or deploy to live host"
    )

    val currentStepIndex = when (currentPhase) {
        is BuildPhase.Idle -> -1
        is BuildPhase.CommittingWorkflow -> 0
        is BuildPhase.PushingSecrets -> 1
        is BuildPhase.Dispatching -> 2
        is BuildPhase.Queued -> 3
        is BuildPhase.InProgress -> {
            val s = currentPhase.step.lowercase()
            if (s.contains("codegen") || s.contains("scaffold")) 4
            else if (s.contains("build") || s.contains("gradle") || s.contains("npm")) 5
            else 4
        }
        is BuildPhase.Completed -> 7
        is BuildPhase.Failed -> 5 // where failure typically occurs
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Build Pipeline Progress",
                fontFamily = InterFontFamily,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            steps.forEachIndexed { index, (title, description) ->
                val isCompleted = currentStepIndex > index || currentPhase is BuildPhase.Completed
                val isCurrent = currentStepIndex == index && currentPhase !is BuildPhase.Failed && currentPhase !is BuildPhase.Completed
                val isFailed = currentPhase is BuildPhase.Failed && currentStepIndex == index

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Step indicator column
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isFailed -> StatusFailed
                                        isCompleted -> StatusCompleted
                                        isCurrent -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isFailed -> Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                isCompleted -> Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                isCurrent -> CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White, modifier = Modifier.size(14.dp))
                                else -> Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                            }
                        }

                        if (index < steps.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(28.dp)
                                    .background(
                                        if (isCompleted) StatusCompleted.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontFamily = InterFontFamily,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isFailed -> StatusFailed
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isCompleted -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = description,
                            fontFamily = InterFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

/**
 * Status AssistChip styled per Section 6.4.
 */
@Composable
fun BuildStatusChip(
    statusText: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon) = when (statusText.lowercase()) {
        "completed", "success" -> Triple(StatusCompleted.copy(alpha = 0.2f), StatusCompleted, Icons.Default.CheckCircle)
        "in progress", "in_progress", "building" -> Triple(StatusInProgress.copy(alpha = 0.2f), StatusInProgress, Icons.Default.Sync)
        "queued" -> Triple(StatusQueued.copy(alpha = 0.2f), StatusQueued, Icons.Default.Schedule)
        "failed", "error" -> Triple(StatusFailed.copy(alpha = 0.2f), StatusFailed, Icons.Default.Error)
        else -> Triple(StatusNeutral.copy(alpha = 0.2f), StatusNeutral, Icons.Default.Info)
    }

    AssistChip(
        onClick = {},
        label = {
            Text(
                text = statusText,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(containerColor = bgColor),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.4f)),
        modifier = modifier
    )
}

/**
 * Low-fidelity Mock Compose Phone Frame preview for Android (Section 6.6).
 */
@Composable
fun PhonePreviewFrame(
    appName: String,
    prompt: String,
    features: List<String>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(260.dp)
            .height(440.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Mock Phone Notch Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )
            }

            // Mock App TopBar
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = appName.ifEmpty { "My Application" },
                        fontFamily = InterFontFamily,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                }
            }

            // Mock Content Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mock Search / Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Generated Overview",
                            fontFamily = InterFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = prompt.ifEmpty { "Your custom application layout" },
                            fontFamily = InterFontFamily,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Mock list items
                for (i in 1..3) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Entity Record #$i",
                                    fontFamily = InterFontFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        }
                    }
                }

                // Feature tags inside preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    features.take(2).forEach { f ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(text = f, fontFamily = InterFontFamily, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // Mock Action Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onSurface),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Primary Action", fontFamily = InterFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Mock Bottom Navigation
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Live Web Preview rendered directly inside in-app WebView (Section 7.8).
 */
@Composable
fun InAppWebPreview(
    htmlContent: String? = null,
    targetUrl: String? = null,
    baseUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(12.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                            // Automatically grant webcam & mic requested by the web page (e.g. hand gestures, MediaPipe, audio)
                            request?.let {
                                try {
                                    it.grant(it.resources)
                                } catch (e: Exception) {
                                    android.util.Log.e("InAppWebPreview", "Grant webview permission failed: ${e.message}")
                                }
                            }
                        }
                    }
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    try {
                        settings.allowFileAccessFromFileURLs = true
                        settings.allowUniversalAccessFromFileURLs = true
                    } catch (_: Exception) {}

                    if (!targetUrl.isNullOrBlank()) {
                        loadUrl(targetUrl)
                    } else if (!htmlContent.isNullOrBlank()) {
                        loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
                    }
                }
            },
            update = { webView ->
                if (!targetUrl.isNullOrBlank()) {
                    webView.loadUrl(targetUrl)
                } else if (!htmlContent.isNullOrBlank()) {
                    webView.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * AI Diff/Proposal Dialog before auto-recommitting (Section 2.4).
 */
@Composable
fun AiDiffProposalDialog(
    diffProposal: DiffProposal,
    retryCount: Int,
    maxRetries: Int = 3,
    onConfirmRecommit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Self-Healing Proposal",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Attempt $retryCount of $maxRetries",
                    fontFamily = InterFontFamily,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = diffProposal.summary,
                    fontFamily = InterFontFamily,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Proposed Code Modifications:",
                    fontFamily = InterFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                diffProposal.changedFiles.forEach { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = file.path,
                                fontFamily = InterFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = file.content.take(300) + if (file.content.length > 300) "..." else "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = diffProposal.explanation,
                    fontFamily = InterFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmRecommit,
                modifier = Modifier.testTag("confirm_recommit_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Apply & Re-commit", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = InterFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/**
 * Real interactive Terminal Console executing real shell commands via NodeService/ProcessBuilder.
 */
@Composable
fun TerminalConsoleView(
    modifier: Modifier = Modifier,
    terminalSession: com.example.data.services.RealTerminalSession? = null
) {
    val context = LocalContext.current
    val session = remember { terminalSession ?: com.example.data.services.RealTerminalSession(context) }
    val outputLines by session.outputLines.collectAsState()
    var inputCommand by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(outputLines.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (terminalSession == null) {
                session.destroy()
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Termux / Node.js Environment",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = { session.executeCommand("clear") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Clear", fontFamily = InterFontFamily, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Console output viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF18181B))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .verticalScroll(scrollState)
            ) {
                Column {
                    outputLines.forEach { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = when {
                                line.startsWith("$") -> Color(0xFFF97316)
                                line.contains("Error", ignoreCase = true) -> Color(0xFFEF4444)
                                line.contains("Ready", ignoreCase = true) -> Color(0xFF4ADE80)
                                else -> Color(0xFFE4E4E7)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick command pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("ls -la", "pwd", "node -v", "npm -v").forEach { cmd ->
                    Surface(
                        onClick = { session.executeCommand(cmd) },
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = cmd,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Command input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$ ",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = inputCommand,
                    onValueChange = { inputCommand = it },
                    placeholder = { Text("type shell command...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        if (inputCommand.isNotBlank()) {
                            session.executeCommand(inputCommand)
                            inputCommand = ""
                        }
                    },
                    modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalConsoleBottomSheet(
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TerminalConsoleView()
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

