package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ProviderConfig
import com.example.data.model.SourceFile
import com.example.data.services.ProjectFileNode
import com.example.ui.common.InAppWebPreview
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LandingPeach
import com.example.ui.theme.LandingPeachHover
import com.example.ui.viewmodel.ProjectWorkspaceViewModel
import com.example.ui.viewmodel.WorkspaceBottomNav
import com.example.ui.viewmodel.WorkspaceChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectWorkspaceScreen(
    projectId: String,
    viewModel: ProjectWorkspaceViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    LaunchedEffect(state.notification) {
        state.notification?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearNotification()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.projectName.ifEmpty { "Project Workspace" },
                                fontFamily = InterFontFamily,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = state.platform.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "📁 ${state.mainFolderPath} • AI: ${state.activeProviderName}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("workspace_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Provider selector dropdown if multiple providers are available
                    var providerMenuExpanded by remember { mutableStateOf(false) }
                    if (state.availableProviders.isNotEmpty()) {
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clickable { providerMenuExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI Provider",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = state.activeProviderName,
                                        fontFamily = InterFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = providerMenuExpanded,
                                onDismissRequest = { providerMenuExpanded = false }
                            ) {
                                state.availableProviders.forEach { provider ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = provider.name,
                                                    fontFamily = InterFontFamily,
                                                    fontWeight = if (provider.id == state.activeProviderId) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (provider.id == state.activeProviderId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (provider.id == state.activeProviderId) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectProvider(provider.id)
                                            providerMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (state.activeTab == WorkspaceBottomNav.CODE && state.hasUnsavedChanges) {
                        IconButton(
                            onClick = { viewModel.saveCurrentFile() },
                            modifier = Modifier.testTag("save_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save File",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("workspace_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("workspace_bottom_nav")
            ) {
                // Option 1: AI Chat
                NavigationBarItem(
                    selected = state.activeTab == WorkspaceBottomNav.AI_CHAT,
                    onClick = { viewModel.selectTab(WorkspaceBottomNav.AI_CHAT) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "AI Chat"
                        )
                    },
                    label = {
                        Text(
                            text = "AI Chat",
                            fontFamily = InterFontFamily,
                            fontWeight = if (state.activeTab == WorkspaceBottomNav.AI_CHAT) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_ai_chat")
                )

                // Option 2: Code
                NavigationBarItem(
                    selected = state.activeTab == WorkspaceBottomNav.CODE,
                    onClick = { viewModel.selectTab(WorkspaceBottomNav.CODE) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Code"
                        )
                    },
                    label = {
                        Text(
                            text = "Code",
                            fontFamily = InterFontFamily,
                            fontWeight = if (state.activeTab == WorkspaceBottomNav.CODE) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_code")
                )

                // Option 3: Preview
                NavigationBarItem(
                    selected = state.activeTab == WorkspaceBottomNav.PREVIEW,
                    onClick = { viewModel.selectTab(WorkspaceBottomNav.PREVIEW) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Preview"
                        )
                    },
                    label = {
                        Text(
                            text = "Preview",
                            fontFamily = InterFontFamily,
                            fontWeight = if (state.activeTab == WorkspaceBottomNav.PREVIEW) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_preview")
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Generating Active State Banner (shown when building)
            if (state.isGenerating) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Coding in Progress",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = state.statusText,
                                fontFamily = InterFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Main Content depending on the selected bottom tab
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (state.activeTab) {
                    WorkspaceBottomNav.AI_CHAT -> {
                        AiChatTab(
                            messages = state.chatMessages,
                            isAiRefining = state.isAiRefining,
                            isOnboarding = state.isOnboarding,
                            isOnboardingThinking = state.isOnboardingThinking,
                            activeProvider = state.activeProviderName,
                            onSendMessage = { instruction ->
                                viewModel.refineWithAi(instruction)
                            },
                            onRetryPrompt = { prompt ->
                                viewModel.retryPrompt(prompt)
                            },
                            onSendOnboardingReply = { reply ->
                                viewModel.sendOnboardingReply(reply)
                            }
                        )
                    }

                    WorkspaceBottomNav.CODE -> {
                        ProjectDirectoryCodeTab(
                            mainFolderPath = state.mainFolderPath,
                            files = state.files,
                            directoryTree = state.directoryTree,
                            selectedFile = state.selectedFile,
                            editedContent = state.editedContent,
                            hasUnsavedChanges = state.hasUnsavedChanges,
                            onSelectFile = { viewModel.selectFile(it) },
                            onContentChanged = { viewModel.updateEditedContent(it) },
                            onSaveFile = { viewModel.saveCurrentFile() },
                            onCreateFile = { path, content -> viewModel.createNewFile(path, content) },
                            onCreateFolder = { path -> viewModel.createNewFolder(path) },
                            onDeleteFile = { path -> viewModel.deleteFile(path) },
                            onCopyContent = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Source Code", state.editedContent))
                                Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    WorkspaceBottomNav.PREVIEW -> {
                        NativeWebPreviewTab(
                            previewHtml = state.previewHtml,
                            previewUrl = state.previewUrl,
                            previewBaseUrl = state.previewBaseUrl,
                            isGenerating = state.isGenerating,
                            isWeb = state.platform.equals("WEB", ignoreCase = true),
                            onOpenBrowser = { viewModel.openInBrowser(context) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 1: AI Chat Interface
 */
@Composable
private fun AiChatTab(
    messages: List<WorkspaceChatMessage>,
    isAiRefining: Boolean,
    isOnboarding: Boolean = false,
    isOnboardingThinking: Boolean = false,
    activeProvider: String,
    onSendMessage: (String) -> Unit,
    onRetryPrompt: (String) -> Unit,
    onSendOnboardingReply: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var promptInput by remember { mutableStateOf("") }
    var selectedPromptActionMessage by remember { mutableStateOf<WorkspaceChatMessage?>(null) }

    fun copyTextToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    val promptSuggestions = listOf(
        "✨ Add a dark mode toggle",
        "📱 Make responsive for mobile",
        "🎨 Improve colors & typography",
        "⚡ Add interactive animations"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.sender == "USER"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .widthIn(max = 340.dp)
                            .pointerInput(msg.id) {
                                detectTapGestures(
                                    onLongPress = {
                                        selectedPromptActionMessage = msg
                                    }
                                )
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Header: Icon + Sender Name + Quick Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isUser) Icons.Default.Person else Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isUser) "You" else activeProvider,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                        else MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Quick Menu / Hold indicator
                                IconButton(
                                    onClick = { selectedPromptActionMessage = msg },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Message Content
                            Text(
                                text = msg.message,
                                fontFamily = InterFontFamily,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Inline Interactive Action Buttons (hidden during onboarding)
                            if (!isOnboarding) {
                            if (isUser) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Copy Prompt Button
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                                        modifier = Modifier
                                            .clickable { copyTextToClipboard("Prompt", msg.message) }
                                            .padding(2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Prompt",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Copy",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // 2. Edit Prompt Button
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                                        modifier = Modifier
                                            .clickable {
                                                promptInput = msg.message
                                                Toast.makeText(context, "Loaded prompt to edit box", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Prompt",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Edit",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // 3. Retry Prompt Button
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                                        modifier = Modifier
                                            .clickable { onRetryPrompt(msg.message) }
                                            .padding(2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Retry Prompt",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Retry",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            } else {
                                // AI Message: Copy Output individually
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .clickable { copyTextToClipboard("AI Output", msg.message) }
                                            .padding(2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Output",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Copy Output",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }

            if (isAiRefining) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "$activeProvider is coding your updates...",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Suggestions row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            promptSuggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = {
                        if (!isAiRefining) {
                            onSendMessage(suggestion.removePrefix("✨ ").removePrefix("📱 ").removePrefix("🎨 ").removePrefix("⚡ "))
                        }
                    },
                    label = { Text(suggestion, fontSize = 11.sp) }
                )
            }
        }

        // Bottom Chat Input Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = {
                        Text(
                            "Ask AI to code changes or add features...",
                            fontFamily = InterFontFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_refine_input"),
                    textStyle = TextStyle(
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.width(8.dp))
                val isRefineSendEnabled = !isAiRefining && promptInput.isNotBlank()
                IconButton(
                    onClick = {
                        if (promptInput.isNotBlank()) {
                            val text = promptInput.trim()
                            promptInput = ""
                            onSendMessage(text)
                        }
                    },
                    enabled = isRefineSendEnabled,
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(
                            elevation = if (isRefineSendEnabled) 4.dp else 1.dp,
                            shape = CircleShape,
                            spotColor = if (isRefineSendEnabled) LandingPeach.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        .clip(CircleShape)
                        .background(
                            if (isRefineSendEnabled) LandingPeach
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isRefineSendEnabled) Color(0xFFFFE5DD) else MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
                            shape = CircleShape
                        )
                        .testTag("ai_refine_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (isRefineSendEnabled) Color(0xFF1E1E1E)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }

    // Modal Action Sheet for Selected Prompt (on hold / tap options)
    selectedPromptActionMessage?.let { selectedMsg ->
        val isUserMsg = selectedMsg.sender == "USER"
        AlertDialog(
            onDismissRequest = { selectedPromptActionMessage = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isUserMsg) Icons.Default.Person else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isUserMsg) "User Prompt Options" else "AI Output Options",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedMsg.message,
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isUserMsg) {
                        // Option 1: Copy Prompt
                        OutlinedButton(
                            onClick = {
                                copyTextToClipboard("User Prompt", selectedMsg.message)
                                selectedPromptActionMessage = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Prompt")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Option 2: Edit Prompt
                        OutlinedButton(
                            onClick = {
                                promptInput = selectedMsg.message
                                selectedPromptActionMessage = null
                                Toast.makeText(context, "Prompt loaded into edit box", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit Prompt")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Option 3: Retry Prompt
                        Button(
                            onClick = {
                                selectedPromptActionMessage = null
                                onRetryPrompt(selectedMsg.message)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Prompt")
                        }
                    } else {
                        // AI Output Copy
                        Button(
                            onClick = {
                                copyTextToClipboard("AI Output", selectedMsg.message)
                                selectedPromptActionMessage = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Complete Output")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedPromptActionMessage = null }) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Tab 2: Code & Project Directory Explorer
 */
@Composable
private fun ProjectDirectoryCodeTab(
    mainFolderPath: String,
    files: List<SourceFile>,
    directoryTree: ProjectFileNode?,
    selectedFile: SourceFile?,
    editedContent: String,
    hasUnsavedChanges: Boolean,
    onSelectFile: (SourceFile) -> Unit,
    onContentChanged: (String) -> Unit,
    onSaveFile: () -> Unit,
    onCreateFile: (String, String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onCopyContent: () -> Unit
) {
    var viewMode by remember { mutableStateOf("EDITOR") } // "TREE" or "EDITOR"
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    if (showNewFileDialog) {
        NewFileDialog(
            onDismiss = { showNewFileDialog = false },
            onCreate = { path ->
                onCreateFile(path, "")
                showNewFileDialog = false
            }
        )
    }

    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onCreate = { path ->
                onCreateFolder(path)
                showNewFolderDialog = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Project Directory Path Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = mainFolderPath,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = { viewMode = if (viewMode == "TREE") "EDITOR" else "TREE" },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = if (viewMode == "TREE") Icons.Default.Code else Icons.Default.AccountTree,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (viewMode == "TREE") "Editor" else "Tree", fontSize = 11.sp)
                    }

                    IconButton(
                        onClick = { showNewFileDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = "Add File", modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = { showNewFolderDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Add Folder", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (viewMode == "TREE") {
            // Directory Tree Hierarchy View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = "PROJECT DIRECTORY STRUCTURE",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (directoryTree != null) {
                    DirectoryTreeNodeView(
                        node = directoryTree,
                        selectedPath = selectedFile?.path,
                        level = 0,
                        onSelectFile = { relPath ->
                            val target = files.firstOrNull { it.path == relPath }
                            if (target != null) {
                                onSelectFile(target)
                                viewMode = "EDITOR"
                            }
                        },
                        onDelete = onDeleteFile
                    )
                } else {
                    // Fallback to flat list of files
                    files.forEach { file ->
                        FileRowItem(
                            file = file,
                            isSelected = file.path == selectedFile?.path,
                            onSelect = {
                                onSelectFile(file)
                                viewMode = "EDITOR"
                            },
                            onDelete = { onDeleteFile(file.path) }
                        )
                    }
                }
            }
        } else {
            // Code Editor & File Tabs View
            if (files.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No files created in this project directory.",
                        fontFamily = InterFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Horizontal Tabs of Files in Project
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    files.forEach { file ->
                        val isSelected = file.path == selectedFile?.path
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectFile(file) },
                            label = {
                                Text(
                                    text = file.path.substringAfterLast("/"),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                val icon = when {
                                    file.path.endsWith(".html") -> Icons.Default.Html
                                    file.path.endsWith(".css") -> Icons.Default.Style
                                    file.path.endsWith(".js") || file.path.endsWith(".ts") -> Icons.Default.Javascript
                                    file.path.endsWith(".json") -> Icons.Default.DataObject
                                    else -> Icons.Default.Code
                                }
                                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }

                // Editor Toolbar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedFile?.path ?: "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (hasUnsavedChanges) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = onCopyContent,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            }

                            if (hasUnsavedChanges) {
                                Button(
                                    onClick = onSaveFile,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Save", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Code Editor Text Field
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = onContentChanged,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .testTag("code_editor_field"),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    ),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

/**
 * Tab 3: Live Native Web Preview
 */
@Composable
private fun NativeWebPreviewTab(
    previewHtml: String?,
    previewUrl: String,
    previewBaseUrl: String?,
    isGenerating: Boolean,
    isWeb: Boolean,
    onOpenBrowser: () -> Unit
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var viewportMode by remember { mutableStateOf("FULL") } // "MOBILE", "TABLET", "FULL"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Preview Header Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isWeb) "Native Web Preview" else "Native Canvas Preview",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Viewport Toggle
                    IconButton(
                        onClick = {
                            viewportMode = when (viewportMode) {
                                "FULL" -> "MOBILE"
                                "MOBILE" -> "TABLET"
                                else -> "FULL"
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        val icon = when (viewportMode) {
                            "MOBILE" -> Icons.Default.Smartphone
                            "TABLET" -> Icons.Default.Tablet
                            else -> Icons.Default.Fullscreen
                        }
                        Icon(icon, contentDescription = "Viewport Mode", modifier = Modifier.size(16.dp))
                    }

                    // Reload Button
                    IconButton(
                        onClick = { refreshKey++ },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload Preview", modifier = Modifier.size(16.dp))
                    }

                    if (isWeb) {
                        IconButton(
                            onClick = onOpenBrowser,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "External Browser", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Native In-App Web Preview Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val previewModifier = when (viewportMode) {
                "MOBILE" -> Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                "TABLET" -> Modifier
                    .width(600.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                else -> Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            }

            key(refreshKey) {
                InAppWebPreview(
                    htmlContent = previewHtml,
                    targetUrl = if (previewHtml.isNullOrBlank()) previewUrl else null,
                    baseUrl = previewBaseUrl,
                    modifier = previewModifier.background(Color.White)
                )
            }

            if (isGenerating) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "AI is writing website code...",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Native live preview will load automatically",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Directory Tree Node View (Recursive)
 */
@Composable
private fun DirectoryTreeNodeView(
    node: ProjectFileNode,
    selectedPath: String?,
    level: Int,
    onSelectFile: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    if (node.isDirectory) {
        if (node.relativePath.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp)
                    .padding(start = (level * 16).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = node.name,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (isExpanded || node.relativePath.isEmpty()) {
            node.children.forEach { child ->
                DirectoryTreeNodeView(
                    node = child,
                    selectedPath = selectedPath,
                    level = if (node.relativePath.isEmpty()) 0 else level + 1,
                    onSelectFile = onSelectFile,
                    onDelete = onDelete
                )
            }
        }
    } else {
        val isSelected = node.relativePath == selectedPath
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else Color.Transparent
                )
                .clickable { onSelectFile(node.relativePath) }
                .padding(vertical = 5.dp, horizontal = 4.dp)
                .padding(start = (level * 16).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                val icon = when {
                    node.name.endsWith(".html") -> Icons.Default.Html
                    node.name.endsWith(".css") -> Icons.Default.Style
                    node.name.endsWith(".js") || node.name.endsWith(".ts") -> Icons.Default.Javascript
                    node.name.endsWith(".json") -> Icons.Default.DataObject
                    else -> Icons.Default.InsertDriveFile
                }
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = node.name,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${node.lineCount}L",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            IconButton(
                onClick = { onDelete(node.relativePath) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun FileRowItem(
    file: SourceFile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.path,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${file.content.lines().size} lines",
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NewFileDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var filePath by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New File", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter relative file path (e.g. index.html, css/style.css):", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = filePath,
                    onValueChange = { filePath = it },
                    placeholder = { Text("path/to/filename.ext") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (filePath.isNotBlank()) onCreate(filePath.trim()) },
                enabled = filePath.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NewFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var folderPath by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Folder", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter folder path (e.g. assets, js, components):", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = folderPath,
                    onValueChange = { folderPath = it },
                    placeholder = { Text("folder_name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (folderPath.isNotBlank()) onCreate(folderPath.trim()) },
                enabled = folderPath.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
