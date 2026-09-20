package com.example.ui.screens
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.BuildRecord
import com.example.data.model.ProviderConfig
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.StatusCompleted
import com.example.ui.theme.StatusFailed
import com.example.ui.theme.StatusInProgress
import com.example.ui.viewmodel.SettingsUiState
import com.example.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var selectedSection by remember { mutableStateOf<String?>(null) }
    var showAddCustomProviderDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var inspectingProvider by remember { mutableStateOf<ProviderConfig?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        Toast.makeText(context, if (granted) "Camera permission granted" else "Camera permission not granted", Toast.LENGTH_SHORT).show()
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
        Toast.makeText(context, if (granted) "Microphone permission granted" else "Microphone permission not granted", Toast.LENGTH_SHORT).show()
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotifPermission = granted
        Toast.makeText(context, if (granted) "Notification permission granted" else "Notification permission not granted", Toast.LENGTH_SHORT).show()
    }

    val allPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        hasNotifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        Toast.makeText(context, "Full WebView permissions updated!", Toast.LENGTH_SHORT).show()
    }


    // Section metadata for the hub menu
    data class SettingsSectionInfo(val id: String, val title: String, val subtitle: String, val icon: ImageVector)
    val sectionsList = listOf(
        SettingsSectionInfo("Permissions", "Permissions", "Camera, microphone, notifications", Icons.Default.Security),
        SettingsSectionInfo("Appearance", "Appearance", "Theme, fonts, colors", Icons.Default.Palette),
        SettingsSectionInfo("Editor", "Editor", "Code editor preferences", Icons.Default.Code),
        SettingsSectionInfo("AI & Models", "AI & Models", "Providers, API keys, models", Icons.Default.AutoAwesome),
        SettingsSectionInfo("Storage", "Storage", "Projects, files, disk usage", Icons.Default.Folder),
        SettingsSectionInfo("Build & Deploy", "Build & Deploy", "Build system and deployment", Icons.Default.Build),
        SettingsSectionInfo("Preview", "Preview", "Live preview and browser", Icons.Default.Computer),
        SettingsSectionInfo("Terminal", "Terminal", "Embedded terminal settings", Icons.Default.Terminal)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_kodrix_icon),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                                )
                            }
                        }
                        Text(
                            text = if (selectedSection != null) selectedSection!! else "Settings",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedSection != null) {
                                selectedSection = null
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("settings_back_button")
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
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedSection == null) {
                // Hub: show the menu of sections
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Choose a category",
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    sectionsList.forEach { section ->
                        Card(
                            onClick = { selectedSection = section.id },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_section_${section.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = section.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = section.title,
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = section.subtitle,
                                        fontFamily = InterFontFamily,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp).rotate(-90f)
                                )
                            }
                        }
                    }
                }
            } else {
                // Sub-screen: show only the selected section's content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {

                // CATEGORY: Permissions & WebView Browser
                if (selectedSection == "Permissions") {
                    CategorySection(
                        title = "Permissions & WebView Browser",
                        icon = Icons.Default.Security
                    ) {
                        // Master Action Card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Full-Fledged Browser Capabilities",
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Enable camera (hand gestures, MediaPipe, AR), microphone (WebRTC, audio synthesis), and background build alerts.",
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        allPermissionsLauncher.launch(perms.toTypedArray())
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("enable_all_permissions_btn")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Grant Full WebView Browser Permissions",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        // Permission 1: Camera & Webcam
                        PermissionToggleRow(
                            title = "Camera & Webcam",
                            subtitle = "Enables getUserMedia for MediaPipe hand tracking, gesture detection, and computer vision canvas websites",
                            isGranted = hasCameraPermission,
                            icon = Icons.Default.Videocam,
                            onToggle = { enable ->
                                if (enable) {
                                    cameraLauncher.launch(Manifest.permission.CAMERA)
                                } else {
                                    openAppSettings(context)
                                }
                            }
                        )

                        // Permission 2: Microphone & Audio
                        PermissionToggleRow(
                            title = "Microphone & Audio Input",
                            subtitle = "Enables audio capture for voice synthesis, audio visualizers, and WebRTC streaming in the browser",
                            isGranted = hasMicPermission,
                            icon = Icons.Default.Mic,
                            onToggle = { enable ->
                                if (enable) {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    openAppSettings(context)
                                }
                            }
                        )

                        // Permission 3: Build Notifications
                        PermissionToggleRow(
                            title = "Background Build Notifications",
                            subtitle = "Alerts you with sound and vibration when AI finishes coding while the app is in the background or recents",
                            isGranted = hasNotifPermission,
                            icon = Icons.Default.Notifications,
                            onToggle = { enable ->
                                if (enable) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    openAppSettings(context)
                                }
                            }
                        )

                        // Open System Settings Link
                        OutlinedButton(
                            onClick = { openAppSettings(context) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_system_settings_btn")
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open Android App Settings",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // CATEGORY 1: Appearance
                if (selectedSection == "Appearance") {
                    CategorySection(
                        title = "CATEGORY 1: Appearance",
                        icon = Icons.Default.Palette
                    ) {

                        // Theme: Segmented Button [Light / Dark / System]
                        SettingItemContainer(label = "Theme") {
                            SegmentedThemeToggle(
                                selectedMode = state.themeMode,
                                onSelect = { viewModel.setThemeMode(it) }
                            )
                        }

                        // Accent Color: Color Picker Chips [Peach / Blue / Purple / Green]
                        SettingItemContainer(label = "Accent Color") {
                            AccentColorPicker(
                                selectedColor = state.accentColor,
                                onSelectColor = { viewModel.setAccentColor(it) }
                            )
                        }

                        // Editor Font Size: Slider [12 - 24]
                        SettingItemContainer(
                            label = "Editor Font Size",
                            badge = "${state.editorFontSize} sp"
                        ) {
                            Slider(
                                value = state.editorFontSize.toFloat(),
                                onValueChange = { viewModel.setEditorFontSize(it.toInt()) },
                                valueRange = 12f..24f,
                                steps = 11,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("editor_font_size_slider")
                            )
                        }

                        // Font Family: Dropdown [JetBrains Mono / Fira Code / Inter]
                        SettingItemContainer(label = "Font Family") {
                            KodrixDropdown(
                                items = listOf("JetBrains Mono", "Fira Code", "Inter"),
                                selectedItem = state.editorFontFamily,
                                onSelect = { viewModel.setEditorFontFamily(it) },
                                modifier = Modifier.testTag("font_family_dropdown")
                            )
                        }

                        // Haptics: Switch
                        SettingSwitchRow(
                            title = "Haptics",
                            subtitle = "Tactile vibration feedback on button presses and actions",
                            checked = state.hapticsEnabled,
                            onCheckedChange = { viewModel.setHapticsEnabled(it) },
                            testTag = "haptics_switch"
                        )
                    }
                }

                // CATEGORY 2: Editor & Workspace
                if (selectedSection == "Editor") {
                    CategorySection(
                        title = "CATEGORY 2: Editor & Workspace",
                        icon = Icons.Default.Code
                    ) {
                        // Word Wrap: Switch
                        SettingSwitchRow(
                            title = "Word Wrap",
                            subtitle = "Wrap long lines to fit viewport width",
                            checked = state.wordWrap,
                            onCheckedChange = { viewModel.setWordWrap(it) },
                            testTag = "word_wrap_switch"
                        )

                        // Line Numbers: Switch
                        SettingSwitchRow(
                            title = "Line Numbers",
                            subtitle = "Display gutter line numbering in the code view",
                            checked = state.lineNumbers,
                            onCheckedChange = { viewModel.setLineNumbers(it) },
                            testTag = "line_numbers_switch"
                        )

                        // Minimap: Switch
                        SettingSwitchRow(
                            title = "Minimap",
                            subtitle = "Display visual code overview bar on right side",
                            checked = state.minimap,
                            onCheckedChange = { viewModel.setMinimap(it) },
                            testTag = "minimap_switch"
                        )

                        // Tab Size: Segmented Button [2 / 4]
                        SettingItemContainer(label = "Tab Size") {
                            KodrixSegmentedButton(
                                items = listOf("2", "4"),
                                selectedItem = state.tabSize.toString(),
                                onSelect = { viewModel.setTabSize(it.toIntOrNull() ?: 2) },
                                modifier = Modifier.testTag("tab_size_segmented_button")
                            )
                        }

                        // Auto Save: Switch
                        SettingSwitchRow(
                            title = "Auto Save",
                            subtitle = "Automatically save modifications to project disk",
                            checked = state.autoSave,
                            onCheckedChange = { viewModel.setAutoSave(it) },
                            testTag = "auto_save_switch"
                        )

                        // Auto Save Delay: Slider [1s / 3s / 5s]
                        AnimatedVisibility(visible = state.autoSave) {
                            SettingItemContainer(
                                label = "Auto Save Delay",
                                badge = "${state.autoSaveDelay}s"
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(1, 3, 5).forEach { delaySec ->
                                        val isSelected = state.autoSaveDelay == delaySec
                                        OutlinedButton(
                                            onClick = { viewModel.setAutoSaveDelay(delaySec) },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            ),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("${delaySec}s", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        // Keyboard Layout: Dropdown [Native / Vim]
                        SettingItemContainer(label = "Keyboard Layout") {
                            KodrixDropdown(
                                items = listOf("Native", "Vim"),
                                selectedItem = state.keyboardLayout,
                                onSelect = { viewModel.setKeyboardLayout(it) },
                                modifier = Modifier.testTag("keyboard_layout_dropdown")
                            )
                        }
                    }
                }

                // CATEGORY 3: AI & Models
                if (selectedSection == "AI & Models") {
                    CategorySection(
                        title = "CATEGORY 3: AI & Models",
                        icon = Icons.Default.AutoAwesome
                    ) {
                        // Default Provider: Segmented Button [Gemini / OpenAI / Claude / Groq / Compatible]
                        SettingItemContainer(label = "Default Provider") {
                            KodrixSegmentedButton(
                                items = listOf("Gemini", "OpenAI", "Claude", "Groq", "Compatible"),
                                selectedItem = state.defaultProvider,
                                onSelect = { viewModel.setDefaultProvider(it) },
                                modifier = Modifier.testTag("default_provider_segmented_button")
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Manage Providers",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        state.providers.forEach { provider ->
                            ProviderManagementCard(
                                provider = provider,
                                isVerifying = state.verifyingProviderId == provider.id,
                                isFetchingModels = state.fetchingModelsForProviderId == provider.id,
                                onToggleEnabled = { enabled -> viewModel.toggleProviderEnabled(provider.id, enabled) },
                                onToggleVision = { enabled -> viewModel.toggleProviderVision(provider.id, enabled) },
                                onUpdateApiKey = { key -> viewModel.updateProviderApiKey(provider.id, key) },
                                onUpdateModel = { model -> viewModel.updateProviderModel(provider.id, model) },
                                onUpdateBaseUrl = { url -> viewModel.updateProviderBaseUrl(provider.id, url) },
                                onUpdateThinking = { enabled, level -> viewModel.updateProviderThinking(provider.id, enabled, level) },
                                onAddCustomThinkingLevel = { level -> viewModel.addCustomThinkingLevel(provider.id, level) },
                                onInspectJson = { inspectingProvider = provider },
                                onDelete = { viewModel.deleteCustomProvider(provider.id) },
                                onVerify = { viewModel.verifyProvider(provider.id) },
                                onFetchModels = { viewModel.fetchProviderModels(provider.id) }
                            )
                        }

                        // Add Custom Provider Button
                        Button(
                            onClick = { showAddCustomProviderDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_custom_provider_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Custom Provider", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // CATEGORY 4: Projects & Storage
                if (selectedSection == "Storage") {
                    CategorySection(
                        title = "CATEGORY 4: Projects & Storage",
                        icon = Icons.Default.Folder
                    ) {
                        // Default Project Location: Text Field [/Kodrix/]
                        SettingItemContainer(label = "Default Project Location") {

                            OutlinedTextField(
                                value = state.defaultProjectLocation,
                                onValueChange = { viewModel.setDefaultProjectLocation(it) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                textStyle = TextStyle(fontFamily = InterFontFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("project_location_input")
                            )
                        }

                        // GitHub Integration: Connect Button + Status
                        GitHubIntegrationCard(
                            isConnected = state.isGithubConnected,
                            username = state.githubUsername,
                            isConnecting = state.isStartingOAuth,
                            onConnect = { viewModel.startDeviceOAuth() },
                            onDisconnect = { viewModel.disconnectGitHub() }
                        )

                        // Auto Commit on Build: Switch
                        SettingSwitchRow(
                            title = "Auto Commit on Build",
                            subtitle = "Automatically commit generated source tree to GitHub repo",
                            checked = state.autoCommitOnBuild,
                            onCheckedChange = { viewModel.setAutoCommitOnBuild(it) },
                            testTag = "auto_commit_switch"
                        )

                        // Export Project: Button [Export as .ZIP]
                        SettingItemContainer(label = "Export Project") {
                            Button(
                                onClick = {
                                    val file = viewModel.exportProjectAsZip()
                                    if (file != null) {
                                        Toast.makeText(context, "Exported: ${file.name}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("export_project_button")
                            ) {
                                Icon(imageVector = Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export as .ZIP", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Clear Cache: Button [Shows size]
                        SettingItemContainer(label = "Cache Storage") {
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearCache()
                                    Toast.makeText(context, "Storage cache cleared", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("clear_cache_button")
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = StatusFailed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clear Cache (${state.cacheSizeFormatted})", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // CATEGORY 5: Build & Deploy
                if (selectedSection == "Build & Deploy") {
                    CategorySection(
                        title = "CATEGORY 5: Build & Deploy",
                        icon = Icons.Default.Build
                    ) {
                        // Build on Save: Switch
                        SettingSwitchRow(
                            title = "Build on Save",
                            subtitle = "Trigger target compilation pipeline when files are saved",
                            checked = state.buildOnSave,
                            onCheckedChange = { viewModel.setBuildOnSave(it) },
                            testTag = "build_on_save_switch"
                        )

                        // Default Build Target: Chips [Web / Android APK / Design / Slides / Animation]
                        SettingItemContainer(label = "Default Build Target") {
                            KodrixChipGroup(
                                options = listOf("Web", "Android APK", "Design", "Slides", "Animation"),
                                selectedOption = state.defaultBuildTarget,
                                onSelect = { viewModel.setDefaultBuildTarget(it) },
                                modifier = Modifier.testTag("build_target_chips")
                            )
                        }

                        // Web Build Runtime: Chip [Local Termux - Default]
                        SettingItemContainer(label = "Web Build Runtime") {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusCompleted.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, StatusCompleted.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Terminal,
                                        contentDescription = null,
                                        tint = StatusCompleted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Local Termux (Default)",
                                        fontFamily = InterFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = StatusCompleted
                                    )
                                }
                            }
                        }

                        // Android Build Runtime: Chip [GitHub Actions - Default]
                        SettingItemContainer(label = "Android Build Runtime") {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusInProgress.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, StatusInProgress.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = StatusInProgress,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "GitHub Actions (Default)",
                                        fontFamily = InterFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = StatusInProgress
                                    )
                                }
                            }
                        }

                        // Auto-Generate Workflow File: Switch ON
                        SettingSwitchRow(
                            title = "Auto-Generate Workflow File",
                            subtitle = "Automatically inject Android CI/CD release workflow in .github/workflows",
                            checked = state.autoGenerateWorkflowFile,
                            onCheckedChange = { viewModel.setAutoGenerateWorkflowFile(it) },
                            testTag = "auto_generate_workflow_switch"
                        )

                        // View Build Logs: Button
                        Button(
                            onClick = { showLogsDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("view_build_logs_button")
                        ) {
                            Icon(imageVector = Icons.Default.Terminal, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Build Logs (${state.buildRecords.size} builds recorded)", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // CATEGORY 6: Preview & Debug Logs
                if (selectedSection == "Preview") {
                    CategorySection(
                        title = "CATEGORY 6: Preview & Debug Logs",
                        icon = Icons.Default.Computer
                    ) {
                        // Preview Mode: Segmented Button [Split View / Full Screen / External Browser]
                        SettingItemContainer(label = "Preview Mode") {
                            KodrixSegmentedButton(
                                items = listOf("Split View", "Full Screen", "External Browser"),
                                selectedItem = state.previewMode,
                                onSelect = { viewModel.setPreviewMode(it) },
                                modifier = Modifier.testTag("preview_mode_segmented_button")
                            )
                        }

                        // Auto-Refresh on Code Change: Switch
                        SettingSwitchRow(
                            title = "Auto-Refresh on Code Change",
                            subtitle = "Live reload preview webview whenever new code is saved",
                            checked = state.autoRefreshOnCodeChange,
                            onCheckedChange = { viewModel.setAutoRefreshOnCodeChange(it) },
                            testTag = "auto_refresh_switch"
                        )

                        // Device Preview: Chips [Mobile / Tablet / Desktop]
                        SettingItemContainer(label = "Device Preview") {
                            KodrixChipGroup(
                                options = listOf("Mobile", "Tablet", "Desktop"),
                                selectedOption = state.devicePreview,
                                onSelect = { viewModel.setDevicePreview(it) },
                                modifier = Modifier.testTag("device_preview_chips")
                            )
                        }

                        // Dev Server Port: Default 5173
                        SettingItemContainer(label = "Dev Server Port") {

                            OutlinedTextField(
                                value = state.devServerPort.toString(),
                                onValueChange = { it.toIntOrNull()?.let { p -> viewModel.setDevServerPort(p) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(fontFamily = InterFontFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dev_server_port_input")
                            )
                        }

                        // AI Auto-Fix on Error: Switch ON
                        SettingSwitchRow(
                            title = "AI Auto-Fix on Error",
                            subtitle = "Automatically diagnose runtime crash logs with AI and propose fixes",
                            checked = state.aiAutoFixOnError,
                            onCheckedChange = { viewModel.setAiAutoFixOnError(it) },
                            testTag = "ai_auto_fix_switch"
                        )

                        // Clear Logs on Reload: Switch
                        SettingSwitchRow(
                            title = "Clear Logs on Reload",
                            subtitle = "Flush preview console logs when webview is refreshed",
                            checked = state.clearLogsOnReload,
                            onCheckedChange = { viewModel.setClearLogsOnReload(it) },
                            testTag = "clear_logs_switch"
                        )
                    }
                }

                // CATEGORY 7: Terminal & Environment
                if (selectedSection == "Terminal") {
                    CategorySection(
                        title = "CATEGORY 7: Terminal & Environment",
                        icon = Icons.Default.Terminal
                    ) {
                        // Environment Type: Segmented [Local Termux / Cloud]
                        SettingItemContainer(label = "Environment Type") {
                            KodrixSegmentedButton(
                                items = listOf("Local Termux", "Cloud"),
                                selectedItem = state.environmentType,
                                onSelect = { viewModel.setEnvironmentType(it) },
                                modifier = Modifier.testTag("environment_type_segmented_button")
                            )
                        }

                        // Node Version: Dropdown [Bundled Node 20 / System Node]
                        SettingItemContainer(label = "Node Version") {
                            KodrixDropdown(
                                items = listOf("Bundled Node 20", "System Node"),
                                selectedItem = state.nodeVersion,
                                onSelect = { viewModel.setNodeVersion(it) },
                                modifier = Modifier.testTag("node_version_dropdown")
                            )
                        }

                        // Auto-Start Dev Server: Switch ON
                        SettingSwitchRow(
                            title = "Auto-Start Dev Server",
                            subtitle = "Automatically boot Vite/Next.js dev daemon upon project load",
                            checked = state.autoStartDevServer,
                            onCheckedChange = { viewModel.setAutoStartDevServer(it) },
                            testTag = "auto_start_dev_server_switch"
                        )

                        // Show Terminal: Switch ON/OFF
                        SettingSwitchRow(
                            title = "Show Terminal",
                            subtitle = "Render bottom interactive terminal console in workspace",
                            checked = state.showTerminal,
                            onCheckedChange = { viewModel.setShowTerminal(it) },
                            testTag = "show_terminal_switch"
                        )

                        // Locked Architecture Banner
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E1E1E)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFF9AD94),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "LOCKED PIPELINE ENVIRONMENTS",
                                        fontFamily = InterFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF9AD94)
                                    )
                                }
                                Text(
                                    text = "â€¢ Web = Local Termux Linux + Node + localhost WebView + auto logs to AI\nâ€¢ APK = GitHub Actions workflow auto-generated + push = APK artifact",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFFE0E0E0)
                                )
                            }
                        }
                    }
                }
                } // Close scrollable Column
            } // Close else block
        } // Close outer Column
    } // Close Scaffold

    // Modal: Add Custom Provider Dialog
    if (showAddCustomProviderDialog) {
        var selectedTab by remember { mutableIntStateOf(0) }

        // Tab 0: Manual fields
        var name by remember { mutableStateOf("") }
        var baseUrl by remember { mutableStateOf("") }
        var apiKey by remember { mutableStateOf("") }
        var modelName by remember { mutableStateOf("") }
        var providerType by remember { mutableStateOf("OpenAI Compatible") }
        var thinkingEnabled by remember { mutableStateOf(false) }
        var thinkingLevel by remember { mutableStateOf("medium") }
        var typeDropdownExpanded by remember { mutableStateOf(false) }
        val types = listOf("OpenAI Compatible", "Anthropic Compatible", "Gemini Compatible")

        // Tab 1: JSON import
        var jsonText by remember { mutableStateOf("") }
        var jsonError by remember { mutableStateOf("") }

        val jsonFilePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    jsonText = inputStream?.bufferedReader()?.readText() ?: ""
                    jsonError = ""
                } catch (e: Exception) {
                    jsonError = "Could not read file: ${e.localizedMessage}"
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showAddCustomProviderDialog = false },
            title = {
                Column {
                    Text(
                        text = "Add AI Provider",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Tab strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("Manual", "Import JSON").forEachIndexed { idx, label ->
                            val isTab = selectedTab == idx
                            Surface(
                                onClick = { selectedTab = idx },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isTab) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = if (isTab) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isTab) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            text = {
                if (selectedTab == 0) {
                    // Manual entry tab
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = providerType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Provider Type") },
                                trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                                textStyle = TextStyle(fontFamily = InterFontFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            androidx.compose.material3.Surface(
                                modifier = Modifier.matchParentSize().clickable { typeDropdownExpanded = true },
                                color = androidx.compose.ui.graphics.Color.Transparent
                            ) {}
                            DropdownMenu(
                                expanded = typeDropdownExpanded,
                                onDismissRequest = { typeDropdownExpanded = false }
                            ) {
                                types.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type) },
                                        onClick = { providerType = type; typeDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = name, onValueChange = { name = it },
                            label = { Text("Provider Name") }, placeholder = { Text("e.g. Together AI") },
                            textStyle = TextStyle(fontFamily = InterFontFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = baseUrl, onValueChange = { baseUrl = it },
                            label = { Text("Base URL") }, placeholder = { Text("https://api.together.xyz/v1") },
                            textStyle = TextStyle(fontFamily = InterFontFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = apiKey, onValueChange = { apiKey = it },
                            label = { Text("API Key") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            textStyle = TextStyle(fontFamily = InterFontFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = modelName, onValueChange = { modelName = it },
                            label = { Text("Model Name") }, placeholder = { Text("e.g. meta-llama/Llama-3.3-70b") },
                            textStyle = TextStyle(fontFamily = InterFontFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )

                        // Thinking Mode Option
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Enable Thinking Mode", fontFamily = InterFontFamily, fontSize = 13.sp)
                            }
                            Switch(
                                checked = thinkingEnabled,
                                onCheckedChange = { thinkingEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        if (thinkingEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Thinking Level:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                KodrixChipGroup(
                                    options = listOf("low", "medium", "high", "ultra", "adaptive"),
                                    selectedOption = thinkingLevel,
                                    onSelect = { thinkingLevel = it }
                                )
                            }
                        }
                    }
                } else {
                    // JSON import tab
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Paste a JSON provider object or array, or upload a .json file.",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Example hint
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "{ \"name\": \"MyProvider\", \"baseUrl\": \"https://api.x.ai/v1\", \"apiKey\": \"sk-...\", \"defaultModel\": \"grok-2\", \"thinkingEnabled\": true, \"thinkingLevel\": \"medium\" }",
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        OutlinedTextField(
                            value = jsonText,
                            onValueChange = { jsonText = it; jsonError = "" },
                            label = { Text("JSON") },
                            placeholder = { Text("Paste JSON here...") },
                            minLines = 4,
                            maxLines = 8,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = if (jsonError.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = if (jsonError.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (jsonError.isNotBlank()) {
                            Text(
                                text = jsonError,
                                fontFamily = InterFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        // Upload file button
                        OutlinedButton(
                            onClick = { jsonFilePicker.launch("application/json") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("upload_json_provider_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload .json File", fontFamily = InterFontFamily, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedTab == 0) {
                            if (baseUrl.isNotBlank()) {
                                viewModel.addCustomProvider(name, baseUrl, apiKey, modelName, providerType, thinkingEnabled, thinkingLevel)
                                showAddCustomProviderDialog = false
                                Toast.makeText(context, "Custom provider added", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (jsonText.isBlank()) {
                                jsonError = "JSON cannot be empty"
                            } else {
                                val result = viewModel.importProvidersFromJson(jsonText)
                                if (result.isSuccess) {
                                    showAddCustomProviderDialog = false
                                    Toast.makeText(context, "Imported ${result.getOrDefault(0)} provider(s)", Toast.LENGTH_SHORT).show()
                                } else {
                                    jsonError = "Invalid JSON: ${result.exceptionOrNull()?.localizedMessage?.take(80)}"
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (selectedTab == 0) "Add Provider" else "Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomProviderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal: Provider JSON Inspector & Customizer Dialog
    if (inspectingProvider != null) {
        val targetProvider = inspectingProvider!!
        var jsonText by remember(targetProvider.id) {
            mutableStateOf(viewModel.exportProviderToJson(targetProvider))
        }
        var jsonError by remember { mutableStateOf("") }
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        AlertDialog(
            onDismissRequest = { inspectingProvider = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "JSON: ${targetProvider.name}",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            val clip = ClipData.newPlainText("provider_config_json", jsonText)
                            clipboardManager.setPrimaryClip(clip)
                            Toast.makeText(context, "Provider JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy JSON",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Inspect and customize raw JSON fields, request payload overrides, thinking parameters, and custom models for ${targetProvider.name}:",
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = {
                            jsonText = it
                            jsonError = ""
                        },
                        minLines = 8,
                        maxLines = 16,
                        textStyle = TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = if (jsonError.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (jsonError.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (jsonError.isNotBlank()) {
                        Text(
                            text = jsonError,
                            fontFamily = InterFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = viewModel.updateProviderFromJson(targetProvider.id, jsonText)
                        if (result.isSuccess) {
                            Toast.makeText(context, "${targetProvider.name} JSON updated", Toast.LENGTH_SHORT).show()
                            inspectingProvider = null
                        } else {
                            jsonError = "Invalid JSON: ${result.exceptionOrNull()?.localizedMessage ?: "Parse error"}"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save & Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectingProvider = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal: Build Logs Dialog
    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = {
                Text(
                    text = "Recent Build Logs",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (state.buildRecords.isEmpty()) {
                    Text(
                        text = "No builds recorded yet. Build an app to see pipeline telemetry and logs.",
                        fontFamily = InterFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.buildRecords.forEach { record ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = record.appName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = record.status,
                                            fontSize = 11.sp,
                                            color = when (record.status.lowercase()) {
                                                "completed" -> StatusCompleted
                                                "failed" -> StatusFailed
                                                else -> StatusInProgress
                                            }
                                        )
                                    }
                                    Text(
                                        text = "${record.platform} â€¢ ${record.framework}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!record.logSummary.isNullOrBlank()) {
                                        Text(
                                            text = record.logSummary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLogsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Device Flow Authorization Dialog (if active)
    if (state.deviceFlow != null) {
        val flow = state.deviceFlow!!
        var secondsRemaining by remember(flow.userCode) { mutableIntStateOf(flow.expiresIn) }

        LaunchedEffect(flow.userCode) {
            while (secondsRemaining > 0) {
                kotlinx.coroutines.delay(1000)
                secondsRemaining--
            }
        }

        AlertDialog(
            onDismissRequest = { viewModel.cancelDeviceOAuth() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_github),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Text("Authorize GitHub OAuth", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Enter this one-time code on GitHub to link your account:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF9AD94).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFF9AD94)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = flow.userCode,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 4.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Code expires in ${secondsRemaining / 60}:${String.format(java.util.Locale.US, "%02d", secondsRemaining % 60)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("GitHub Code", flow.userCode))
                                Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copy Code")
                        }

                        Button(
                            onClick = {
                                val url = flow.verificationUriComplete ?: flow.verificationUri
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open GitHub")
                        }
                    }

                    if (flow.isPolling) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Waiting for authorization...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeviceOAuth() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------------------
// Subcomponents
// -----------------------------------------------------------------------------------------

@Composable
private fun CategorySection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text = title,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            content()
        }
    }
}

@Composable
private fun SettingItemContainer(
    label: String,
    badge: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = badge,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontFamily = InterFontFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun KodrixSegmentedButton(
    items: List<String>,
    selectedItem: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                val isSelected = selectedItem.equals(item, ignoreCase = true)
                Surface(
                    onClick = { onSelect(item) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier
                ) {
                    Text(
                        text = item,
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentColorPicker(
    selectedColor: String,
    onSelectColor: (String) -> Unit
) {
    val colorMap = listOf(
        "Peach" to Color(0xFFF9AD94),
        "Blue" to Color(0xFF3B82F6),
        "Purple" to Color(0xFF8B5CF6),
        "Green" to Color(0xFF10B981)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        colorMap.forEach { (name, color) ->
            val isSelected = selectedColor.equals(name, ignoreCase = true)
            Surface(
                onClick = { onSelectColor(name) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) color else MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .testTag("accent_${name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = name,
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun KodrixDropdown(
    items: List<String>,
    selectedItem: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedItem,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item,
                            fontFamily = InterFontFamily,
                            fontWeight = if (item == selectedItem) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                    trailingIcon = if (item == selectedItem) {
                        { Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KodrixChipGroup(
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = selectedOption.equals(option, ignoreCase = true)
            Surface(
                onClick = { onSelect(option) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = option,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ProviderManagementCard(
    provider: ProviderConfig,
    isVerifying: Boolean,
    isFetchingModels: Boolean = false,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleVision: (Boolean) -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateBaseUrl: (String) -> Unit,
    onUpdateThinking: (Boolean, String) -> Unit,
    onAddCustomThinkingLevel: (String) -> Unit,
    onInspectJson: () -> Unit,
    onDelete: () -> Unit,
    onVerify: () -> Unit,
    onFetchModels: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var apiKeyText by remember(provider.apiKey) { mutableStateOf(provider.apiKey) }
    var baseUrlText by remember(provider.baseUrl) { mutableStateOf(provider.baseUrl) }
    var showApiKey by remember { mutableStateOf(false) }

    val isConnected = provider.apiKey.isNotBlank() || provider.isValid

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${provider.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Header Row: Status, Name, JSON Icon, Switch, Expand
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { expanded = !expanded },
                                onLongPress = { onInspectJson() }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) StatusCompleted else Color(0xFF9E9E9E))
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = provider.name,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (provider.thinkingEnabled) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "ðŸ§  ${provider.thinkingLevel.uppercase()}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isConnected) "Connected (Hold to inspect JSON)" else "Not Connected (Hold to inspect JSON)",
                            fontSize = 11.sp,
                            color = if (isConnected) StatusCompleted else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Inspect / Edit JSON Button
                    IconButton(
                        onClick = onInspectJson,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Inspect / Edit JSON",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = provider.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("switch_${provider.id}")
                    )
                }
            }

            // Expandable Detail Section: API Key, Models, Thinking, Base URL
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    // Base URL (if Compatible/Custom)
                    if (provider.id.contains("custom") || provider.id.contains("compatible")) {
                        OutlinedTextField(
                            value = baseUrlText,
                            onValueChange = {
                                baseUrlText = it
                                onUpdateBaseUrl(it)
                            },
                            label = { Text("Base URL", fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = InterFontFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // API Key Field
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = {
                            apiKeyText = it
                            onUpdateApiKey(it)
                        },
                        label = { Text("API Key", fontSize = 11.sp) },
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = InterFontFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("apikey_${provider.id}")
                    )

                    // Models selector & Custom Model ID
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Models & Fetch
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Model: ${provider.defaultModel.ifBlank { "Not set" }}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedButton(
                                    onClick = onFetchModels,
                                    enabled = !isFetchingModels && provider.apiKey.isNotBlank(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    if (isFetchingModels) {
                                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Fetching...", fontSize = 11.sp)
                                    } else {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Fetch Models", fontSize = 11.sp)
                                    }
                                }
                            }

                            if (provider.availableModels.isNotEmpty()) {
                                Text(
                                    text = "${provider.availableModels.size} models available — tap to select:",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                KodrixChipGroup(
                                    options = provider.availableModels,
                                    selectedOption = provider.defaultModel,
                                    onSelect = onUpdateModel
                                )
                            }
                        }

                        var customModelInput by remember(provider.id) { mutableStateOf("") }
                        var showCustomModelInput by remember { mutableStateOf(false) }

                        if (showCustomModelInput) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customModelInput,
                                    onValueChange = { customModelInput = it },
                                    label = { Text("Custom Model ID", fontSize = 11.sp) },
                                    placeholder = { Text("e.g. gpt-4.5-preview, claude-3-7-sonnet", fontSize = 11.sp) },
                                    singleLine = true,
                                    textStyle = TextStyle(fontFamily = InterFontFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        cursorColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        if (customModelInput.isNotBlank()) {
                                            onUpdateModel(customModelInput.trim())
                                            showCustomModelInput = false
                                            customModelInput = ""
                                        }
                                    },
                                    enabled = customModelInput.isNotBlank(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text("Apply", fontSize = 11.sp)
                                }
                            }
                        } else {
                            TextButton(
                                onClick = { showCustomModelInput = true },
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Add Custom Model ID", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Thinking & Reasoning Configuration Section
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (provider.thinkingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Thinking / Reasoning Mode",
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (provider.thinkingEnabled) "Enabled (${provider.thinkingLevel})" else "Disabled",
                                            fontSize = 10.sp,
                                            color = if (provider.thinkingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Switch(
                                    checked = provider.thinkingEnabled,
                                    onCheckedChange = { onUpdateThinking(it, provider.thinkingLevel) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            if (provider.thinkingEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Thinking Level / Method:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    KodrixChipGroup(
                                        options = provider.supportedThinkingLevels,
                                        selectedOption = provider.thinkingLevel,
                                        onSelect = { onUpdateThinking(true, it) }
                                    )

                                    var customThinkingInput by remember(provider.id) { mutableStateOf("") }
                                    var showCustomThinkingInput by remember { mutableStateOf(false) }

                                    if (showCustomThinkingInput) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = customThinkingInput,
                                                onValueChange = { customThinkingInput = it },
                                                label = { Text("Custom Thinking Level / Tokens", fontSize = 10.sp) },
                                                placeholder = { Text("e.g. 4096, deep_reasoning, turbo", fontSize = 10.sp) },
                                                singleLine = true,
                                                textStyle = TextStyle(fontFamily = InterFontFamily, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Button(
                                                onClick = {
                                                    if (customThinkingInput.isNotBlank()) {
                                                        onAddCustomThinkingLevel(customThinkingInput.trim())
                                                        showCustomThinkingInput = false
                                                        customThinkingInput = ""
                                                    }
                                                },
                                                enabled = customThinkingInput.isNotBlank(),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text("Add", fontSize = 11.sp)
                                            }
                                        }
                                    } else {
                                        TextButton(
                                            onClick = { showCustomThinkingInput = true },
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("+ Add Custom Thinking Level", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Row: Inspect JSON shortcut, Delete (if custom), Verify button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onInspectJson,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Inspect / Edit JSON", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        // Vision Support Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = if (provider.supportsVision) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = "Vision Support",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (provider.supportsVision) "Image attachments enabled" else "Disabled",
                                        fontSize = 10.sp,
                                        color = if (provider.supportsVision) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = provider.supportsVision,
                                onCheckedChange = { onToggleVision(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (provider.id.startsWith("custom_")) {
                                OutlinedButton(
                                    onClick = onDelete,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusFailed),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(13.dp), tint = StatusFailed)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", fontSize = 11.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = onVerify,
                                enabled = !isVerifying,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (isVerifying) {
                                    CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Verifying...", fontSize = 11.sp)
                                } else {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Verify Key", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubIntegrationCard(
    isConnected: Boolean,
    username: String,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag("github_integration_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Column {
                    Text(
                        text = "GitHub Integration",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (isConnected) "Connected as @$username" else "Not connected",
                        fontSize = 11.sp,
                        color = if (isConnected) StatusCompleted else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusFailed)
                ) {
                    Text("Disconnect", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = !isConnecting,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("github_connect_button")
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentedThemeToggle(
    selectedMode: String,
    onSelect: (String) -> Unit
) {
    val options = listOf("Light", "Dark", "System")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = option.equals(selectedMode, ignoreCase = true)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionToggleRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    icon: ImageVector,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isGranted) StatusCompleted.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (isGranted) "Granted" else "Not Granted",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = if (isGranted) StatusCompleted else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isGranted,
                onCheckedChange = { onToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}


