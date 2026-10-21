package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.services.NodeService
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.ServerSocket
import java.net.Socket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DevServerSecurityTest {

    private lateinit var nodeService: NodeService
    private lateinit var projectDir: File
    private var port = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        nodeService = NodeService(context)
        projectDir = File(context.filesDir, "my_projects/Site").apply { mkdirs() }
        File(projectDir, "index.html").writeText("<h1>site</h1>")
        File(projectDir, "my page.html").writeText("<h1>spaced</h1>")
        File(context.filesDir, "../shared_prefs").mkdirs()
        File(context.filesDir, "../shared_prefs/secret.xml").writeText("API_KEY=sk-secret")

        port = ServerSocket(0).use { it.localPort }
        nodeService.startLocalDevServer(projectDir, port)
    }

    @After
    fun tearDown() {
        nodeService.stopLocalDevServer()
    }

    /** Sends a raw request line, exactly as given (no client-side path normalization). */
    private fun rawGet(path: String): String {
        repeat(50) {
            try {
                Socket("127.0.0.1", port).use { socket ->
                    socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                    socket.getOutputStream().flush()
                    return socket.getInputStream().bufferedReader().readText()
                }
            } catch (_: Exception) {
                Thread.sleep(50) // server still starting
            }
        }
        error("dev server did not start on port $port")
    }

    @Test
    fun `serves project files`() {
        val response = rawGet("/")
        assertTrue(response.startsWith("HTTP/1.1 200"))
        assertTrue(response.contains("<h1>site</h1>"))
    }

    @Test
    fun `decodes percent-encoded paths`() {
        assertTrue(rawGet("/my%20page.html").contains("<h1>spaced</h1>"))
    }

    @Test
    fun `refuses paths outside the project folder`() {
        for (path in listOf("/../../../shared_prefs/secret.xml", "/%2e%2e/%2e%2e/%2e%2e/shared_prefs/secret.xml", "/..%2f..%2f..%2fshared_prefs%2fsecret.xml")) {
            val response = rawGet(path)
            assertTrue("expected 404 for $path", response.startsWith("HTTP/1.1 404"))
            assertFalse("leaked secret for $path", response.contains("sk-secret"))
        }
    }
}
