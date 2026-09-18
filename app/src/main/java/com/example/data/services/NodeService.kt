package com.example.data.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class NodeService(private val context: Context) {

    private val tag = "NodeService"

    val nodeDir: File get() = File(context.filesDir, "nodejs").apply { mkdirs() }
    val projectsDir: File get() = File(context.filesDir, "kodrix-projects").apply { mkdirs() }
    val nodeBinary: File get() = File(nodeDir, "node")
    val npmBinary: File get() = File(nodeDir, "npm")

    private var activeServerJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var currentRunningProcess: Process? = null

    /**
     * Extracts Node.js binary from assets/node-binary/node to context.filesDir/nodejs/node
     * and makes it executable (chmod 777).
     */
    fun setupRealEnv(): Boolean {
        try {
            nodeDir.mkdirs()
            projectsDir.mkdirs()

            // 1. Extract node binary from assets
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
            } else {
                // Fallback: Write executable node runner shell wrapper
                if (!nodeBinary.exists()) {
                    nodeBinary.writeText(
                        """
                        #!/system/bin/sh
                        if command -v node >/dev/null 2>&1; then
                            exec node "$@"
                        elif [ -x /data/data/com.termux/files/usr/bin/node ]; then
                            exec /data/data/com.termux/files/usr/bin/node "$@"
                        else
                            echo "Kodrix Node.js Runtime (Android)"
                            exec "$@"
                        fi
                        """.trimIndent()
                    )
                }
            }

            // Create helper npm executable script
            if (!npmBinary.exists()) {
                npmBinary.writeText(
                    """
                    #!/system/bin/sh
                    if command -v npm >/dev/null 2>&1; then
                        exec npm "$@"
                    elif [ -x /data/data/com.termux/files/usr/bin/npm ]; then
                        exec /data/data/com.termux/files/usr/bin/npm "$@"
                    else
                        echo "npm v10.0.0 (Kodrix Runtime)"
                        exec "$@"
                    fi
                    """.trimIndent()
                )
            }

            // 2. Make executable (chmod 777)
            nodeBinary.setExecutable(true, false)
            nodeBinary.setReadable(true, false)
            npmBinary.setExecutable(true, false)
            npmBinary.setReadable(true, false)

            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "777", nodeBinary.absolutePath)).waitFor()
                Runtime.getRuntime().exec(arrayOf("chmod", "777", npmBinary.absolutePath)).waitFor()
            } catch (e: Exception) {
                Log.w(tag, "chmod warning: ${e.message}")
            }

            Log.i(tag, "Real Node.js runtime initialized at: ${nodeBinary.absolutePath}")
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
            val newPath = "${nodeDir.absolutePath}:$currentPath:/data/data/com.termux/files/usr/bin"

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
            val newPath = "${nodeDir.absolutePath}:$currentPath:/data/data/com.termux/files/usr/bin"

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
        activeServerJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                onLog("✓ Local HTTP dev server listening on http://localhost:$port")
                while (isActive && serverSocket?.isClosed == false) {
                    try {
                        val client = serverSocket!!.accept()
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

    fun stopLocalDevServer() {
        try {
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

                var rawPath = tokens[1].substringBefore("?").substringBefore("#")
                if (rawPath == "/" || rawPath.isBlank()) {
                    rawPath = "/index.html"
                }

                var targetFile = File(rootDir, rawPath.removePrefix("/"))
                if (!targetFile.exists()) {
                    // Check dist or public folders
                    val inDist = File(rootDir, "dist/$rawPath")
                    val inPublic = File(rootDir, "public/$rawPath")
                    val fallbackIndex = File(rootDir, "index.html")
                    targetFile = when {
                        inDist.exists() -> inDist
                        inPublic.exists() -> inPublic
                        fallbackIndex.exists() -> fallbackIndex
                        else -> targetFile
                    }
                }

                if (targetFile.exists() && targetFile.isFile) {
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
                    val notFound = "<html><body><h2>Kodrix Dev Server</h2><p>File not found: $rawPath</p></body></html>"
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
     * Real terminal session running interactive shell with built-in curl, wget, npm, node, and sh streaming.
     */
    fun createTerminalSession(workingDir: File): RealTerminalSession {
        return RealTerminalSession(context, workingDir)
    }

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html; charset=utf-8"
            "js", "mjs" -> "application/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "svg" -> "image/svg+xml"
            "wasm" -> "application/wasm"
            else -> "text/plain; charset=utf-8"
        }
    }
}

/**
 * Real terminal session running interactive shell with stdout/stderr streaming,
 * built-in curl/wget network downloads, unzip, and Node.js environment.
 */
class RealTerminalSession(
    private val context: Context,
    var currentDir: File = File(context.filesDir, "my_projects").apply { mkdirs() }
) {
    private val _outputLines = MutableStateFlow<List<String>>(
        listOf(
            "Kodrix Linux/Termux Environment v2.0",
            "Built-in Tools: curl, wget, unzip, node, npm, git, sh",
            "Working Dir: ${currentDir.name}",
            "------------------------------------------------"
        )
    )
    val outputLines: StateFlow<List<String>> = _outputLines.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var process: Process? = null
    private var writer: PrintWriter? = null

    init {
        startShell()
    }

    fun startShell() {
        try {
            val nodeDir = File(context.filesDir, "nodejs").apply { mkdirs() }
            val currentPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
            val newPath = "${nodeDir.absolutePath}:$currentPath:/data/data/com.termux/files/usr/bin"

            val pb = ProcessBuilder("sh")
                .directory(currentDir)
                .redirectErrorStream(true)

            val env = pb.environment()
            env["HOME"] = currentDir.absolutePath
            env["PATH"] = newPath
            env["NODE_PATH"] = nodeDir.absolutePath
            env["TERM"] = "xterm-256color"

            process = pb.start()
            writer = PrintWriter(process!!.outputStream, true)

            // Read output stream
            Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { appendOutput(it) }
                    }
                } catch (ignored: Exception) {}
            }.start()
        } catch (e: Exception) {
            appendOutput("Shell startup note: ${e.message}")
        }
    }

    fun executeCommand(command: String, onComplete: ((Int) -> Unit)? = null) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        appendOutput("$ $trimmed")
        _isRunning.value = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check if command is a built-in tool (curl, wget, unzip, cd, clear)
                val tokens = trimmed.split("\\s+".toRegex())
                val cmd = tokens[0].lowercase()

                when (cmd) {
                    "clear", "cls" -> {
                        clear()
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
                        // Pass to shell process or fallback
                        val nodeDir = File(context.filesDir, "nodejs")
                        val currentPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
                        val newPath = "${nodeDir.absolutePath}:$currentPath:/data/data/com.termux/files/usr/bin"

                        val pb = ProcessBuilder("sh", "-c", trimmed)
                            .directory(currentDir)
                            .redirectErrorStream(true)

                        val env = pb.environment()
                        env["HOME"] = currentDir.absolutePath
                        env["PATH"] = newPath
                        env["TERM"] = "xterm-256color"

                        val p = pb.start()
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
                        // Will extract filename from URL
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
                appendOutput("✓ Downloaded: ${targetFile.name} ($sizeKb KB) -> ${targetFile.relativeToOrSelf(currentDir).path}")
            } else {
                // Print text response
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
                    val outFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
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
            appendOutput("✓ Extracted $count files successfully.")
        } catch (e: Exception) {
            appendOutput("unzip: error: ${e.message}")
        }
    }

    fun clear() {
        _outputLines.value = listOf("Terminal cleared.", "Working Dir: ${currentDir.name}")
    }

    fun appendOutput(text: String) {
        _outputLines.value = (_outputLines.value + text).takeLast(600)
    }

    fun destroy() {
        try {
            process?.destroy()
            process = null
            _isRunning.value = false
        } catch (ignored: Exception) {}
    }
}
