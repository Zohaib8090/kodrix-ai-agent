package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferenceStorage
import com.example.ui.viewmodel.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsViewModelTest {

    private lateinit var context: Application
    private lateinit var prefs: PreferenceStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear shared preferences
        context.getSharedPreferences("nocode_app_builder_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = PreferenceStorage(context)
    }

    @Test
    fun `test Category 1 Appearance defaults`() {
        val viewModel = SettingsViewModel(context)
        val state = viewModel.state.value

        assertEquals("System", state.themeMode)
        assertEquals("Peach", state.accentColor)
        assertEquals(14, state.editorFontSize)
        assertEquals("Inter", state.editorFontFamily)
        assertTrue(state.hapticsEnabled)

        // Test updating appearance
        viewModel.setThemeMode("Dark")
        viewModel.setAccentColor("Blue")
        viewModel.setEditorFontSize(18)
        viewModel.setEditorFontFamily("JetBrains Mono")
        viewModel.setHapticsEnabled(false)

        assertEquals("Dark", viewModel.state.value.themeMode)
        assertEquals("Blue", viewModel.state.value.accentColor)
        assertEquals(18, viewModel.state.value.editorFontSize)
        assertEquals("JetBrains Mono", viewModel.state.value.editorFontFamily)
        assertFalse(viewModel.state.value.hapticsEnabled)
    }

    @Test
    fun `test Category 2 Editor and Workspace defaults`() {
        val viewModel = SettingsViewModel(context)
        val state = viewModel.state.value

        assertTrue(state.wordWrap)
        assertTrue(state.lineNumbers)
        assertFalse(state.minimap)
        assertEquals(2, state.tabSize)
        assertTrue(state.autoSave)
        assertEquals(3, state.autoSaveDelay)
        assertEquals("Native", state.keyboardLayout)

        viewModel.setTabSize(4)
        viewModel.setAutoSaveDelay(5)
        viewModel.setKeyboardLayout("Vim")

        assertEquals(4, viewModel.state.value.tabSize)
        assertEquals(5, viewModel.state.value.autoSaveDelay)
        assertEquals("Vim", viewModel.state.value.keyboardLayout)
    }

    @Test
    fun `test Category 3 AI and Models defaults and provider toggle`() {
        val viewModel = SettingsViewModel(context)
        val state = viewModel.state.value

        assertEquals("Gemini", state.defaultProvider)
        assertTrue(state.providers.isNotEmpty())

        val gemini = state.providers.find { it.id == "gemini" }
        assertTrue(gemini != null)
        assertTrue(gemini!!.availableModels.isNotEmpty())

        viewModel.toggleProviderEnabled("gemini", false)
        val updatedGemini = viewModel.state.value.providers.find { it.id == "gemini" }
        assertFalse(updatedGemini!!.isEnabled)
    }

    @Test
    fun `test Category 4 Projects and Storage defaults`() {
        val viewModel = SettingsViewModel(context)
        val state = viewModel.state.value

        assertEquals("/Kodrix/", state.defaultProjectLocation)
        assertTrue(state.autoCommitOnBuild)

        viewModel.setDefaultProjectLocation("/Projects/Kodrix/")
        assertEquals("/Projects/Kodrix/", viewModel.state.value.defaultProjectLocation)
    }

    @Test
    fun `test Category 5 Build and Deploy defaults`() {
        val viewModel = SettingsViewModel(context)
        val state = viewModel.state.value

        assertFalse(state.buildOnSave)
        assertEquals("Web", state.defaultBuildTarget)
        assertEquals("Local Termux", state.webBuildRuntime)
        assertEquals("GitHub Actions", state.androidBuildRuntime)
        assertTrue(state.autoGenerateWorkflowFile)
    }

    @Test
    fun `test Category 6 Preview and Debug Logs defaults`() {
        val viewModel = SettingsViewModel(context)
        val state = viewModel.state.value

        assertEquals("Split View", state.previewMode)
        assertTrue(state.autoRefreshOnCodeChange)
        assertEquals("Mobile", state.devicePreview)
        assertEquals(5173, state.devServerPort)
        assertTrue(state.aiAutoFixOnError)
        assertFalse(state.clearLogsOnReload)
    }

    @Test
    fun `test Category 7 Terminal and Environment defaults`() {
        val viewModel = SettingsViewModel(context)
        val state = viewModel.state.value

        assertEquals("Local Termux", state.environmentType)
        assertEquals("Bundled Node 20", state.nodeVersion)
        assertTrue(state.autoStartDevServer)
        assertFalse(state.showTerminal)
    }
}
