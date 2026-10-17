package com.example.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.services.LinuxSetupError
import com.example.data.services.LinuxSetupPhase
import com.example.data.services.LinuxSetupState
import com.example.ui.screens.ChangeReviewSheet
import com.example.ui.screens.LinuxSetupPrompt
import com.example.ui.screens.LinuxSetupSheet
import com.example.ui.screens.PendingChangesBanner
import com.example.ui.terminal.AnsiTerminalOutput
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PendingChanges
import com.example.ui.viewmodel.PendingFileChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class NewWorkspaceUiTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `terminal renders ansi text without raw escape codes`() {
        rule.setContent {
            MyApplicationTheme(darkTheme = true) {
                AnsiTerminalOutput(lines = listOf("\u001B[31mred text\u001B[0m", "plain text"))
            }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("red text")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("plain text").assertExists()
        assertTrue(rule.onAllNodes(hasText("[31m", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `install prompt calls back`() {
        var installed = false
        rule.setContent { MyApplicationTheme { LinuxSetupPrompt(onInstall = { installed = true }, onDismiss = {}) } }
        rule.onNodeWithText("Install").performClick()
        assertTrue(installed)
    }

    @Test
    fun `banner shows count and is clickable`() {
        var clicks = 0
        rule.setContent { MyApplicationTheme { PendingChangesBanner(fileCount = 3, onReview = { clicks++ }) } }
        rule.onNodeWithText("3 file changes waiting for review").assertExists()
        rule.onNodeWithTag("pending_changes_banner").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `setup sheet shows progress and cancel while downloading`() {
        var cancelled = false
        val state = LinuxSetupState(
            phase = LinuxSetupPhase.DOWNLOADING,
            phaseProgress = 0.5f,
            bytesDownloaded = 15L * 1024 * 1024,
            totalBytes = 30L * 1024 * 1024,
            mirrorIndex = 1,
            mirrorCount = 3
        )
        rule.setContent {
            MyApplicationTheme {
                LinuxSetupSheet(state, listOf("Mirror 1/3"), onStart = {}, onCancel = { cancelled = true }, onDismiss = {})
            }
        }
        rule.onNodeWithText("15.0 MB of 30.0 MB (source 1 of 3)").assertExists()
        rule.onNodeWithTag("linux_setup_progress").assertExists()
        rule.onNodeWithTag("linux_setup_cancel").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun `setup sheet explains a failure and offers retry`() {
        var retried = false
        val state = LinuxSetupState(
            phase = LinuxSetupPhase.FAILED,
            failedPhase = LinuxSetupPhase.DOWNLOADING,
            error = LinuxSetupError.NO_NETWORK,
            errorDetail = "Unable to resolve host"
        )
        rule.setContent {
            MyApplicationTheme {
                LinuxSetupSheet(state, listOf("Cannot resolve host"), onStart = { retried = true }, onCancel = {}, onDismiss = {})
            }
        }
        rule.onNodeWithText("No internet connection").assertExists()
        rule.onNodeWithText("Unable to resolve host").assertExists()
        rule.onNodeWithTag("linux_setup_retry").performClick()
        assertTrue(retried)
    }

    @Test
    fun `review sheet lists files, shows a diff and reports selection`() {
        var toggled: String? = null
        var applied = false
        val pending = PendingChanges(
            files = listOf(
                PendingFileChange("index.html", "<p>old</p>\n", "<p>new</p>\n", added = 1, removed = 1),
                PendingFileChange("app.js", null, "console.log(1)\n", added = 1, removed = 0)
            ),
            commands = listOf("npm install"),
            summary = "Update"
        )
        rule.setContent {
            MyApplicationTheme {
                ChangeReviewSheet(
                    pending = pending,
                    onToggleFile = { toggled = it },
                    onSelectAll = {},
                    onApply = { applied = true },
                    onDiscard = {},
                    onHide = {}
                )
            }
        }
        rule.onNodeWithText("Review changes").assertExists()
        rule.onNodeWithText("2 of 2 selected").assertExists()
        rule.onNodeWithText("npm install", substring = true).assertExists()

        rule.onNodeWithText("index.html").performClick() // expands the diff
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("<p>new</p>")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("<p>old</p>").assertExists()

        rule.onNodeWithTag("review_apply").assertIsEnabled().performClick()
        assertTrue(applied)
        assertEquals(null, toggled)
    }

    @Test
    fun `overall setup progress never goes backwards across phases`() {
        val phases = listOf(
            LinuxSetupPhase.CHECKING, LinuxSetupPhase.RESOLVING, LinuxSetupPhase.DOWNLOADING,
            LinuxSetupPhase.VERIFYING, LinuxSetupPhase.EXTRACTING, LinuxSetupPhase.CONFIGURING, LinuxSetupPhase.DONE
        )
        var previous = 0f
        for (phase in phases) {
            for (p in listOf(0f, 0.5f, 1f)) {
                val value = LinuxSetupState(phase = phase, phaseProgress = p).overallProgress
                assertTrue("$phase@$p = $value < $previous", value >= previous - 1e-6f)
                previous = value
            }
        }
        assertEquals(1f, previous, 0f)
    }
}
