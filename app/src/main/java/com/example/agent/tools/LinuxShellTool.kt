package com.example.agent.tools

import com.example.agent.Risk
import com.example.agent.Tool
import com.example.agent.ToolResult
import com.example.linux.LinuxEnvironment
import com.example.linux.LinuxException
import org.json.JSONObject

/** Runs shell commands in the app's private Alpine Linux. Asks for approval unless [requireConfirm] is false. */
class LinuxShellTool(private val linux: LinuxEnvironment, requireConfirm: Boolean = true) : Tool {
    override val name = "linux_shell"
    override val description =
        "Run a shell command in a private Alpine Linux environment inside this app (not the phone's own system). " +
            "It has busybox tools; install more with 'apk add <package>' (python3, git, curl, nodejs...). " +
            "Files under /root persist between calls. Each call has a time limit (default 60 s, max 300 s)."
    // The shell only touches the app's private Alpine, so chat can run it silently; the power-button assistant still asks.
    override val risk = if (requireConfirm) Risk.CONFIRM else Risk.SAFE
    override val parameters = JSONObject(
        """{"type":"object","properties":{
             "command":{"type":"string","description":"Shell command, run with /bin/sh -c"},
             "timeout_seconds":{"type":"integer","minimum":1,"maximum":300}
           },"required":["command"]}"""
    )

    override suspend fun run(args: JSONObject): ToolResult {
        val command = args.optString("command").trim()
        if (command.isEmpty()) return ToolResult("Missing 'command'.", true)
        val timeout = args.optLong("timeout_seconds", 60).coerceIn(1, 300)
        if (!linux.isSupported) return ToolResult("Linux is not available on this phone's CPU.", true)
        return try {
            if (!linux.isInstalled) linux.install()
            val r = linux.exec(command, timeout)
            val header = when {
                r.timedOut -> "TIMED OUT after ${timeout}s (process killed)"
                else -> "exit code ${r.exitCode}"
            }
            ToolResult("$header\n${r.output}".trimEnd(), isError = r.timedOut || r.exitCode != 0)
        } catch (e: LinuxException) {
            ToolResult("Linux error: ${e.message}", true)
        } catch (e: java.io.IOException) {
            ToolResult("Linux failed to start: ${e.message}", true)
        }
    }
}
