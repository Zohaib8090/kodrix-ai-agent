package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.local.PreferenceStorage
import com.example.ui.theme.*
import com.example.ui.viewmodel.OnboardingViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinishOnboarding: () -> Unit,
    onNavigateBack: () -> Unit = onFinishOnboarding
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showClientIdDialog by remember { mutableStateOf(false) }

    val isVerified = state.verifiedUser != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GitHub Connection",
                        fontFamily = InterFontFamily,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("onboarding_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(
                        onClick = { showClientIdDialog = true },
                        modifier = Modifier.testTag("oauth_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "OAuth Client ID Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { viewModel.completeOnboarding(onFinishOnboarding) },
                        enabled = isVerified,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("continue_to_builder_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isVerified) "Continue to App Builder" else "Connect GitHub to Continue",
                            fontFamily = InterFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isVerified) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Step Progress Indicator
            LinearProgressIndicator(
                progress = { if (isVerified) 1.0f else 0.5f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                trackColor = MaterialTheme.colorScheme.outline,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            // Explanatory Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "GitHub Actions compiles and packages your mobile & web apps serverlessly — no backend server required.",
                        fontFamily = InterFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Target Repository Name section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Target Repository",
                    fontFamily = InterFontFamily,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = state.githubRepoName,
                    onValueChange = { viewModel.onRepoNameChange(it) },
                    label = { Text("Repository Name", fontFamily = InterFontFamily) },
                    placeholder = { Text("my-nocode-apps", fontFamily = InterFontFamily) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_repo_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = "Created automatically as a private repository if it doesn't already exist.",
                    fontFamily = InterFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Main Auth Flow: Connected Card OR Sign In Flow
            if (isVerified && state.verifiedUser != null) {
                ConnectedAccountCard(
                    user = state.verifiedUser!!,
                    repoName = state.githubRepoName,
                    onDisconnect = { viewModel.disconnectGithub() }
                )
            } else {
                // Not connected yet: show OAuth primary button
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Authentication Method",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Primary Action: Sign in with GitHub (OAuth)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF24292F)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_github),
                                            contentDescription = "GitHub Mark",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "GitHub OAuth (Recommended)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "1-click browser sign-in • No server secret",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text("Secure", fontSize = 11.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = StatusCompleted.copy(alpha = 0.12f),
                                        labelColor = StatusCompleted
                                    ),
                                    border = null
                                )
                            }

                            Button(
                                onClick = { viewModel.startDeviceOAuth() },
                                enabled = !state.isStartingOAuth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("oauth_github_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF24292F),
                                    contentColor = Color.White
                                )
                            ) {
                                if (state.isStartingOAuth) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Connecting to GitHub...")
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_github),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Sign in with GitHub",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Device authorization grant flow",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { showClientIdDialog = true },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Client ID Settings", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Display general GitHub Error if present
                    if (state.githubError != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusFailed.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = StatusFailed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.githubError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog: OAuth Device Authorization in Progress
    if (state.deviceFlow != null) {
        val flow = state.deviceFlow!!
        DeviceAuthorizationDialog(
            userCode = flow.userCode,
            verificationUri = flow.verificationUriComplete ?: flow.verificationUri,
            expiresIn = flow.expiresIn,
            statusText = flow.statusText,
            errorMessage = flow.error,
            onOpenBrowser = {
                val url = flow.verificationUriComplete ?: flow.verificationUri
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onCopyCode = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("GitHub Code", flow.userCode))
                Toast.makeText(context, "Code ${flow.userCode} copied to clipboard!", Toast.LENGTH_SHORT).show()
            },
            onCancel = { viewModel.cancelDeviceOAuth() },
            onRetry = { viewModel.startDeviceOAuth() }
        )
    }

    // Modal Dialog: Custom OAuth Client ID Settings
    if (showClientIdDialog) {
        ClientIdSettingsDialog(
            currentClientId = state.githubClientId,
            onSave = { newId ->
                viewModel.onClientIdChange(newId)
                showClientIdDialog = false
                Toast.makeText(context, "GitHub Client ID saved", Toast.LENGTH_SHORT).show()
            },
            onResetDefault = {
                viewModel.onClientIdChange(PreferenceStorage.DEFAULT_GITHUB_CLIENT_ID)
                showClientIdDialog = false
                Toast.makeText(context, "Reset to default Client ID", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClientIdDialog = false }
        )
    }
}

/**
 * Card displaying verified GitHub account status.
 */
@Composable
private fun ConnectedAccountCard(
    user: com.example.data.remote.GitHubUser,
    repoName: String,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = StatusCompleted.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(StatusCompleted.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusCompleted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = user.name ?: user.login,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${user.login}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = "GitHub Connected",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = StatusCompleted.copy(alpha = 0.1f),
                        labelColor = StatusCompleted
                    ),
                    border = null
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Linked Repository",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${user.login}/$repoName",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Private Repo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StatusFailed
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Disconnect Account", fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Dialog displaying user code and waiting for approval on GitHub.
 */
@Composable
private fun DeviceAuthorizationDialog(
    userCode: String,
    verificationUri: String,
    expiresIn: Int,
    statusText: String,
    errorMessage: String?,
    onOpenBrowser: () -> Unit,
    onCopyCode: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    var secondsRemaining by remember(userCode) { mutableIntStateOf(expiresIn) }

    LaunchedEffect(userCode) {
        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
        }
    }

    val minutes = secondsRemaining / 60
    val seconds = secondsRemaining % 60
    val formattedTime = "%d:%02d".format(minutes, seconds)

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with GitHub Octocat
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF24292F)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_github),
                        contentDescription = "GitHub",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Authorize on GitHub",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Copy the code below, then tap the button to authorize in your browser:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Large Monospace User Code Display Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCopyCode() }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = userCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Tap to Copy Code",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Expiration Timer
                Text(
                    text = "Code expires in $formattedTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Error Message if any
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusFailed,
                        textAlign = TextAlign.Center
                    )
                }

                // Primary Browser Action Button
                Button(
                    onClick = onOpenBrowser,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_github_browser_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open GitHub Verification Page",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Live Polling Indicator
                if (errorMessage.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Cancel or Retry Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("cancel_oauth_button")
                    ) {
                        Text("Cancel")
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        TextButton(onClick = onRetry) {
                            Text("Retry Authorization")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog to view or configure a custom GitHub OAuth App Client ID.
 */
@Composable
private fun ClientIdSettingsDialog(
    currentClientId: String,
    onSave: (String) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    var inputId by remember { mutableStateOf(currentClientId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "GitHub OAuth App Settings",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Configure your GitHub OAuth App Client ID for device flow authorization:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = inputId,
                    onValueChange = { inputId = it },
                    label = { Text("Client ID") },
                    placeholder = { Text(PreferenceStorage.DEFAULT_GITHUB_CLIENT_ID) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "To create your own free GitHub OAuth App:\n1. Visit github.com/settings/applications/new\n2. Set any name (e.g. My App Builder)\n3. Check 'Enable Device Flow'\n4. Copy the public Client ID here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(inputId) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onResetDefault) {
                    Text("Reset Default")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
