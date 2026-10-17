package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.services.LinuxSetupError
import com.example.data.services.LinuxSetupPhase
import com.example.data.services.LinuxSetupState
import com.example.data.services.formatBytes
import com.example.ui.theme.InterFontFamily

private val SETUP_STEPS = listOf("Check device", "Download", "Install files", "Configure")

private fun stepIndexFor(phase: LinuxSetupPhase?): Int = when (phase) {
    LinuxSetupPhase.CHECKING -> 0
    LinuxSetupPhase.RESOLVING, LinuxSetupPhase.DOWNLOADING -> 1
    LinuxSetupPhase.VERIFYING, LinuxSetupPhase.EXTRACTING -> 2
    LinuxSetupPhase.CONFIGURING -> 3
    LinuxSetupPhase.DONE -> SETUP_STEPS.size
    else -> -1
}

/** Asks the user for permission before the app downloads and installs the Linux environment. */
@Composable
fun LinuxSetupPrompt(onInstall: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
        title = { Text("Set up the Linux environment?", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "To build and preview this project, the assistant needs a shell with Node.js and npm. " +
                    "Kodrix can install one inside the app. It downloads about 30 MB, needs roughly 400 MB of free " +
                    "storage, and stays private to this app.",
                fontFamily = InterFontFamily,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(onClick = onInstall, modifier = Modifier.testTag("linux_prompt_install")) { Text("Install") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinuxSetupSheet(
    state: LinuxSetupState,
    log: List<String>,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showLog by remember { mutableStateOf(false) }
    val failed = state.phase == LinuxSetupPhase.FAILED
    val done = state.phase == LinuxSetupPhase.DONE
    val cancelled = failed && state.error == LinuxSetupError.CANCELLED
    val idle = state.phase == LinuxSetupPhase.IDLE

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            // The body scrolls; the action buttons below stay pinned so they are always reachable.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
            Text(
                text = "Linux environment",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    done -> "Installed and ready to use."
                    failed && !cancelled -> "Setup could not finish."
                    cancelled -> "Setup was cancelled. Nothing was installed."
                    idle -> "Install a private shell with Node.js, npm and git inside the app."
                    else -> "Keep the app open while this runs."
                },
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (!idle) {
                SetupSteps(state)
                Spacer(Modifier.height(16.dp))
            }

            if (state.isRunning) {
                val progress = state.overallProgress
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .testTag("linux_setup_progress"),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = detailLine(state),
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            if (failed && !cancelled) {
                ErrorCard(state)
                Spacer(Modifier.height(16.dp))
            }

            if (log.isNotEmpty()) {
                TextButton(onClick = { showLog = !showLog }) {
                    Text(if (showLog) "Hide details" else "Show details")
                }
                if (showLog) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF030712),
                        border = BorderStroke(1.dp, Color(0xFF1F2937)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState(Int.MAX_VALUE))
                                .padding(10.dp)
                        ) {
                            log.takeLast(60).forEach { line ->
                                Text(
                                    text = line,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                when {
                    state.isRunning -> {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.testTag("linux_setup_cancel")) { Text("Cancel setup") }
                        TextButton(onClick = onDismiss) { Text("Hide") }
                    }
                    done -> Button(onClick = onDismiss) { Text("Done") }
                    failed -> {
                        if (log.isNotEmpty() && !cancelled) {
                            TextButton(onClick = { copyLog(context, state, log) }) { Text("Copy log") }
                        }
                        TextButton(onClick = onDismiss) { Text("Close") }
                        Button(onClick = onStart, modifier = Modifier.testTag("linux_setup_retry")) {
                            Text(if (cancelled) "Start again" else "Try again")
                        }
                    }
                    else -> {
                        TextButton(onClick = onDismiss) { Text("Not now") }
                        Button(onClick = onStart, modifier = Modifier.testTag("linux_setup_start")) { Text("Install") }
                    }
                }
            }
        }
    }
}

private fun detailLine(state: LinuxSetupState): String = when (state.phase) {
    LinuxSetupPhase.DOWNLOADING -> {
        val sizes = if (state.totalBytes > 0) "${formatBytes(state.bytesDownloaded)} of ${formatBytes(state.totalBytes)}"
        else formatBytes(state.bytesDownloaded)
        if (state.mirrorCount > 1) "$sizes (source ${state.mirrorIndex} of ${state.mirrorCount})" else sizes
    }
    LinuxSetupPhase.EXTRACTING ->
        if (state.filesTotal > 0) "${state.filesDone} of ${state.filesTotal} files" else state.message
    else -> state.message
}

@Composable
private fun SetupSteps(state: LinuxSetupState) {
    val failed = state.phase == LinuxSetupPhase.FAILED
    val current = if (failed) stepIndexFor(state.failedPhase) else stepIndexFor(state.phase)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SETUP_STEPS.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    when {
                        index < current -> Icon(
                            Icons.Default.CheckCircle, contentDescription = "Done",
                            tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp)
                        )
                        index == current && failed -> Icon(
                            Icons.Default.ErrorOutline, contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)
                        )
                        index == current -> CircularProgressIndicator(
                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                        )
                        else -> Icon(
                            Icons.Default.RadioButtonUnchecked, contentDescription = "Pending",
                            tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = if (index == current) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (index > current && current >= 0) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(state: LinuxSetupState) {
    val error = state.error ?: return
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = error.title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = error.hint,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            state.errorDetail?.takeIf { it.isNotBlank() }?.let { detail ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun copyLog(context: Context, state: LinuxSetupState, log: List<String>) {
    val text = buildString {
        state.error?.let { appendLine("Error: ${it.title}") }
        state.errorDetail?.let { appendLine("Detail: $it") }
        appendLine()
        log.forEach { appendLine(it) }
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Linux setup log", text))
    Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
}
