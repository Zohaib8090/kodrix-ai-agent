package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.example.data.local.ProjectChatSession
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
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

                // Option 3: Terminal (Linux / Termux Environment)
                NavigationBarItem(
                    selected = state.activeTab == WorkspaceBottomNav.TERMINAL,
                    onClick = { viewModel.selectTab(WorkspaceBottomNav.TERMINAL) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Terminal"
                        )
                    },
                    label = {
                        Text(
                            text = "Terminal",
                            fontFamily = InterFontFamily,
                            fontWeight = if (state.activeTab == WorkspaceBottomNav.TERMINAL) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_terminal")
                )

                // Option 4: Preview
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
                            chatSessions = state.chatSessions,
                            activeChatSession = state.activeChatSession,
                            isAiRefining = state.isAiRefining,
                            isGenerating = state.isGenerating,
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
                            },
                            onStopResponse = {
                                viewModel.stopAiResponse()
                            },
                            onRunInTerminal = { command ->
                                viewModel.executeTerminalCommand(command)
                                viewModel.selectTab(WorkspaceBottomNav.TERMINAL)
                            },
                            onCreateNewChat = { title ->
                                viewModel.createNewChatSession(title)
                            },
                            onSwitchChatSession = { session ->
                                viewModel.switchChatSession(session)
                            },
                            onRenameChatSession = { session, newTitle ->
                                viewModel.renameChatSession(session, newTitle)
                            },
                            onDeleteChatSession = { session ->
                                viewModel.deleteChatSession(session)
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

                    WorkspaceBottomNav.TERMINAL -> {
                        TerminalTab(
                            projectName = state.projectName,
                            terminalLogs = state.terminalLogs,
                            isRunning = state.isTerminalRunning,
                            onExecuteCommand = { command ->
                                viewModel.executeTerminalCommand(command)
                            },
                            onClearTerminal = {
                                viewModel.clearTerminal()
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatTab(
    messages: List<WorkspaceChatMessage>,
    chatSessions: List<ProjectChatSession> = emptyList(),
    activeChatSession: ProjectChatSession? = null,
    isAiRefining: Boolean,
    isGenerating: Boolean = false,
    isOnboarding: Boolean = false,
    isOnboardingThinking: Boolean = false,
    activeProvider: String,
    onSendMessage: (String) -> Unit,
    onRetryPrompt: (String) -> Unit,
    onSendOnboardingReply: (String) -> Unit = {},
    onStopResponse: () -> Unit = {},
    onRunInTerminal: (String) -> Unit = {},
    onCreateNewChat: (String?) -> Unit = {},
    onSwitchChatSession: (ProjectChatSession) -> Unit = {},
    onRenameChatSession: (ProjectChatSession, String) -> Unit = { _, _ -> },
    onDeleteChatSession: (ProjectChatSession) -> Unit = {}
) {
    val context = LocalContext.current
    var promptInput by remember { mutableStateOf("") }
    var selectedPromptActionMessage by remember { mutableStateOf<WorkspaceChatMessage?>(null) }
    var showSessionsSheet by remember { mutableStateOf(false) }
    var sessionToRename by remember { mutableStateOf<ProjectChatSession?>(null) }
    var sessionToDelete by remember { mutableStateOf<ProjectChatSession?>(null) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var newChatTitleInput by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        attachedImageUri = uri
    }

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
        // Multi-Chat Sessions Header Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Active Chat Session Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showSessionsSheet = true }
                        .testTag("chat_session_selector")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activeChatSession?.title ?: "Main Chat",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Switch Session",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // New Chat Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = LandingPeach,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showNewChatDialog = true }
                        .testTag("btn_new_chat")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Chat",
                            tint = Color(0xFF1E1E1E),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New Chat",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF1E1E1E)
                        )
                    }
                }
            }
        }
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

            val isAiActive = isAiRefining || isOnboardingThinking || isGenerating
            if (isAiActive) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
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
                                    text = if (isOnboardingThinking) "Kodrix is analyzing requirements..." else if (isGenerating) "AI is generating code files..." else "$activeProvider is coding your updates...",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onStopResponse() }
                                        .padding(1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Stop",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
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
                        val isAiActiveNow = isAiRefining || isOnboardingThinking || isGenerating
                        if (!isAiActiveNow) {
                            val cleanPrompt = suggestion.removePrefix("✨ ").removePrefix("📱 ").removePrefix("🎨 ").removePrefix("⚡ ")
                            if (isOnboarding) {
                                onSendOnboardingReply(cleanPrompt)
                            } else {
                                onSendMessage(cleanPrompt)
                            }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Attachment preview strip
                if (attachedImageUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Box {
                                AsyncImage(
                                    model = attachedImageUri,
                                    contentDescription = "Attachment",
                                    modifier = Modifier.size(56.dp),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { attachedImageUri = null },
                                    modifier = Modifier
                                        .size(18.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove attachment",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Image attached",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment button
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach image",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        placeholder = {
                            Text(
                                if (isOnboarding) "Reply to Kodrix..." else "Ask AI to code changes or add features...",
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

                    val isAiActiveCurrent = isAiRefining || isOnboardingThinking || isGenerating
                    if (isAiActiveCurrent) {
                        IconButton(
                            onClick = onStopResponse,
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(4.dp, CircleShape, spotColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .testTag("ai_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Response",
                                tint = MaterialTheme.colorScheme.onError
                            )
                        }
                    } else {
                        val isSendEnabled = promptInput.isNotBlank() || attachedImageUri != null
                        IconButton(
                            onClick = {
                                if (promptInput.isNotBlank() || attachedImageUri != null) {
                                    val text = promptInput.trim()
                                    promptInput = ""
                                    attachedImageUri = null
                                    if (isOnboarding) {
                                        onSendOnboardingReply(text)
                                    } else {
                                        onSendMessage(text)
                                    }
                                }
                            },
                            enabled = isSendEnabled,
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(
                                    elevation = if (isSendEnabled) 4.dp else 1.dp,
                                    shape = CircleShape,
                                    spotColor = if (isSendEnabled) LandingPeach.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                                .clip(CircleShape)
                                .background(
                                    if (isSendEnabled) LandingPeach
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSendEnabled) Color(0xFFFFE5DD) else MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
                                    shape = CircleShape
                                )
                                .testTag("ai_refine_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (isSendEnabled) Color(0xFF1E1E1E)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            )
                        }
                    }
                } // end inner Row
            } // end Column
        } // end Surface
    } // end outer Column

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

    // New Chat Dialog
    if (showNewChatDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewChatDialog = false
                newChatTitleInput = ""
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AddComment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "New Chat Thread",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Create a separate chat thread for a new feature, bug fix, or question.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newChatTitleInput,
                        onValueChange = { newChatTitleInput = it },
                        label = { Text("Chat Title (optional)") },
                        placeholder = { Text("e.g. Navigation Refactor") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateNewChat(newChatTitleInput.trim().ifBlank { null })
                        showNewChatDialog = false
                        newChatTitleInput = ""
                    }
                ) {
                    Text("Create Chat")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewChatDialog = false
                        newChatTitleInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Chat Dialog
    sessionToRename?.let { session ->
        var renameInput by remember { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rename Chat",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Chat Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            onRenameChatSession(session, renameInput.trim())
                        }
                        sessionToRename = null
                    },
                    enabled = renameInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Chat Confirmation Dialog
    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete Chat Thread?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${session.title}' and all its messages? This action cannot be undone.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteChatSession(session)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // All Chat Sessions Bottom Sheet
    if (showSessionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSessionsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                // Sheet Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Project Chat Threads",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${chatSessions.size} conversation(s) in this project",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // + New Chat button inside sheet
                    FilledTonalButton(
                        onClick = {
                            showSessionsSheet = false
                            showNewChatDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // List of Sessions
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatSessions, key = { it.id }) { session ->
                        val isActive = session.id == activeChatSession?.id
                        val formattedDate = try {
                            val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                            sdf.format(Date(session.updatedAt))
                        } catch (_: Exception) { "" }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                1.5.dp,
                                if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSwitchChatSession(session)
                                    showSessionsSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isActive) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = session.title,
                                                fontFamily = InterFontFamily,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isActive) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                ) {
                                                    Text(
                                                        text = "ACTIVE",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (formattedDate.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Last active $formattedDate",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Rename Action
                                    IconButton(
                                        onClick = {
                                            sessionToRename = session
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Delete Action
                                    IconButton(
                                        onClick = {
                                            sessionToDelete = session
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
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

/**
 * Tab 3: Interactive Linux / Termux Terminal Environment with curl/wget/node/npm support.
 */
@Composable
private fun TerminalTab(
    projectName: String,
    terminalLogs: List<String>,
    isRunning: Boolean,
    onExecuteCommand: (String) -> Unit,
    onClearTerminal: () -> Unit
) {
    var commandInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to the latest log line
    LaunchedEffect(terminalLogs.size) {
        if (terminalLogs.isNotEmpty()) {
            listState.animateScrollToItem(terminalLogs.size - 1)
        }
    }

    val quickCommands = listOf(
        "🚀 Install Full Termux" to "setup-termux",
        "⚡ Install Node Engine" to "setup-node",
        "pkg install nodejs" to "pkg install -y nodejs git",
        "ls -la" to "ls -la",
        "npm install" to "npm install",
        "npm run dev" to "npm run dev",
        "curl -O" to "curl -O ",
        "node -v" to "node -v",
        "git status" to "git status"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(12.dp)
    ) {
        // 1. Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Terminal",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = "my_projects/$projectName",
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Running...",
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(
                    onClick = onClearTerminal,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Terminal",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. Quick Command Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickCommands.forEach { (label, cmd) ->
                val isSetup = cmd == "setup-node"
                val bgColor = if (isSetup) Color(0xFF1E3A5F) else Color(0xFF1E293B)
                val textColor = if (isSetup) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                val borderColor = if (isSetup) Color(0xFF0284C7) else Color(0xFF334155)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = bgColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.clickable {
                        if (cmd == "curl -O ") {
                            commandInput = "curl -O "
                        } else {
                            onExecuteCommand(cmd)
                        }
                    }
                ) {
                    Text(
                        text = label,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = if (isSetup) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 3. Main Console Output Box
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = Color(0xFF030712),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF1F2937))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                items(terminalLogs) { line ->
                    val textColor = when {
                        line.startsWith("$ ") -> Color(0xFF4ADE80) // Green command
                        line.startsWith(">> ") -> Color(0xFF38BDF8) // Cyan progress
                        line.startsWith("✓ ") -> Color(0xFFFACC15) // Yellow success
                        line.contains("error", ignoreCase = true) || line.contains("failed", ignoreCase = true) || line.contains("fatal", ignoreCase = true) -> Color(0xFFF87171) // Red error
                        line.startsWith("[Exit") -> Color(0xFFA78BFA) // Purple exit code
                        else -> Color(0xFFCBD5E1) // Light gray normal text
                    }

                    Text(
                        text = line,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = textColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Command Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                textStyle = TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color.White
                ),
                leadingIcon = {
                    Text(
                        text = "$",
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF4ADE80)
                    )
                },
                placeholder = {
                    Text(
                        text = "curl -O https://... or npm install",
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (commandInput.isNotBlank()) {
                                val toRun = commandInput.trim()
                                commandInput = ""
                                onExecuteCommand(toRun)
                            }
                        },
                        enabled = commandInput.isNotBlank(),
                        modifier = Modifier.testTag("terminal_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Run Command",
                            tint = if (commandInput.isNotBlank()) Color(0xFF4ADE80) else Color(0xFF475569),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF111827),
                    unfocusedContainerColor = Color(0xFF111827),
                    focusedBorderColor = Color(0xFF4ADE80),
                    unfocusedBorderColor = Color(0xFF374151),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("terminal_command_input")
            )
        }
    }
}
