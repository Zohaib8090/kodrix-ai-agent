package com.example.data.services

import android.content.Context
import android.util.Log
import com.example.util.SafePaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

class NodeService(private val context: Context) {

    private val tag = "NodeService"

    val nodeDir: File get() = File(context.filesDir, "nodejs").apply { mkdirs() }
    val usrDir: File get() = File(context.filesDir, "usr").apply { mkdirs() }
    val usrBin: File get() = File(usrDir, "bin").apply { mkdirs() }
    val usrLib: File get() = File(usrDir, "lib").apply { mkdirs() }
    val projectsDir: File get() = File(context.filesDir, "kodrix-projects").apply { mkdirs() }
    val nodeBinary: File get() = File(usrBin, "node")
    val npmBinary: File get() = File(usrBin, "npm")

    val isRealNodeInstalled: Boolean
        get() {
            val localNode = File(usrBin, "node")
            return localNode.exists() && localNode.canExecute()
        }

    fun getRealNodeBinary(): File? {
        val localNode = File(usrBin, "node")
        if (localNode.exists() && localNode.canExecute()) return localNode
        return null
    }

    companion object {
        // One preview server per process: every screen creates its own NodeService, and a server
        // left running by a previous project would keep port 5173 and keep serving its files.
        @Volatile private var activeServerJob: Job? = null
        @Volatile private var serverSocket: ServerSocket? = null
        @Volatile private var currentRunningProcess: Process? = null
        @Volatile private var serverRootDir: File? = null
    }

    /**
     * Downloads and installs the real precompiled Node.js / Termux runtime for the device's CPU architecture.
     */
    suspend fun downloadAndInstallRealNode(onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val embeddedManager = EmbeddedTermuxManager(context)
        return@withContext embeddedManager.installBootstrap(onLog)
    }

    /**
     * Sets up Node.js runtime permissions and environment.
     */
    fun setupRealEnv(): Boolean {
        try {
            nodeDir.mkdirs()
            usrBin.mkdirs()
            projectsDir.mkdirs()

            // 1. Extract node binary from assets if bundled
            val assetNames = try {
                context.assets.list("node-binary") ?: emptyArray()
            } catch (e: Exception) {
                emptyArray<String>()
            }

            if ("node" in assetNames) {
                context.assets.open("node-binary/node").use { input ->
                    FileOutputStream(nodeBinary).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            nodeBinary.setExecutable(true, false)
            nodeBinary.setReadable(true, false)
            npmBinary.setExecutable(true, false)
            npmBinary.setReadable(true, false)

            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "755", nodeBinary.absolutePath)).waitFor()
                Runtime.getRuntime().exec(arrayOf("chmod", "755", npmBinary.absolutePath)).waitFor()
            } catch (_: Exception) {}

            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to setup real Node environment", e)
            return false
        }
    }

    /**
     * Executes a real shell command using ProcessBuilder with HOME set to filesDir/kodrix-projects.
     */
    fun runCommand(
        command: String,
        workingDir: File = projectsDir,
        onOutput: (String) -> Unit
    ): Int {
        setupRealEnv()
        try {
            val currentPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
            val newPath = "${nodeDir.absolutePath}:$currentPath"

            val pb = ProcessBuilder("sh", "-c", command)
                .directory(workingDir)
                .redirectErrorStream(true)

            val env = pb.environment()
            env["HOME"] = projectsDir.absolutePath
            env["PATH"] = newPath
            env["NODE_PATH"] = nodeDir.absolutePath
            env["TERM"] = "xterm-256color"

            val process = pb.start()
            currentRunningProcess = process

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    onOutput(line ?: "")
                }
            }

            val exitCode = process.waitFor()
            return exitCode
        } catch (e: Exception) {
            onOutput("Error executing command: ${e.message}")
            return -1
        }
    }

    /**
     * Installs dependencies and runs the development server on port 5173.
     * Serves project files over real HTTP at http://localhost:5173.
     */
    fun installAndRun(
        projectDir: File,
        port: Int = 5173,
        onOutput: (String) -> Unit
    ): Process? {
        setupRealEnv()
        projectDir.mkdirs()

        // 1. Run npm install
        onOutput(">> Running: node npm install in ${projectDir.name}...")
        runCommand("npm install || true", workingDir = projectDir, onOutput = onOutput)

        // 2. Start local HTTP dev server on port 5173 to serve the live web application files
        startLocalDevServer(projectDir, port, onOutput)

        // 3. Start node dev server process
        onOutput(">> Starting real dev server: node npm run dev -- --port $port")
        try {
            val currentPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
            val newPath = "${nodeDir.absolutePath}:$currentPath"

            val pb = ProcessBuilder("sh", "-c", "npm run dev -- --port $port || true")
                .directory(projectDir)
                .redirectErrorStream(true)

            val env = pb.environment()
            env["HOME"] = projectsDir.absolutePath
            env["PATH"] = newPath
            env["NODE_PATH"] = nodeDir.absolutePath
            env["PORT"] = port.toString()

            val process = pb.start()
            currentRunningProcess = process

            // Background output logger
            Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            onOutput(line ?: "")
                        }
                    }
                } catch (ignored: Exception) {}
            }.start()

            onOutput(">> Dev server active at http://localhost:$port")
            return process
        } catch (e: Exception) {
            onOutput("Dev server startup note: ${e.message}")
            return null
        }
    }

    /**
     * Real embedded HTTP static server serving the project files on http://localhost:5173.
     */
    fun startLocalDevServer(
        rootDir: File,
        port: Int = 5173,
        onLog: (String) -> Unit = {}
    ) {
        stopLocalDevServer()
        serverRootDir = rootDir
        activeServerJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Loopback only: the preview is for this device. Binding every interface would let
                // anyone on the same Wi-Fi read files from the app's private storage.
                val socket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
                }
                serverSocket = socket
                onLog("Local HTTP dev server listening on http://localhost:$port")
                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        handleClientConnection(client, rootDir)
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                onLog("Dev server port $port: ${e.message}")
            }
        }
    }

    /** Stops the preview server; with [rootDir], only if it is still serving that folder. */
    fun stopLocalDevServer(rootDir: File? = null) {
        if (rootDir != null && rootDir != serverRootDir) return
        try {
            serverRootDir = null
            serverSocket?.close()
            serverSocket = null
            activeServerJob?.cancel()
            currentRunningProcess?.destroy()
            currentRunningProcess = null
        } catch (ignored: Exception) {}
    }

    private fun handleClientConnection(socket: Socket, rootDir: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = socket.getOutputStream()
                val requestLine = input.readLine() ?: return@launch
                val tokens = requestLine.split(" ")
                if (tokens.size < 2) return@launch

                val requestPath = tokens[1].substringBefore("?").substringBefore("#")
                val decodedPath = try {
                    URLDecoder.decode(requestPath.replace("+", "%2B"), "UTF-8")
                } catch (_: Exception) {
                    requestPath
                }
                val rawPath = if (decodedPath == "/" || decodedPath.isBlank()) "/index.html" else decodedPath
                val relativePath = rawPath.removePrefix("/")

                // Anything that resolves outside the project folder ("../../shared_prefs/...") is refused.
                var targetFile: File? = SafePaths.resolveInside(rootDir, relativePath)
                if (targetFile != null && targetFile.isDirectory) {
                    targetFile = File(targetFile, "index.html")
                }
                if (targetFile != null && !targetFile.exists()) {
                    // Check dist or public folders, then fall back to index.html for client-side routes
                    targetFile = listOf("dist/$relativePath", "public/$relativePath", "index.html")
                        .mapNotNull { SafePaths.resolveInside(rootDir, it) }
                        .firstOrNull { it.isFile }
                        ?: targetFile
                }

                if (targetFile != null && targetFile.exists() && targetFile.isFile) {
                    val bytes = targetFile.readBytes()
                    val mime = getMimeType(targetFile.name)
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: $mime\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray())
                    output.write(bytes)
                    output.flush()
                } else {
                    val notFound = "<html><body><h2>Kodrix Dev Server</h2><p>File not found: ${escapeHtml(rawPath)}</p></body></html>"
                    val bytes = notFound.toByteArray()
                    val header = "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Type: text/html\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray())
                    output.write(bytes)
                    output.flush()
                }
                socket.close()
            } catch (e: Exception) {
                try { socket.close() } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * Real terminal session running interactive shell with built-in curl, wget, npm, node, git and sh streaming.
     */
    fun createTerminalSession(workingDir: File): RealTerminalSession {
        return RealTerminalSession(context, workingDir, this)
    }

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html; charset=utf-8"
            "js", "mjs" -> "application/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "json", "map" -> "application/json; charset=utf-8"
            "xml" -> "application/xml; charset=utf-8"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "svg" -> "image/svg+xml"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "wasm" -> "application/wasm"
            else -> "text/plain; charset=utf-8"
        }
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/**
 * Real terminal session running interactive shell with stdout/stderr streaming,
 * built-in npm, node, git, curl/wget network downloads, unzip, and Node.js environment.
 */
class RealTerminalSession(
    private val context: Context,
    var currentDir: File = File(context.filesDir, "my_projects").apply { mkdirs() },
    private val nodeService: NodeService? = null
) {
    private val embeddedTermux = EmbeddedTermuxManager(context)

    // Commands (including the ones the AI issues) run one at a time, in order.
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()

    private val _outputLines = MutableStateFlow<List<String>>(
        listOf(
            "Kodrix Linux/Termux Environment v2.0",
            "Built-in: ls, cd, pwd, cat, mkdir, rm, touch, echo, curl, wget, unzip, git clone",
            if (embeddedTermux.isInstalled) "Embedded Termux Linux: ACTIVE (${embeddedTermux.getArchitecture()})"
            else if (nodeService?.isRealNodeInstalled == true) "Native Node.js runtime: ACTIVE (${embeddedTermux.getArchitecture()})"
            else "Type 'setup-termux' to install bash, node, npm, git and pkg",
            "Working Dir: ${currentDir.name}",
            "------------------------------------------------"
        )
    )
    val outputLines: StateFlow<List<String>> = _outputLines.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var process: Process? = null

    init {
        nodeService?.setupRealEnv()
    }

    fun executeCommand(command: String, onComplete: ((Int) -> Unit)? = null) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        sessionScope.launch {
            commandMutex.withLock {
                appendOutput("$ $trimmed")
                _isRunning.value = true
                try {
                    val tokens = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    val cmd = tokens.firstOrNull()?.lowercase() ?: ""

                    // 1. Check for setup-termux or setup-node installer commands
                    if (cmd == "setup-termux" || cmd == "install-termux") {
                        appendOutput(">> Initializing Embedded Termux Linux Rootfs (${embeddedTermux.getArchitecture()})...")
                        val success = embeddedTermux.installBootstrap(
                            onLog = { appendOutput(it) },
                            onProgress = { p -> if (p > 0) appendOutput(">> Progress: ${(p * 100).toInt()}%") }
                        )
                        if (success) {
                            appendOutput("Embedded Termux Linux environment is ACTIVE.")
                            appendOutput("You can now run 'pkg install <pkg>', 'bash', 'node', 'npm', 'git', etc.")
                        } else {
                            appendOutput("Setup failed. Check your internet connection and try again.")
                        }
                        _isRunning.value = false
                        onComplete?.invoke(if (success) 0 else 1)
                        return@launch
                    }

                    if (cmd == "setup-node" || cmd == "install-node") {
                        appendOutput(">> Starting Real Native Node.js Installer...")
                        val success = nodeService?.downloadAndInstallRealNode { appendOutput(it) } ?: false
                        if (success) {
                            appendOutput("Embedded Linux environment with Node.js installed.")
                            appendOutput("You can now run real 'node', 'npm install', 'git clone', etc.")
                        } else {
                            appendOutput("Installation failed. Check your internet connection and try again.")
                        }
                        _isRunning.value = false
                        onComplete?.invoke(if (success) 0 else 1)
                        return@launch
                    }

                    // 2. If Embedded Termux or Real Node.js binary is installed, execute real OS process
                    val realNode = nodeService?.getRealNodeBinary()
                    if (embeddedTermux.isInstalled || realNode != null || cmd == "pkg" || cmd == "apt" || cmd == "apt-get" || cmd == "bash" || cmd == "sh" || cmd == "dpkg") {
                        val exit = embeddedTermux.execute(trimmed, currentDir) { line ->
                            appendOutput(line)
                        }
                        if (exit != 0) {
                            appendOutput("[Exit code $exit]")
                        }
                        _isRunning.value = false
                        onComplete?.invoke(exit)
                        return@launch
                    }

                    when (cmd) {
                        "clear", "cls" -> {
                            clear()
                            _isRunning.value = false
                            onComplete?.invoke(0)
                            return@launch
                        }

                        "help" -> {
                            appendOutput("Kodrix Terminal Commands:")
                            appendOutput("  setup-termux              - Install the embedded Linux environment (bash, node, npm, git, pkg)")
                            appendOutput("  npm run dev | npm start   - Serve project files at http://localhost:5173")
                            appendOutput("  git clone <url>           - Download a public GitHub repository")
                            appendOutput("  curl -O <url> | wget <url>- Download files from internet")
                            appendOutput("  unzip <file.zip>          - Extract archive files")
                            appendOutput("  ls [-la] | pwd | cat      - File system inspection")
                            appendOutput("  mkdir | rm [-r] | touch   - File system operations")
                            appendOutput("  clear                     - Clear terminal screen")
                            appendOutput("Other npm, node and git commands need 'setup-termux' first.")
                            _isRunning.value = false
                            onComplete?.invoke(0)
                            return@launch
                        }

                        "cd" -> {
                            val target = if (tokens.size > 1) tokens[1] else currentDir.parent ?: currentDir.absolutePath
                            val newDir = if (target.startsWith("/")) File(target) else File(currentDir, target)
                            if (newDir.exists() && newDir.isDirectory) {
                                currentDir = newDir.canonicalFile
                                appendOutput("Changed directory to: ${currentDir.absolutePath}")
                            } else {
                                appendOutput("cd: no such directory: $target")
                            }
                            _isRunning.value = false
                            onComplete?.invoke(0)
                            return@launch
                        }

                        "npm" -> {
                            val exit = handleNpm(tokens, trimmed)
                            _isRunning.value = false
                            onComplete?.invoke(exit)
                            return@launch
                        }

                        "npx" -> {
                            val exit = handleNpx(tokens)
                            _isRunning.value = false
                            onComplete?.invoke(exit)
                            return@launch
                        }

                        "node" -> {
                            val exit = handleNode(tokens)
                            _isRunning.value = false
                            onComplete?.invoke(exit)
                            return@launch
                        }

                        "git" -> {
                            val exit = handleGit(tokens, trimmed)
                            _isRunning.value = false
                            onComplete?.invoke(exit)
                            return@launch
                        }

                        "ls", "dir" -> {
                            handleLs(tokens)
                            _isRunning.value = false
                            onComplete?.invoke(0)
                            return@launch
                        }

                        "pwd" -> {
                            appendOutput(currentDir.absolutePath)
                            _isRunning.value = false
                            onComplete?.invoke(0)
                            return@launch
                        }

                        "cat" -> {
                            handleCat(tokens)
                            _isRunning.value = false
                            onComplete?.invoke(0)
                            return@launch
                        }

                        "mkdir" -> {
                            handleMkdir(tokens)
                            _isRunning.value = false
                            onComplete?.invoke(0)
                            return@launch
                        }

                        "rm" -> {
                            val exit = handleRm(tokens)
                            _isRunning.value = false
                            onComplete?.invoke(exit)
                            return@launch
                        }

                        "touch" -> {
                            handleTouch(tokens)
                            _isRunning.value = false
                            onComplete?.invoke(0)
                            return@launch
                        }

                        "echo" -> {
                            handleEcho(tokens, trimmed)
                            _isRunning.value = false
                            onComplete?.invoke(0)
                            return@launch
                        }

                        "curl" -> {
                            handleCurl(tokens)
                            _isRunning.value = false
                            onComplete?.invoke(0)
                            return@launch
                        }

                        "wget" -> {
                            handleWget(tokens)
                            _isRunning.value = false
                            onComplete?.invoke(0)
                            return@launch
                        }

                        "unzip" -> {
                            handleUnzip(tokens)
                            _isRunning.value = false
                            onComplete?.invoke(0)
                            return@launch
                        }

                        else -> {
                            val nodeDir = File(context.filesDir, "nodejs")
                            val currentPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
                            val newPath = "${nodeDir.absolutePath}:$currentPath"

                            val pb = ProcessBuilder("sh", "-c", trimmed)
                                .directory(currentDir)
                                .redirectErrorStream(true)

                            val env = pb.environment()
                            env["HOME"] = currentDir.absolutePath
                            env["PATH"] = newPath
                            env["TERM"] = "xterm-256color"

                            val p = pb.start()
                            process = p
                            BufferedReader(InputStreamReader(p.inputStream)).use { reader ->
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    line?.let { appendOutput(it) }
                                }
                            }
                            val exit = p.waitFor()
                            if (exit != 0) {
                                appendOutput("[Exit code $exit]")
                            }
                            _isRunning.value = false
                            onComplete?.invoke(exit)
                        }
                    }
                } catch (e: Exception) {
                    appendOutput("Error: ${e.message}")
                    _isRunning.value = false
                    onComplete?.invoke(-1)
                }
            }
        }
    }

    /**
     * Without the embedded Linux environment there is no real npm, node or git. Say so plainly
     * instead of printing output that looks like the command worked.
     */
    private fun runtimeMissing(tool: String): Int {
        appendOutput("$tool: not available. The embedded Linux environment is not installed.")
        appendOutput("Run 'setup-termux' to install it, then run the command again.")
        return 127
    }

    /** Serves the project folder as static files; no bundler runs without Node.js. */
    private fun startStaticPreview(): Int {
        nodeService?.startLocalDevServer(currentDir, port = 5173) { log ->
            appendOutput(log)
        }
        appendOutput("Node.js is not installed, so no bundler or dev server process is running.")
        appendOutput("Serving the project files as-is at http://localhost:5173/ (open the Preview tab).")
        return 0
    }

    private fun handleNpm(tokens: List<String>, rawCommand: String): Int {
        val subCmd = tokens.getOrNull(1)?.lowercase() ?: ""
        val script = if (subCmd == "run") tokens.getOrNull(2)?.lowercase() ?: "" else subCmd
        return if (script == "dev" || script == "start" || script == "preview") {
            startStaticPreview()
        } else {
            runtimeMissing("npm")
        }
    }

    private fun handleNpx(tokens: List<String>): Int {
        val target = tokens.getOrNull(1)?.lowercase() ?: ""
        return if (target.contains("vite") || target.contains("serve") || target.contains("http-server")) {
            startStaticPreview()
        } else {
            runtimeMissing("npx")
        }
    }

    private fun handleNode(tokens: List<String>): Int = runtimeMissing("node")

    private fun handleGit(tokens: List<String>, rawCommand: String): Int {
        val subCmd = tokens.getOrNull(1)?.lowercase() ?: ""
        if (subCmd != "clone") return runtimeMissing("git")

        val repoUrl = tokens.getOrNull(2)
        if (repoUrl.isNullOrBlank()) {
            appendOutput("git clone: missing repository URL")
            return 128
        }
        if (!repoUrl.contains("github.com")) {
            appendOutput("git clone: only public GitHub repositories can be downloaded without the embedded Linux environment.")
            appendOutput("Run 'setup-termux' to use the real git client.")
            return 128
        }

        val repoName = repoUrl.removeSuffix("/").substringAfterLast('/').removeSuffix(".git").ifBlank { "cloned-repo" }
        val targetDir = SafePaths.resolveInside(currentDir, repoName)
        if (targetDir == null) {
            appendOutput("git clone: invalid destination '$repoName'")
            return 128
        }
        if (targetDir.exists() && !targetDir.listFiles().isNullOrEmpty()) {
            appendOutput("fatal: destination path '$repoName' already exists and is not an empty directory.")
            return 128
        }

        appendOutput("Downloading '$repoName' from GitHub (archive of the default branch)...")
        val clean = repoUrl.removeSuffix("/").removeSuffix(".git").let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
        for (branch in listOf("main", "master")) {
            val tempZip = File(context.cacheDir, "clone_${System.currentTimeMillis()}.zip")
            try {
                val conn = java.net.URL("$clean/archive/refs/heads/$branch.zip").openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    conn.disconnect()
                    continue
                }
                conn.inputStream.use { input ->
                    tempZip.outputStream().use { output -> input.copyTo(output) }
                }
                targetDir.mkdirs()
                var count = 0
                // Extract into targetDir, stripping the archive's root folder
                java.util.zip.ZipInputStream(tempZip.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val relPath = entry.name.substringAfter('/', missingDelimiterValue = "")
                        val outFile = if (relPath.isNotEmpty()) SafePaths.resolveInside(targetDir, relPath) else null
                        if (outFile != null) {
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { fos -> zis.copyTo(fos) }
                                count++
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                appendOutput("Downloaded $count files from branch '$branch' into $repoName/ (no git history).")
                return 0
            } catch (e: Exception) {
                appendOutput("git clone: ${e.message}")
            } finally {
                tempZip.delete()
            }
        }

        appendOutput("git clone: could not download $repoUrl (is it a public repository with a 'main' or 'master' branch?)")
        return 128
    }

    private fun handleLs(tokens: List<String>) {
        val showAll = tokens.any { it.contains("a") }
        val showLong = tokens.any { it.contains("l") }

        val files = currentDir.listFiles() ?: emptyArray()
        val filtered = if (showAll) files else files.filter { !it.name.startsWith(".") }.toTypedArray()
        val sorted = filtered.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        if (showLong) {
            val totalBlocks = sorted.size * 4
            appendOutput("total $totalBlocks")
            val sdf = java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault())
            sorted.forEach { f ->
                val perm = if (f.isDirectory) "drwxr-xr-x" else "-rw-r--r--"
                val size = if (f.isDirectory) 4096 else f.length()
                val dateStr = sdf.format(java.util.Date(f.lastModified()))
                val nameDisplay = if (f.isDirectory) "${f.name}/" else f.name
                appendOutput(String.format("%-10s  1 user user %6d %s %s", perm, size, dateStr, nameDisplay))
            }
        } else {
            val names = sorted.joinToString("  ") { if (it.isDirectory) "${it.name}/" else it.name }
            appendOutput(if (names.isBlank()) "(empty directory)" else names)
        }
    }

    private fun handleCat(tokens: List<String>) {
        if (tokens.size < 2) {
            appendOutput("cat: missing file operand")
            return
        }
        val targetPath = tokens[1]
        val targetFile = if (targetPath.startsWith("/")) File(targetPath) else File(currentDir, targetPath)
        if (!targetFile.exists()) {
            appendOutput("cat: ${targetFile.name}: No such file or directory")
            return
        }
        if (targetFile.isDirectory) {
            appendOutput("cat: ${targetFile.name}: Is a directory")
            return
        }
        try {
            val text = targetFile.readText()
            appendOutput(text.take(3000) + if (text.length > 3000) "\n... [truncated ${text.length - 3000} chars]" else "")
        } catch (e: Exception) {
            appendOutput("cat: error reading file: ${e.message}")
        }
    }

    private fun handleMkdir(tokens: List<String>) {
        val targetName = tokens.firstOrNull { !it.startsWith("-") && it != "mkdir" }
        if (targetName == null) {
            appendOutput("mkdir: missing operand")
            return
        }
        val targetDir = if (targetName.startsWith("/")) File(targetName) else File(currentDir, targetName)
        if (targetDir.mkdirs()) {
            appendOutput("Created directory: ${targetDir.name}")
        } else {
            appendOutput("mkdir: cannot create directory '${targetName}'")
        }
    }

    private fun handleRm(tokens: List<String>): Int {
        val flags = tokens.drop(1).filter { it.startsWith("-") }
        val recursive = flags.any { it.contains('r') || it.contains('R') }
        val targets = tokens.drop(1).filterNot { it.startsWith("-") }
        if (targets.isEmpty()) {
            appendOutput("rm: missing operand")
            return 1
        }
        var exit = 0
        for (targetName in targets) {
            val target = if (targetName.startsWith("/")) File(targetName) else File(currentDir, targetName)
            when {
                targetName == "." || targetName == ".." || targetName == "/" ||
                    (target.exists() && SafePaths.isInside(target, currentDir)) -> {
                    appendOutput("rm: refusing to remove '$targetName'")
                    exit = 1
                }
                !target.exists() -> {
                    appendOutput("rm: cannot remove '$targetName': No such file or directory")
                    exit = 1
                }
                target.isDirectory && !recursive -> {
                    appendOutput("rm: cannot remove '$targetName': Is a directory (use rm -r)")
                    exit = 1
                }
                target.deleteRecursively() -> appendOutput("Removed: ${target.name}")
                else -> {
                    appendOutput("rm: failed to remove '$targetName'")
                    exit = 1
                }
            }
        }
        return exit
    }

    private fun handleTouch(tokens: List<String>) {
        val targetName = tokens.getOrNull(1)
        if (targetName == null) {
            appendOutput("touch: missing file operand")
            return
        }
        val targetFile = if (targetName.startsWith("/")) File(targetName) else File(currentDir, targetName)
        try {
            targetFile.createNewFile()
            appendOutput("Created file: ${targetFile.name}")
        } catch (e: Exception) {
            appendOutput("touch: error creating file: ${e.message}")
        }
    }

    private fun handleEcho(tokens: List<String>, raw: String) {
        val content = raw.removePrefix("echo").trim()
        if (content.contains(">")) {
            val appendMode = content.contains(">>")
            val parts = if (appendMode) content.split(">>") else content.split(">")
            val textToEcho = parts[0].trim().removeSurrounding("\"").removeSurrounding("'")
            val targetName = parts.getOrNull(1)?.trim() ?: ""
            if (targetName.isNotEmpty()) {
                val targetFile = if (targetName.startsWith("/")) File(targetName) else File(currentDir, targetName)
                if (appendMode) {
                    targetFile.appendText(textToEcho + "\n")
                } else {
                    targetFile.writeText(textToEcho + "\n")
                }
                appendOutput("Wrote to ${targetFile.name}")
                return
            }
        }
        appendOutput(content.removeSurrounding("\"").removeSurrounding("'"))
    }

    private fun handleCurl(tokens: List<String>) {
        try {
            var urlStr: String? = null
            var outputFile: String? = null
            var silent = false
            var i = 1
            while (i < tokens.size) {
                when (tokens[i]) {
                    "-o" -> {
                        if (i + 1 < tokens.size) {
                            outputFile = tokens[i + 1]
                            i++
                        }
                    }
                    "-O" -> {
                        outputFile = "AUTO"
                    }
                    "-s", "-sS", "--silent" -> silent = true
                    else -> {
                        if (!tokens[i].startsWith("-")) {
                            urlStr = tokens[i]
                        }
                    }
                }
                i++
            }

            if (urlStr.isNullOrBlank()) {
                appendOutput("curl: usage: curl [-o <file>] [-O] [-s] <url>")
                return
            }

            if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
                urlStr = "https://$urlStr"
            }

            if (outputFile == "AUTO") {
                outputFile = urlStr.substringAfterLast('/').substringBefore('?').ifEmpty { "download_${System.currentTimeMillis()}" }
            }

            if (!silent) {
                appendOutput(">> Connecting to $urlStr ...")
            }

            val url = java.net.URL(urlStr)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "Kodrix-Agent/2.0 (Android; Linux)")
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                appendOutput("curl: HTTP error $responseCode ${conn.responseMessage}")
                return
            }

            val totalBytes = conn.contentLengthLong
            val inputStream = conn.inputStream

            if (outputFile != null) {
                val targetFile = if (outputFile.startsWith("/")) File(outputFile) else File(currentDir, outputFile)
                targetFile.parentFile?.mkdirs()
                var downloaded = 0L
                targetFile.outputStream().use { fos ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        downloaded += read
                    }
                }
                val sizeKb = downloaded / 1024
                appendOutput("Downloaded: ${targetFile.name} ($sizeKb KB) -> ${targetFile.relativeToOrSelf(currentDir).path}")
            } else {
                val text = inputStream.bufferedReader().use { it.readText() }
                appendOutput(text.take(2000) + if (text.length > 2000) "\n... [truncated ${text.length - 2000} chars]" else "")
            }
        } catch (e: Exception) {
            appendOutput("curl: error: ${e.message}")
        }
    }

    private fun handleWget(tokens: List<String>) {
        val url = tokens.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            ?: tokens.getOrNull(1)

        if (url == null) {
            appendOutput("wget: missing URL")
            return
        }
        handleCurl(listOf("curl", "-O", url))
    }

    private fun handleUnzip(tokens: List<String>) {
        try {
            val zipFileName = tokens.getOrNull(1)
            if (zipFileName.isNullOrBlank()) {
                appendOutput("unzip: usage: unzip <file.zip> [-d <destination>]")
                return
            }
            val zipFile = if (zipFileName.startsWith("/")) File(zipFileName) else File(currentDir, zipFileName)
            if (!zipFile.exists()) {
                appendOutput("unzip: cannot find zip file: ${zipFile.name}")
                return
            }

            var destDir = currentDir
            val dIndex = tokens.indexOf("-d")
            if (dIndex != -1 && dIndex + 1 < tokens.size) {
                val dPath = tokens[dIndex + 1]
                destDir = if (dPath.startsWith("/")) File(dPath) else File(currentDir, dPath)
            }
            destDir.mkdirs()

            appendOutput(">> Extracting ${zipFile.name} into ${destDir.name} ...")
            var count = 0
            java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = SafePaths.resolveInside(destDir, entry.name)
                    if (outFile == null) {
                        appendOutput("unzip: skipping unsafe entry '${entry.name}'")
                    } else if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { fos ->
                            zis.copyTo(fos)
                        }
                        count++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            appendOutput("Extracted $count files successfully.")
        } catch (e: Exception) {
            appendOutput("unzip: error: ${e.message}")
        }
    }

    fun clear() {
        _outputLines.value = listOf("Terminal cleared.", "Working Dir: ${currentDir.name}")
    }

    fun appendOutput(text: String) {
        // update{} so lines from concurrent readers (dev server logs, commands) are never lost
        _outputLines.update { (it + text).takeLast(600) }
    }

    fun destroy() {
        try {
            sessionScope.cancel()
            process?.destroy()
            process = null
            _isRunning.value = false
        } catch (ignored: Exception) {}
    }
}
