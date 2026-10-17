package com.example.data.services

import android.content.Context
import android.os.Build
import android.util.Log
import android.system.Os
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Embedded Termux Linux Userspace Manager.
 * Provides a full standalone Linux environment directly inside the app sandbox.
 */
class EmbeddedTermuxManager(private val context: Context) {

    private val tag = "EmbeddedTermux"

    private companion object {
        const val MIN_FREE_BYTES = 400L * 1024 * 1024
        const val MIN_ARCHIVE_BYTES = 1L * 1024 * 1024
        const val ATTEMPTS_PER_MIRROR = 2
        const val RETRY_DELAY_MS = 1500L
        const val REPORT_INTERVAL_NANOS = 120_000_000L
    }

    val filesDir: File get() = context.filesDir
    val prefixDir: File get() = File(filesDir, "usr")
    val homeDir: File get() = File(filesDir, "home")
    val binDir: File get() = File(prefixDir, "bin")
    val libDir: File get() = File(prefixDir, "lib")
    val etcDir: File get() = File(prefixDir, "etc")
    val tmpDir: File get() = File(prefixDir, "tmp")

    init {
        homeDir.mkdirs()
        prefixDir.mkdirs()
        binDir.mkdirs()
        libDir.mkdirs()
        etcDir.mkdirs()
        tmpDir.mkdirs()
    }

    /**
     * Checks if the embedded Termux Linux environment is fully installed (has real ELF binaries).
     */
    val isInstalled: Boolean
        get() {
            val bash = File(binDir, "bash")
            val dpkg = File(binDir, "dpkg")
            val apt = File(binDir, "apt")
            val node = File(binDir, "node")
            // Must exist and have significant binary size (> 10KB, not a dummy script)
            return (bash.exists() && bash.length() > 10000) ||
                    (dpkg.exists() && dpkg.length() > 10000) ||
                    (apt.exists() && apt.length() > 10000) ||
                    (node.exists() && node.length() > 10000)
        }

    /**
     * Returns the detected CPU architecture string.
     */
    fun getArchitecture(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "arm"
            abi.contains("x86_64") -> "x86_64"
            abi.contains("x86") -> "i686"
            else -> "aarch64"
        }
    }

    /**
     * Dynamically resolves the latest working bootstrap download URL from GitHub API or mirrors.
     */
    private fun resolveBootstrapUrls(arch: String): List<String> {
        val urls = mutableListOf<String>()

        // 1. Try to query GitHub Releases API dynamically
        try {
            val apiUrl = URL("https://api.github.com/repos/termux/termux-packages/releases")
            val conn = apiUrl.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Kodrix-Agent/2.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.connect()

            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val releases = JSONArray(jsonStr)
                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    if (release.has("assets")) {
                        val assets = release.getJSONArray("assets")
                        for (j in 0 until assets.length()) {
                            val asset = assets.getJSONObject(j)
                            val name = asset.optString("name", "")
                            if (name == "bootstrap-$arch.zip") {
                                val dlUrl = asset.optString("browser_download_url", "")
                                if (dlUrl.isNotBlank() && !urls.contains(dlUrl)) {
                                    urls.add(dlUrl)
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Known static release tags
        urls.add("https://github.com/termux/termux-packages/releases/download/bootstrap-2026.09.13-r1%2Bapt.android-7/bootstrap-$arch.zip")
        urls.add("https://github.com/termux/termux-packages/releases/download/bootstrap-2026.09.06-r1%2Bapt.android-7/bootstrap-$arch.zip")
        urls.add("https://github.com/termux/termux-packages/releases/download/bootstrap-2026.08.30-r1%2Bapt.android-7/bootstrap-$arch.zip")
        urls.add("https://github.com/termux/termux-packages/releases/download/bootstrap-2026.08.23-r1%2Bapt.android-7/bootstrap-$arch.zip")
        urls.add("https://github.com/termux/termux-packages/releases/download/bootstrap-2026.08.16-r1%2Bapt.android-7/bootstrap-$arch.zip")

        return urls.distinct()
    }

    /**
     * Downloads and installs the full official Termux bootstrap Linux rootfs.
     *
     * Files are extracted into a staging directory and only moved into place once everything succeeded, so
     * an interrupted or failed install never leaves a half populated prefix behind. Progress and failures
     * are reported through [onState]; [onLog] receives human readable log lines.
     */
    suspend fun installBootstrap(
        onLog: (String) -> Unit,
        onProgress: (Float) -> Unit = {},
        onState: (LinuxSetupState) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val arch = getArchitecture()
        val tempZip = File(context.cacheDir, "termux_bootstrap_$arch.zip")
        val stagingDir = File(filesDir, "usr-staging")
        var state = LinuxSetupState()

        fun emit(update: (LinuxSetupState) -> LinuxSetupState) {
            state = update(state)
            onState(state)
        }

        try {
            emit { it.copy(phase = LinuxSetupPhase.CHECKING, message = "Checking device", phaseProgress = null) }
            onLog("Detected architecture: $arch (${Build.SUPPORTED_ABIS.joinToString(", ")})")
            checkStorage(onLog)

            emit { it.copy(phase = LinuxSetupPhase.RESOLVING, message = "Looking for the latest Linux bootstrap") }
            onLog("Querying Termux bootstrap release mirrors")
            val urls = resolveBootstrapUrls(arch)
            checkCancelled()

            downloadBootstrap(urls, tempZip, onLog) { downloading ->
                emit(downloading)
                state.phaseProgress?.let(onProgress)
            }

            emit { it.copy(phase = LinuxSetupPhase.VERIFYING, message = "Verifying archive", phaseProgress = null) }
            verifyArchive(tempZip)

            emit { it.copy(phase = LinuxSetupPhase.EXTRACTING, message = "Installing files", phaseProgress = 0f) }
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            val symlinkFailures = extractBootstrap(tempZip, stagingDir, onLog) { done, total ->
                emit {
                    it.copy(
                        filesDone = done,
                        filesTotal = total,
                        phaseProgress = if (total > 0) done.toFloat() / total else null,
                        message = "Installing files ($done of $total)"
                    )
                }
            }
            if (symlinkFailures > 0) onLog("$symlinkFailures symlink(s) could not be created")

            emit { it.copy(phase = LinuxSetupPhase.CONFIGURING, message = "Finishing setup", phaseProgress = 0f) }
            if (prefixDir.exists()) prefixDir.deleteRecursively()
            if (!stagingDir.renameTo(prefixDir)) {
                throw LinuxSetupException(
                    LinuxSetupError.EXTRACT_FAILED,
                    "Could not move the installed files into ${prefixDir.absolutePath}"
                )
            }
            homeDir.mkdirs(); binDir.mkdirs(); libDir.mkdirs(); etcDir.mkdirs(); tmpDir.mkdirs()
            fixPermissions(onLog)
            setupConfigFiles(onLog)
            tempZip.delete()

            if (!isInstalled) {
                throw LinuxSetupException(
                    LinuxSetupError.EXTRACT_FAILED,
                    "Installation finished but no shell binary was found in ${binDir.absolutePath}"
                )
            }

            emit { it.copy(phase = LinuxSetupPhase.DONE, message = "Linux environment is ready", phaseProgress = 1f) }
            onLog("Embedded Linux environment is active ($arch)")
            true
        } catch (e: CancellationException) {
            tempZip.delete()
            stagingDir.deleteRecursively()
            onLog("Setup cancelled")
            emit {
                it.copy(
                    phase = LinuxSetupPhase.FAILED,
                    failedPhase = it.phase,
                    error = LinuxSetupError.CANCELLED,
                    errorDetail = null,
                    message = LinuxSetupError.CANCELLED.title
                )
            }
            throw e
        } catch (e: Exception) {
            val failure = classifyFailure(e)
            Log.w(tag, "Bootstrap install failed: ${failure.error} ${e.message}", e)
            tempZip.delete()
            stagingDir.deleteRecursively()
            setupConfigFiles(onLog)
            onLog("Setup failed: ${failure.error.title}: ${failure.message}")
            emit {
                it.copy(
                    phase = LinuxSetupPhase.FAILED,
                    failedPhase = it.phase,
                    error = failure.error,
                    errorDetail = failure.message,
                    message = failure.error.title
                )
            }
            false
        }
    }

    private fun classifyFailure(e: Exception): LinuxSetupException = when (e) {
        is LinuxSetupException -> e
        is UnknownHostException, is ConnectException -> LinuxSetupException(LinuxSetupError.NO_NETWORK, e.message ?: "Network unreachable", e)
        is SocketTimeoutException -> LinuxSetupException(LinuxSetupError.DOWNLOAD_INTERRUPTED, "Connection timed out", e)
        is ZipException -> LinuxSetupException(LinuxSetupError.CORRUPT_ARCHIVE, e.message ?: "Invalid zip", e)
        is IOException -> LinuxSetupException(
            if (e.message?.contains("ENOSPC", ignoreCase = true) == true || e.message?.contains("No space", ignoreCase = true) == true)
                LinuxSetupError.LOW_STORAGE else LinuxSetupError.EXTRACT_FAILED,
            e.message ?: "I/O error", e
        )
        else -> LinuxSetupException(LinuxSetupError.UNKNOWN, e.message ?: e.javaClass.simpleName, e)
    }

    private fun checkStorage(onLog: (String) -> Unit) {
        val free = filesDir.usableSpace
        onLog("Free storage: ${formatBytes(free)}")
        if (free < MIN_FREE_BYTES) {
            throw LinuxSetupException(
                LinuxSetupError.LOW_STORAGE,
                "${formatBytes(free)} free, at least ${formatBytes(MIN_FREE_BYTES)} is required"
            )
        }
    }

    private suspend fun checkCancelled() = currentCoroutineContext().ensureActive()

    /** Tries each mirror in turn, retrying transient failures, until one delivers a complete file. */
    private suspend fun downloadBootstrap(
        urls: List<String>,
        target: File,
        onLog: (String) -> Unit,
        report: ((LinuxSetupState) -> LinuxSetupState) -> Unit
    ) {
        var lastError: LinuxSetupException? = null
        var sawNetwork = false

        for ((index, urlStr) in urls.withIndex()) {
            for (attempt in 1..ATTEMPTS_PER_MIRROR) {
                checkCancelled()
                try {
                    onLog("Mirror ${index + 1}/${urls.size}, attempt $attempt: $urlStr")
                    report {
                        it.copy(
                            phase = LinuxSetupPhase.DOWNLOADING,
                            message = "Connecting to mirror ${index + 1} of ${urls.size}",
                            mirrorIndex = index + 1,
                            mirrorCount = urls.size,
                            bytesDownloaded = 0L,
                            totalBytes = 0L,
                            phaseProgress = null
                        )
                    }
                    downloadOnce(urlStr, target, report)
                    onLog("Download complete (${formatBytes(target.length())})")
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LinuxSetupException) {
                    if (e.error == LinuxSetupError.LOW_STORAGE) throw e
                    lastError = e
                    onLog("Mirror failed: ${e.message}")
                } catch (e: UnknownHostException) {
                    lastError = LinuxSetupException(LinuxSetupError.NO_NETWORK, e.message ?: "Unknown host", e)
                    onLog("Cannot resolve host: ${e.message}")
                    break // retrying the same host will not help
                } catch (e: IOException) {
                    sawNetwork = true
                    lastError = LinuxSetupException(LinuxSetupError.DOWNLOAD_INTERRUPTED, e.message ?: "I/O error", e)
                    onLog("Download error: ${e.message}")
                    if (attempt < ATTEMPTS_PER_MIRROR) delay(RETRY_DELAY_MS * attempt)
                }
            }
        }
        target.delete()
        val last = lastError
        throw when {
            last == null -> LinuxSetupException(LinuxSetupError.NO_MIRROR, "No bootstrap download is available for ${getArchitecture()}")
            last.error == LinuxSetupError.NO_NETWORK && !sawNetwork -> last
            last.error == LinuxSetupError.DOWNLOAD_INTERRUPTED -> last
            else -> LinuxSetupException(LinuxSetupError.NO_MIRROR, last.message ?: "No mirror responded", last)
        }
    }

    private suspend fun downloadOnce(
        urlStr: String,
        target: File,
        report: ((LinuxSetupState) -> LinuxSetupState) -> Unit
    ) {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("User-Agent", "Kodrix-Agent/2.0 (Android; Linux)")
            conn.connectTimeout = 15000
            conn.readTimeout = 45000
            conn.connect()
            if (conn.responseCode !in 200..299) {
                throw LinuxSetupException(LinuxSetupError.NO_MIRROR, "HTTP ${conn.responseCode} from $urlStr")
            }
            val total = conn.contentLengthLong
            if (total > 0 && filesDir.usableSpace < total * 4) {
                throw LinuxSetupException(
                    LinuxSetupError.LOW_STORAGE,
                    "${formatBytes(filesDir.usableSpace)} free, about ${formatBytes(total * 4)} is needed to download and unpack"
                )
            }

            var bytesRead = 0L
            var lastReport = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        checkCancelled()
                        output.write(buffer, 0, read)
                        bytesRead += read
                        val now = System.nanoTime()
                        if (now - lastReport > REPORT_INTERVAL_NANOS) {
                            lastReport = now
                            val snapshot = bytesRead
                            report {
                                it.copy(
                                    phase = LinuxSetupPhase.DOWNLOADING,
                                    message = "Downloading",
                                    bytesDownloaded = snapshot,
                                    totalBytes = total.coerceAtLeast(0L),
                                    phaseProgress = if (total > 0) snapshot.toFloat() / total else null
                                )
                            }
                        }
                    }
                }
            }
            if (total > 0 && bytesRead < total) {
                throw IOException("Connection closed after ${formatBytes(bytesRead)} of ${formatBytes(total)}")
            }
            val done = bytesRead
            report {
                it.copy(
                    bytesDownloaded = done,
                    totalBytes = maxOf(total, done),
                    phaseProgress = 1f,
                    message = "Download complete"
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun verifyArchive(zip: File) {
        if (!zip.exists() || zip.length() < MIN_ARCHIVE_BYTES) {
            throw LinuxSetupException(
                LinuxSetupError.CORRUPT_ARCHIVE,
                "Downloaded file is only ${formatBytes(if (zip.exists()) zip.length() else 0)}"
            )
        }
        val magic = ByteArray(4)
        zip.inputStream().use { it.read(magic) }
        if (magic[0] != 'P'.code.toByte() || magic[1] != 'K'.code.toByte()) {
            throw LinuxSetupException(LinuxSetupError.CORRUPT_ARCHIVE, "File is not a zip archive")
        }
    }

    /**
     * Extracts the bootstrap into [stagingDir] the same way the official Termux installer does: entries are
     * relative to the prefix, SYMLINKS.txt holds `target←linkPath` lines, and selected paths get the
     * executable bit. Returns the number of symlinks that could not be created.
     */
    private suspend fun extractBootstrap(
        zip: File,
        stagingDir: File,
        onLog: (String) -> Unit,
        onFiles: (done: Int, total: Int) -> Unit
    ): Int {
        val stagingRoot = stagingDir.canonicalPath + File.separator
        val symlinks = mutableListOf<Pair<String, String>>()
        var done = 0

        ZipFile(zip).use { zf ->
            val total = zf.size()
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                checkCancelled()
                val entry = entries.nextElement()
                val name = entry.name

                if (name == "SYMLINKS.txt") {
                    zf.getInputStream(entry).bufferedReader().useLines { lines ->
                        lines.filter { it.isNotBlank() }.forEach { line ->
                            val parts = line.split("←")
                            if (parts.size != 2) {
                                throw LinuxSetupException(LinuxSetupError.CORRUPT_ARCHIVE, "Malformed symlink entry: $line")
                            }
                            symlinks.add(parts[0] to parts[1])
                        }
                    }
                } else {
                    val outFile = File(stagingDir, name)
                    // Guard against entries such as ../../x escaping the staging directory.
                    if (!outFile.canonicalPath.startsWith(stagingRoot) && outFile.canonicalPath + File.separator != stagingRoot) {
                        throw LinuxSetupException(LinuxSetupError.CORRUPT_ARCHIVE, "Unsafe path in archive: $name")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        zf.getInputStream(entry).use { input -> outFile.outputStream().use { input.copyTo(it) } }
                        if (name.startsWith("bin/") || name.startsWith("libexec") ||
                            name.startsWith("lib/apt/apt-helper") || name.startsWith("lib/apt/methods")
                        ) {
                            outFile.setExecutable(true, false)
                        }
                    }
                }
                done++
                if (done % 25 == 0 || done == total) onFiles(done, total)
            }
            onFiles(done, total)
        }
        onLog("Extracted $done entries")

        var failures = 0
        for ((target, linkPath) in symlinks) {
            try {
                val link = File(stagingDir, linkPath)
                link.parentFile?.mkdirs()
                Os.symlink(target, link.absolutePath)
            } catch (e: Exception) {
                failures++
                Log.w(tag, "symlink $linkPath -> $target failed: ${e.message}")
            }
        }
        return failures
    }

    /**
     * Recursively grants executable permissions to all binaries.
     */
    fun fixPermissions(onLog: ((String) -> Unit)? = null) {
        try {
            binDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    file.setExecutable(true, false)
                    file.setReadable(true, false)
                    file.setWritable(true, true)
                }
            }
            libDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    file.setExecutable(true, false)
                    file.setReadable(true, false)
                }
            }
            // Run chmod 755 via OS process to ensure PIE flags work
            val pb = ProcessBuilder("/system/bin/sh", "-c", "chmod -R 755 \"${binDir.absolutePath}\" \"${libDir.absolutePath}\"")
            pb.start().waitFor()
        } catch (e: Exception) {
            Log.w(tag, "Failed to set chmod 755: ${e.message}")
        }
    }

    /**
     * Configures DNS, apt sources, and environment config files.
     */
    private fun setupConfigFiles(onLog: (String) -> Unit) {
        try {
            val resolvFile = File(etcDir, "resolv.conf")
            resolvFile.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")

            val aptDir = File(etcDir, "apt")
            aptDir.mkdirs()
            val sourcesList = File(aptDir, "sources.list")
            sourcesList.writeText("deb https://packages.termux.dev/apt/termux-main stable main\n")

            val profile = File(homeDir, ".profile")
            profile.writeText(
                """
                export PREFIX="${prefixDir.absolutePath}"
                export HOME="${homeDir.absolutePath}"
                export PATH="${binDir.absolutePath}:${'$'}PATH"
                export LD_LIBRARY_PATH="${libDir.absolutePath}"
                export TMPDIR="${tmpDir.absolutePath}"
                export TERM="xterm-256color"
                export LANG="en_US.UTF-8"
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.w(tag, "setupConfigFiles error: ${e.message}")
        }
    }

    /**
     * Builds standard environment variables for executing commands inside Embedded Termux.
     */
    fun buildEnvironment(workingDir: File = homeDir): Map<String, String> {
        val currentPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        val path = "${binDir.absolutePath}:$currentPath"
        val ldLibPath = "${libDir.absolutePath}:${System.getenv("LD_LIBRARY_PATH") ?: ""}"
        val nodeModules = "${libDir.absolutePath}/node_modules:${File(filesDir, "nodejs/node_modules").absolutePath}"

        return mapOf(
            "PREFIX" to prefixDir.absolutePath,
            "HOME" to workingDir.absolutePath,
            "PATH" to path,
            "LD_LIBRARY_PATH" to ldLibPath,
            "TMPDIR" to tmpDir.absolutePath,
            "SHELL" to "/system/bin/sh",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            // Output goes through a pipe, not a tty, so tools only emit color when asked to.
            "FORCE_COLOR" to "1",
            "CLICOLOR_FORCE" to "1",
            "LANG" to "en_US.UTF-8",
            "NODE_PATH" to nodeModules,
            "ANDROID_DATA" to "/data",
            "ANDROID_ROOT" to "/system"
        )
    }

    @Volatile
    private var runningProcess: Process? = null

    /** True while a command started through [execute] is still running. */
    val hasRunningProcess: Boolean get() = runningProcess?.isAlive == true

    /** Sends [text] followed by a newline to the stdin of the running command. */
    fun sendInput(text: String): Boolean {
        val process = runningProcess?.takeIf { it.isAlive } ?: return false
        return try {
            process.outputStream.write((text + "\n").toByteArray())
            process.outputStream.flush()
            true
        } catch (_: IOException) {
            false
        }
    }

    /** Stops the running command, the equivalent of pressing Ctrl+C. */
    fun interrupt() {
        val process = runningProcess ?: return
        process.destroy()
        // Give the process a moment to exit cleanly, then make sure it is gone.
        Thread {
            try {
                if (!process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) process.destroyForcibly()
            } catch (_: InterruptedException) {
            }
        }.start()
    }

    /**
     * Executes a command inside the Embedded Termux environment with live stdout/stderr streaming.
     * ALWAYS uses /system/bin/sh to prevent Android 10+ W^X EACCES permission denied errors.
     *
     * [onOutput] receives each completed line (which may still contain ANSI escapes and carriage returns).
     * [onPartial], when given, receives the unfinished current line whenever the output pauses, so progress
     * bars and prompts such as "Continue? [Y/n]" show up before a newline arrives.
     */
    suspend fun execute(
        command: String,
        workingDir: File = homeDir,
        onPartial: ((String) -> Unit)? = null,
        onOutput: (String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return@withContext 0

        // Always invoke /system/bin/sh to execute commands safely on Android 10+
        val pb = ProcessBuilder("/system/bin/sh", "-c", trimmed)
            .directory(workingDir)
            .redirectErrorStream(true)

        val env = pb.environment()
        env.putAll(buildEnvironment(workingDir))

        return@withContext try {
            val process = pb.start()
            runningProcess = process
            val reader = InputStreamReader(process.inputStream, Charsets.UTF_8)
            val line = StringBuilder()
            val chunk = CharArray(4096)
            while (true) {
                val n = reader.read(chunk)
                if (n < 0) break
                for (i in 0 until n) {
                    val c = chunk[i]
                    if (c == '\n') {
                        onOutput(line.toString().trimEnd('\r'))
                        line.setLength(0)
                    } else {
                        line.append(c)
                    }
                }
                if (line.isNotEmpty() && onPartial != null && !reader.ready()) onPartial(line.toString())
            }
            if (line.isNotEmpty()) onOutput(line.toString())
            val code = process.waitFor()
            runningProcess = null
            code
        } catch (e: Exception) {
            runningProcess = null
            onOutput("Error executing command: ${e.message}")
            -1
        }
    }
}
