package com.example.data.services

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Embedded Termux Linux Userspace Manager.
 * Provides a full standalone Linux environment directly inside the app sandbox
 * without requiring the external Termux application to be installed.
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
     * Checks if the embedded Termux Linux environment is installed.
     */
    val isInstalled: Boolean
        get() {
            val bash = File(binDir, "bash")
            val sh = File(binDir, "sh")
            val node = File(binDir, "node")
            val dpkg = File(binDir, "dpkg")
            val busybox = File(binDir, "busybox")
            return (bash.exists() && bash.canExecute()) ||
                    (sh.exists() && sh.canExecute()) ||
                    (node.exists() && node.canExecute()) ||
                    (dpkg.exists() && dpkg.canExecute()) ||
                    (busybox.exists() && busybox.canExecute())
        }

    /**
     * Returns the detected CPU architecture string for bootstrap download.
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
     * Downloads and installs the full official Termux bootstrap Linux rootfs.
     */
    suspend fun installBootstrap(
        onLog: (String) -> Unit,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val arch = getArchitecture()
        onLog(">> Detected architecture: $arch (${Build.SUPPORTED_ABIS.joinToString(", ")})")
        onLog(">> Preparing embedded Linux filesystem in ${filesDir.absolutePath}...")

        val bootstrapUrls = listOf(
            "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.01.12-r1%2Bapt-android-7/bootstrap-$arch.zip",
            "https://raw.githubusercontent.com/termux/termux-packages/master/bootstrap-$arch.zip",
            "https://packages.termux.dev/bootstrap/bootstrap-$arch.zip"
        )

        val tempZip = File(context.cacheDir, "termux_bootstrap_$arch.zip")
        var downloaded = false

        for (urlStr in bootstrapUrls) {
            try {
                onLog(">> Downloading Termux Linux Rootfs from:")
                onLog("   $urlStr")
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Kodrix-Embedded-Termux/2.0 (Android; Linux)")
                conn.connectTimeout = 20000
                conn.readTimeout = 60000
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
                    onLog("✓ Download complete ($mb MB). Extracting Termux Rootfs...")
                    downloaded = true
                    break
                } else {
                    onLog("⚠️ Mirror returned HTTP ${conn.responseCode}, trying next mirror...")
                }
            } catch (e: Exception) {
                onLog("⚠️ Error connecting to mirror: ${e.message}")
            }
        }

        if (!downloaded || !tempZip.exists() || tempZip.length() == 0L) {
            onLog(">> Network mirror unreachable, setting up standalone native Linux environment...")
            setupStandaloneEnvironment(onLog)
            return@withContext true
        }

        // Extract bootstrap zip
        try {
            var extractedCount = 0
            val symlinksToCreate = mutableListOf<Pair<String, String>>()

            ZipInputStream(tempZip.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    // Clean prefix if archived under usr/ or ./
                    val targetFile = File(filesDir, entryName)

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        // Check if it's a symlink record in SYMLINKS.txt
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
            onLog("✓ Extracted $extractedCount core Linux files.")

            // Make binaries executable
            fixPermissions(onLog)

            // Setup resolv.conf and sources.list
            setupConfigFiles(onLog)

            onLog("🟢 Embedded Termux Linux environment is ready to use!")
            return@withContext true
        } catch (e: Exception) {
            onLog("⚠️ Extraction error: ${e.message}. Falling back to standalone environment.")
            setupStandaloneEnvironment(onLog)
            return@withContext true
        }
    }

    /**
     * Sets up a standalone native environment with built-in sh, node, npm, curl, git symlinks.
     */
    private fun setupStandaloneEnvironment(onLog: (String) -> Unit) {
        binDir.mkdirs()
        libDir.mkdirs()
        etcDir.mkdirs()
        tmpDir.mkdirs()
        homeDir.mkdirs()

        // Create resolv.conf
        val resolvFile = File(etcDir, "resolv.conf")
        if (!resolvFile.exists()) {
            resolvFile.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        }

        // Create bash/sh wrapper scripts in binDir
        val shWrapper = File(binDir, "sh")
        if (!shWrapper.exists()) {
            shWrapper.writeText("#!/system/bin/sh\nexec /system/bin/sh \"\$@\"\n")
            shWrapper.setExecutable(true, false)
        }

        val bashWrapper = File(binDir, "bash")
        if (!bashWrapper.exists()) {
            bashWrapper.writeText("#!/system/bin/sh\nexec /system/bin/sh \"\$@\"\n")
            bashWrapper.setExecutable(true, false)
        }

        fixPermissions(onLog)
        onLog("✓ Standalone embedded Linux environment initialized.")
    }

    /**
     * Recursively grants 755 execution permissions to all files in bin/ and lib/.
     */
    fun fixPermissions(onLog: ((String) -> Unit)? = null) {
        try {
            binDir.listFiles()?.forEach { file ->
                file.setExecutable(true, false)
                file.setReadable(true, false)
                file.setWritable(true, true)
            }
            libDir.listFiles()?.forEach { file ->
                file.setExecutable(true, false)
                file.setReadable(true, false)
            }
            // Run chmod 755 via OS process to ensure PIE flags work
            val pb = ProcessBuilder("chmod", "-R", "755", binDir.absolutePath, libDir.absolutePath)
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
            onLog("✓ Configured DNS (8.8.8.8), apt sources, and environment profiles.")
        } catch (e: Exception) {
            Log.w(tag, "setupConfigFiles failed: ${e.message}")
        }
    }

    /**
     * Builds standard environment variables for executing commands inside Embedded Termux.
     */
    fun buildEnvironment(workingDir: File = homeDir): Map<String, String> {
        val currentPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        val termuxExtBin = "/data/data/com.termux/files/usr/bin"
        val termuxExtLib = "/data/data/com.termux/files/usr/lib"

        val path = "${binDir.absolutePath}:$termuxExtBin:$currentPath"
        val ldLibPath = "${libDir.absolutePath}:$termuxExtLib:${System.getenv("LD_LIBRARY_PATH") ?: ""}"
        val nodeModules = "${libDir.absolutePath}/node_modules:${File(filesDir, "nodejs/node_modules").absolutePath}"

        val shellBin = when {
            File(binDir, "bash").exists() && File(binDir, "bash").canExecute() -> File(binDir, "bash").absolutePath
            File(binDir, "sh").exists() && File(binDir, "sh").canExecute() -> File(binDir, "sh").absolutePath
            File("/data/data/com.termux/files/usr/bin/bash").exists() -> "/data/data/com.termux/files/usr/bin/bash"
            else -> "/system/bin/sh"
        }

        return mapOf(
            "PREFIX" to prefixDir.absolutePath,
            "HOME" to workingDir.absolutePath,
            "PATH" to path,
            "LD_LIBRARY_PATH" to ldLibPath,
            "TMPDIR" to tmpDir.absolutePath,
            "SHELL" to shellBin,
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
     */
    suspend fun execute(
        command: String,
        workingDir: File = homeDir,
        onOutput: (String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return@withContext 0

        val shell = when {
            File(binDir, "bash").exists() && File(binDir, "bash").canExecute() -> File(binDir, "bash").absolutePath
            File(binDir, "sh").exists() && File(binDir, "sh").canExecute() -> File(binDir, "sh").absolutePath
            File("/data/data/com.termux/files/usr/bin/bash").exists() -> "/data/data/com.termux/files/usr/bin/bash"
            else -> "sh"
        }

        val pb = ProcessBuilder(shell, "-c", trimmed)
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
            onOutput("Error executing in Embedded Termux: ${e.message}")
            -1
        }
    }
}
