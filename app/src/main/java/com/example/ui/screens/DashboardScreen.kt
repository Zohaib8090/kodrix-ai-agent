package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.R
import com.example.data.model.PlatformType
import com.example.data.model.WebFramework
import com.example.ui.theme.*
import com.example.ui.viewmodel.AiChatMessage
import com.example.ui.viewmodel.ChatSender
import com.example.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

private data class CategoryOption(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val platform: PlatformType? = null,
    val defaultPromptHint: String
)

private val CATEGORIES = listOf(
    CategoryOption(
        id = "website",
        label = "Website",
        icon = Icons.Outlined.Language,
        platform = PlatformType.WEB,
        defaultPromptHint = "Make a modern landing page for a SaaS platform"
    ),
    CategoryOption(
        id = "mobile",
        label = "Mobile",
        icon = Icons.Outlined.Smartphone,
        platform = PlatformType.ANDROID,
        defaultPromptHint = "Build an Android mobile app with Jetpack Compose"
    ),
    CategoryOption(
        id = "design",
        label = "Design",
        icon = Icons.Outlined.Palette,
        platform = PlatformType.WEB,
        defaultPromptHint = "Design a sleek creative studio portfolio with dark mode"
    ),
    CategoryOption(
        id = "slides",
        label = "Slides",
        icon = Icons.Outlined.Slideshow,
        platform = PlatformType.WEB,
        defaultPromptHint = "Build an interactive startup pitch deck with slide transitions"
    ),
    CategoryOption(
        id = "animation",
        label = "Animation",
        icon = Icons.Outlined.AutoAwesome,
        platform = PlatformType.WEB,
        defaultPromptHint = "Create an interactive animated particle experience with spring physics"
    )
)

private val EXAMPLE_PROMPT_SETS = listOf(
    listOf("Retail sales dashboard", "Startup pitch deck", "App signup demo"),
    listOf("E-commerce product landing", "Fitness tracker dashboard", "AI chat assistant"),
    listOf("Real-time team kanban", "Crypto wallet portfolio", "Waitlist with invite links")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToTracker: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processMessage by viewModel.processMessage.collectAsState()
    val processError by viewModel.processError.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var promptText by remember { mutableStateOf(state.prompt) }
    var selectedCategoryId by remember { mutableStateOf("website") }
    var promptSetIndex by remember { mutableIntStateOf(0) }
    var refreshRotation by remember { mutableFloatStateOf(0f) }
    var showPlusMenuSheet by remember { mutableStateOf(false) }
    var showChatSheet by remember { mutableStateOf(false) }

    // Clone GitHub Dialog state
    var showCloneDialog by remember { mutableStateOf(false) }
    var githubRepoInput by remember { mutableStateOf("") }
    var githubBranchInput by remember { mutableStateOf("") }
    var githubTokenInput by remember { mutableStateOf("") }

    // ZIP file picker launcher for uploading full project
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importZipProject(it, context) { recordId ->
                onNavigateToTracker(recordId)
            }
        }
    }

    // File attachment state for attaching documents/images to prompt
    var attachedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            attachedFiles = (attachedFiles + uris).distinct()
        }
    }

    // Processing Overlay Dialog
    if (isProcessing) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                modifier = Modifier.widthIn(min = 280.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 3.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = processMessage ?: "Processing project...",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Extracting files and setting up your workspace",
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Process Error Dialog
    if (processError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearProcessError() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Operation Failed", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    text = processError ?: "An unexpected error occurred.",
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearProcessError() },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Clone from GitHub Dialog
    if (showCloneDialog) {
        AlertDialog(
            onDismissRequest = { showCloneDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Clone from GitHub", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Enter a GitHub repository URL or slug to clone and extract into your workspace.",
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = githubRepoInput,
                        onValueChange = { githubRepoInput = it },
                        label = { Text("Repository (e.g. owner/repo or https://...)") },
                        placeholder = { Text("e.g. facebook/react or torvalds/linux") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_github_repo_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = githubBranchInput,
                        onValueChange = { githubBranchInput = it },
                        label = { Text("Branch (Optional, default main/master)") },
                        placeholder = { Text("main") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = githubTokenInput,
                        onValueChange = { githubTokenInput = it },
                        label = { Text("GitHub Token (Optional for private repos)") },
                        placeholder = { Text("ghp_...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (githubRepoInput.isNotBlank()) {
                            val repo = githubRepoInput.trim()
                            val branch = githubBranchInput.trim().ifEmpty { null }
                            val token = githubTokenInput.trim().ifEmpty { null }
                            showCloneDialog = false
                            viewModel.cloneGitHubRepo(repo, branch, token) { recordId ->
                                onNavigateToTracker(recordId)
                            }
                        }
                    },
                    enabled = githubRepoInput.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clone & Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloneDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Synchronize if external change
    LaunchedEffect(state.prompt) {
        if (state.prompt != promptText) {
            promptText = state.prompt
        }
    }

    // Subtle fade-in-up animation (200ms ease-out)
    var animationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animationTriggered = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (animationTriggered) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )
    val contentOffsetY by animateDpAsState(
        targetValue = if (animationTriggered) 0.dp else 20.dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "contentOffsetY"
    )

    val currentExamplePills = EXAMPLE_PROMPT_SETS[promptSetIndex % EXAMPLE_PROMPT_SETS.size]

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier.testTag("projects_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "Projects",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            val isMobile = maxWidth < 600.dp
            val headingFontSize = if (isMobile) 36.sp else 64.sp
            val headingLineHeight = if (isMobile) 42.sp else 72.sp

            // Vertically and horizontally centered main layout
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = if (isMobile) 20.dp else 40.dp, vertical = 40.dp)
                        .offset(y = contentOffsetY)
                        .alpha(contentAlpha),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 2. Large Heading Text: "What will you build?"
                    Text(
                        text = "What will you build?",
                        fontFamily = InterFontFamily,
                        fontSize = headingFontSize,
                        lineHeight = headingLineHeight,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).em,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("landing_heading")
                    )

                    // 3. Subtext: "You can always make changes later."
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "You can always make changes later.",
                        fontFamily = InterFontFamily,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("landing_subtext")
                    )

                    // 4. Main Input Bar
                    Spacer(modifier = Modifier.height(32.dp))
                    MainInputBar(
                        promptText = promptText,
                        onPromptChange = {
                            promptText = it
                            viewModel.onPromptChanged(it)
                        },
                        // "+" opens options sheet for Upload Project ZIP, Clone GitHub, or Attach Files
                        onOpenOptions = { showPlusMenuSheet = true },
                        onSubmit = {
                            if (promptText.isNotBlank() && !state.isLaunchingBuild) {
                                viewModel.onPromptChanged(promptText)
                                viewModel.startBuild(onNavigateToTracker)
                            }
                        },
                        isLaunching = state.isLaunchingBuild,
                        isMobile = isMobile,
                        attachedFiles = attachedFiles,
                        onRemoveFile = { uri -> attachedFiles = attachedFiles - uri }
                    )

                    // 5. Category Filters Row
                    Spacer(modifier = Modifier.height(28.dp))
                    CategoryFiltersRow(
                        categories = CATEGORIES,
                        selectedCategoryId = selectedCategoryId,
                        onSelectCategory = { category ->
                            selectedCategoryId = category.id
                            category.platform?.let { viewModel.onPlatformChanged(it) }
                            if (promptText.isBlank()) {
                                promptText = category.defaultPromptHint
                                viewModel.onPromptChanged(category.defaultPromptHint)
                            }
                        },
                        isMobile = isMobile
                    )

                    // 6. Bottom: "Try an example prompt" with refresh icon
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                refreshRotation += 360f
                                promptSetIndex++
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("refresh_example_prompts_button")
                    ) {
                        Text(
                            text = "Try an example prompt",
                            fontFamily = InterFontFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val animatedAngle by animateFloatAsState(
                            targetValue = refreshRotation,
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                            label = "refreshAngle"
                        )
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh example prompts",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(animatedAngle)
                        )
                    }

                    // 7. Example Pills: 3 outlined pills
                    Spacer(modifier = Modifier.height(14.dp))
                    ExamplePillsRow(
                        prompts = currentExamplePills,
                        onSelectPrompt = { selectedPrompt ->
                            promptText = selectedPrompt
                            viewModel.onPromptChanged(selectedPrompt)
                            if (selectedPrompt.equals("AI chat assistant", ignoreCase = true)) {
                                showChatSheet = true
                            }
                        },
                        isMobile = isMobile
                    )
                }
            }
        }
    }

    // Plus Menu Bottom Sheet
    if (showPlusMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlusMenuSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp, top = 8.dp)
            ) {
                Text(
                    text = "Project & Context Options",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Option 1: Upload Project ZIP
                ListItem(
                    headlineContent = { Text("Upload Project (.ZIP)", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                    supportingContent = { Text("Import existing source files directly into workspace", fontFamily = InterFontFamily, fontSize = 11.sp) },
                    leadingContent = {
                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showPlusMenuSheet = false
                            zipPickerLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        }
                )

                // Option 2: Clone from GitHub
                ListItem(
                    headlineContent = { Text("Clone from GitHub", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                    supportingContent = { Text("Download and open any GitHub repository in workspace", fontFamily = InterFontFamily, fontSize = 11.sp) },
                    leadingContent = {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showPlusMenuSheet = false
                            showCloneDialog = true
                        }
                )

                // Option 3: Attach Files to Current Prompt
                ListItem(
                    headlineContent = { Text("Attach Files to Prompt", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                    supportingContent = { Text("Attach reference mockups, specs, or images to prompt", fontFamily = InterFontFamily, fontSize = 11.sp) },
                    leadingContent = {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showPlusMenuSheet = false
                            filePicker.launch("*/*")
                        }
                )
            }
        }
    }

    // AI App Architect Chat Bottom Sheet
    if (showChatSheet) {
        AiChatBottomSheet(
            viewModel = viewModel,
            onApplyPrompt = { prompt, category ->
                promptText = prompt
                viewModel.onPromptChanged(prompt)
                category?.let { selectedCategoryId = it }
                showChatSheet = false
                viewModel.startBuild(onNavigateToTracker)
            },
            onDismiss = { showChatSheet = false }
        )
    }
}

/**
 * Main pill-shaped input bar container.
 * Max width 720px, height ~110px, background #FFFFFF, border 1px solid #EAE5E0.
 * On focus: border #F9AD94 and soft focus glow ring.
 */
@Composable
private fun MainInputBar(
    promptText: String,
    onPromptChange: (String) -> Unit,
    onOpenOptions: () -> Unit,
    onSubmit: () -> Unit,
    isLaunching: Boolean,
    isMobile: Boolean,
    attachedFiles: List<Uri> = emptyList(),
    onRemoveFile: (Uri) -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outline,
        animationSpec = tween(180),
        label = "borderColor"
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (isFocused) 6.dp else 2.dp,
        animationSpec = tween(180),
        label = "shadowElevation"
    )

    val inputBarWidth = if (isMobile) 0.92f else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth(inputBarWidth)
            .widthIn(max = 720.dp)
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(20.dp),
                spotColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color(0x10000000),
                ambientColor = Color(0x08000000)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top: text input with placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
            ) {
                BasicTextField(
                    value = promptText,
                    onValueChange = onPromptChange,
                    textStyle = TextStyle(
                        fontFamily = InterFontFamily,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused }
                        .testTag("main_prompt_input"),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (promptText.isEmpty()) {
                                Text(
                                    text = "Make a landing page for...",
                                    fontFamily = InterFontFamily,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // Attached files strip — shown when files are attached via the "+" button
            if (attachedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    attachedFiles.forEach { uri ->
                        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = fileName.take(20) + if (fileName.length > 20) "…" else "",
                                    fontFamily = InterFontFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove file",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clickable { onRemoveFile(uri) }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom bar: "+" on left, peach submit button on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bottom-left: small "+" icon button (20px, #1E1E1E)
                IconButton(
                    onClick = onOpenOptions,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("add_options_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Advanced build options",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Bottom-right: circular peach/orange submit button (44x44px, #F9AD94)
                val isButtonEnabled = promptText.isNotBlank() && !isLaunching

                val submitInteractionSource = remember { MutableInteractionSource() }
                val isPressed by submitInteractionSource.collectIsPressedAsState()
                val isHovered by submitInteractionSource.collectIsHoveredAsState()

                val buttonScale by animateFloatAsState(
                    targetValue = when {
                        isPressed -> 0.90f
                        isHovered -> 1.08f
                        isButtonEnabled -> 1.02f
                        else -> 1f
                    },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "submitScale"
                )

                // Highlighted button styling: In dark theme, ensures high visibility, luminous glow, and crisp contrast
                val buttonBg = when {
                    isButtonEnabled && (isHovered || isPressed) -> LandingPeachHover
                    isButtonEnabled -> LandingPeach
                    isFocused || isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                }

                val buttonBorder = when {
                    isButtonEnabled -> BorderStroke(1.5.dp, Color(0xFFFFE5DD))
                    isFocused || isHovered -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f))
                }

                val arrowIconTint = when {
                    isButtonEnabled -> Color(0xFF1E1E1E)
                    isFocused || isHovered -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                }

                val buttonElevation by animateDpAsState(
                    targetValue = when {
                        isButtonEnabled && isHovered -> 8.dp
                        isButtonEnabled -> 4.dp
                        isFocused || isHovered -> 3.dp
                        else -> 1.dp
                    },
                    label = "submitElevation"
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(buttonScale)
                        .shadow(
                            elevation = buttonElevation,
                            shape = CircleShape,
                            spotColor = if (isButtonEnabled) LandingPeach.copy(alpha = 0.65f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            ambientColor = Color(0x20000000)
                        )
                        .clip(CircleShape)
                        .background(buttonBg)
                        .border(buttonBorder, CircleShape)
                        .clickable(
                            enabled = isButtonEnabled,
                            interactionSource = submitInteractionSource,
                            indication = ripple(bounded = true, color = if (isButtonEnabled) Color.White else MaterialTheme.colorScheme.primary),
                            onClick = onSubmit
                        )
                        .testTag("submit_peach_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLaunching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = if (isButtonEnabled) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Build App",
                            tint = arrowIconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 5 icons in a row with labels: Website, Mobile, Design, Slides, Animation.
 * Each: 56x56px rounded square (16px radius), background #F2EEE9, icon centered, stroke icon style.
 * Label below: 14px, color #5A5A5A.
 * Active: #EAE5E0. Hover/Press: #E8E2DC, scale 1.05.
 * Responsive with horizontal scroll and scroll arrows.
 */
@Composable
private fun CategoryFiltersRow(
    categories: List<CategoryOption>,
    selectedCategoryId: String,
    onSelectCategory: (CategoryOption) -> Unit,
    isMobile: Boolean
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Left scroll arrow on mobile when scrollable
        if (isMobile && scrollState.value > 0) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo((scrollState.value - 120).coerceAtLeast(0))
                    }
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Scroll left",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            modifier = if (isMobile) {
                Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(scrollState)
            } else {
                Modifier.wrapContentWidth()
            },
            horizontalArrangement = Arrangement.spacedBy(if (isMobile) 16.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEach { category ->
                val isSelected = category.id == selectedCategoryId
                CategoryItem(
                    item = category,
                    isSelected = isSelected,
                    onClick = { onSelectCategory(category) }
                )
            }
        }

        // Right scroll arrow on mobile when scrollable
        if (isMobile && scrollState.value < scrollState.maxValue) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(
                            (scrollState.value + 120).coerceAtMost(scrollState.maxValue)
                        )
                    }
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Scroll right",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryItem(
    item: CategoryOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed || isHovered) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "catScale"
    )

    val bgColor = when {
        isHovered || isPressed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        isSelected -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("category_${item.id}")
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(scale)
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (isSelected) MaterialTheme.colorScheme.onSurface else Color(0xFF424242),
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.label,
            fontFamily = InterFontFamily,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 3 outlined pills: "Retail sales dashboard", "Startup pitch deck", "App signup demo"
 * Background transparent, border #EAE5E0, border-radius 8px, 14px text.
 */
@Composable
private fun ExamplePillsRow(
    prompts: List<String>,
    onSelectPrompt: (String) -> Unit,
    isMobile: Boolean
) {
    if (isMobile) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            prompts.forEach { prompt ->
                ExamplePillItem(prompt = prompt, onClick = { onSelectPrompt(prompt) })
            }
        }
    } else {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .widthIn(max = 720.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            prompts.forEach { prompt ->
                ExamplePillItem(prompt = prompt, onClick = { onSelectPrompt(prompt) })
            }
        }
    }
}

@Composable
private fun ExamplePillItem(
    prompt: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val borderColor = if (isHovered || isPressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outline
    val bgColor = if (isHovered) MaterialTheme.colorScheme.surface else Color.Transparent

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = MaterialTheme.colorScheme.primaryContainer),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("example_pill_${prompt.take(8).replace(" ", "_")}"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = prompt,
            fontFamily = InterFontFamily,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * Advanced Build Settings Bottom Sheet accessible via "+" icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedOptionsBottomSheet(
    state: com.example.ui.viewmodel.DashboardUiState,
    viewModel: DashboardViewModel,
    onOpenAiChat: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Build Settings & AI Configuration",
                    fontFamily = InterFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            // AI Architect Chat shortcut
            Surface(
                onClick = onOpenAiChat,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Brainstorm with AI Architect",
                            fontFamily = InterFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Chat interactively to refine user flows, screens & features",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Target Platform Picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Target Platform",
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlatformType.values().forEach { platform ->
                        val isSelected = state.platform == platform
                        OutlinedButton(
                            onClick = { viewModel.onPlatformChanged(platform) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                            ),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = platform.displayName,
                                fontFamily = InterFontFamily,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Web Framework picker if Web selected
            if (state.platform == PlatformType.WEB) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Web Framework",
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WebFramework.values().forEach { framework ->
                            val isSelected = state.webFramework == framework
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onWebFrameworkChanged(framework) },
                                label = {
                                    Text(
                                        text = framework.displayName,
                                        fontFamily = InterFontFamily,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }

            // AI Model Provider Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "AI Code Generator Provider",
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("gemini", "claude", "openrouter", "deepseek", "ollama").forEach { pId ->
                        val isSelected = state.selectedCodegenProviderId == pId
                        val pName = when (pId) {
                            "gemini" -> "Gemini 2.5"
                            "claude" -> "Claude 3.5"
                            "openrouter" -> "OpenRouter"
                            "deepseek" -> "DeepSeek R1"
                            else -> "Ollama Local"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onCodegenProviderChanged(pId) },
                            label = {
                                Text(
                                    text = pName,
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            // Feature Presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enabled Capabilities",
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val allFeatures = listOf(
                    "Local Room SQLite Persistence",
                    "Biometric Fingerprint/Face Auth",
                    "Dark/Light Adaptive Palette",
                    "Offline-first Sync Queue",
                    "Vico Charting Analytics"
                )
                allFeatures.forEach { feature ->
                    val isChecked = state.enabledFeatures.contains(feature)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleFeature(feature, !isChecked) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { viewModel.toggleFeature(feature, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primaryContainer,
                                checkmarkColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = feature,
                            fontFamily = InterFontFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Text(
                    text = "Save & Apply Settings",
                    fontFamily = InterFontFamily,
                    color = MaterialTheme.colorScheme.surface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Interactive AI App Architect Chat Sheet.
 * Allows users to converse with an AI Architect to brainstorm features, user flows, and generate prompt blueprints.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatBottomSheet(
    viewModel: DashboardViewModel,
    onApplyPrompt: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isGenerating by viewModel.isChatGenerating.collectAsState()
    val usableProviders by viewModel.usableProviders.collectAsState()
    val chatProviderId by viewModel.chatProviderId.collectAsState()
    var inputMessage by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.outline,
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
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
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AI App Architect",
                            fontFamily = InterFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Chat to brainstorm & craft your blueprint",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Provider selector chips — only shown when >1 provider has a key
            if (usableProviders.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    usableProviders.forEach { provider ->
                        val isSelected = provider.id == chatProviderId
                        Surface(
                            onClick = { viewModel.selectChatProvider(provider.id) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected)
                                androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primaryContainer)
                            else
                                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.testTag("chat_provider_${provider.id}")
                        ) {
                            Text(
                                text = provider.name,
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }

            // Chat History
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.sender == ChatSender.USER) {
                        // User message bubble
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    fontFamily = InterFontFamily,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    } else {
                        // AI Architect message bubble
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                shadowElevation = 1.dp,
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = msg.text,
                                        fontFamily = InterFontFamily,
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (!msg.suggestedPrompt.isNullOrBlank()) {
                                        Button(
                                            onClick = { onApplyPrompt(msg.suggestedPrompt, msg.suggestedCategory) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("apply_prompt_button")
                                        ) {
                                            Text(
                                                text = "Apply to App Builder",
                                                fontFamily = InterFontFamily,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isGenerating) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "AI Architect is formulating architecture...",
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Message Composer Input
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = inputMessage,
                        onValueChange = { inputMessage = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_chat_input"),
                        textStyle = TextStyle(
                            fontFamily = InterFontFamily,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            if (inputMessage.isEmpty()) {
                                Text(
                                    text = "Ask about features, UI layouts, platforms...",
                                    fontFamily = InterFontFamily,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            innerTextField()
                        }
                    )

                    val isChatSendEnabled = inputMessage.isNotBlank()
                    IconButton(
                        onClick = {
                            if (isChatSendEnabled) {
                                val text = inputMessage
                                inputMessage = ""
                                viewModel.sendChatMessage(text)
                            }
                        },
                        enabled = isChatSendEnabled,
                        modifier = Modifier
                            .size(36.dp)
                            .shadow(
                                elevation = if (isChatSendEnabled) 3.dp else 0.dp,
                                shape = CircleShape,
                                spotColor = LandingPeach.copy(alpha = 0.5f)
                            )
                            .clip(CircleShape)
                            .background(
                                if (isChatSendEnabled) LandingPeach else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isChatSendEnabled) Color(0xFFFFDBCF) else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                shape = CircleShape
                            )
                            .testTag("ai_chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (isChatSendEnabled) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

