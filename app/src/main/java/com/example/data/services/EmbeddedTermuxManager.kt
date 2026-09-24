package com.example.data.services

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Embedded Termux Linux Userspace Manager.
 * Provides a full standalone Linux environment directly inside the app sandbox.
 */
class EmbeddedTermuxManager(private val context: Context) {

    private val tag = "EmbeddedTermux"

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

        return urls
    }

    /**
     * Downloads and installs the full official Termux bootstrap Linux rootfs.
     */
    suspend fun installBootstrap(
        onLog: (String) -> Unit,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val arch = getArchitecture()
        onLog(">> Detected architecture: $arch (${Build.SUPPORTED_ABIS.joinToString(", ")})")
        onLog(">> Querying live Termux bootstrap release mirrors...")

        val bootstrapUrls = resolveBootstrapUrls(arch)
        val tempZip = File(context.cacheDir, "termux_bootstrap_$arch.zip")
        var downloaded = false

        for (urlStr in bootstrapUrls) {
            try {
                onLog(">> Connecting to: $urlStr")
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Kodrix-Agent/2.0 (Android; Linux)")
                conn.connectTimeout = 15000
                conn.readTimeout = 45000
                conn.connect()

                if (conn.responseCode in 200..299) {
                    val totalBytes = conn.contentLengthLong
                    var bytesRead = 0L

                    conn.inputStream.use { input ->
                        tempZip.outputStream().use { output ->
                            val buffer = ByteArray(32768)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesRead += read
                                if (totalBytes > 0) {
                                    val progress = bytesRead.toFloat() / totalBytes
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                    val mb = bytesRead / (1024 * 1024)
                    onLog("✓ Download complete ($mb MB). Extracting full Linux rootfs...")
                    downloaded = true
                    break
                } else {
                    onLog("⚠️ Mirror returned HTTP ${conn.responseCode}, checking next mirror...")
                }
            } catch (e: Exception) {
                onLog("⚠️ Mirror error: ${e.message}")
            }
        }

        if (!downloaded || !tempZip.exists() || tempZip.length() == 0L) {
            onLog(">> Mirrors currently unreachable. Initialized standard shell environment.")
            setupConfigFiles(onLog)
            return@withContext false
        }

        // Extract bootstrap zip
        try {
            var extractedCount = 0
            val symlinksToCreate = mutableListOf<Pair<String, String>>()

            ZipInputStream(tempZip.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    val targetFile = File(filesDir, entryName)

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        if (entryName == "SYMLINKS.txt") {
                            val content = zis.bufferedReader().readText()
                            content.lines().forEach { line ->
                                val parts = line.split("←")
                                if (parts.size == 2) {
                                    symlinksToCreate.add(parts[0].trim() to parts[1].trim())
                                }
                            }
                        } else {
                            targetFile.parentFile?.mkdirs()
                            targetFile.outputStream().use { fos ->
                                zis.copyTo(fos)
                            }
                            extractedCount++
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            tempZip.delete()
            onLog("✓ Extracted $extractedCount core Linux packages and binaries.")

            // Create symlinks if any
            symlinksToCreate.forEach { (dest, src) ->
                try {
                    val destFile = File(filesDir, dest)
                    val srcFile = File(filesDir, src)
                    destFile.parentFile?.mkdirs()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        android.system.Os.symlink(srcFile.absolutePath, destFile.absolutePath)
                    }
                } catch (_: Exception) {}
            }

            // Make all binaries executable
            fixPermissions(onLog)

            // Setup resolv.conf and sources.list
            setupConfigFiles(onLog)

            onLog("Full Embedded Termux Linux Userspace is ACTIVE (aarch64)!")
            return@withContext true
        } catch (e: Exception) {
            onLog("⚠️ Extraction error: ${e.message}")
            setupConfigFiles(onLog)
            return@withContext false
        }
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
            "LANG" to "en_US.UTF-8",
            "NODE_PATH" to nodeModules,
            "ANDROID_DATA" to "/data",
            "ANDROID_ROOT" to "/system"
        )
    }

    /**
     * Executes a command inside the Embedded Termux environment with live stdout/stderr streaming.
     * ALWAYS uses /system/bin/sh to prevent Android 10+ W^X EACCES permission denied errors.
     */
    suspend fun execute(
        command: String,
        workingDir: File = homeDir,
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
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { onOutput(it) }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            onOutput("Error executing command: ${e.message}")
            -1
        }
    }
}
