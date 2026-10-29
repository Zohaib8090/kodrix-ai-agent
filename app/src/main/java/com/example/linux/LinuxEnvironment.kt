package com.example.linux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ExecResult(val exitCode: Int, val output: String, val timedOut: Boolean, val truncated: Boolean)

class LinuxException(message: String) : Exception(message)

/**
 * A real Linux userland (Alpine) running inside the app, no root needed.
 *
 * Why this design: Android 10+ forbids executing files from an app's data directory, so Termux-style
 * "unpack binaries and run them" fails with EACCES. What does work (verified on an Android 15 emulator,
 * targetSdk 36):
 *   - `proot` ships as a native library (libproot.so) and its loader as libproot-loader.so, both in
 *     nativeLibraryDir, which Android lets us execute;
 *   - proot then runs the guest's binaries itself (it maps them into memory instead of exec'ing them);
 *   - proot's two small shared libraries are loaded from the data directory via LD_LIBRARY_PATH.
 *
 * [assets] opens a packaged asset by path, e.g. "linux/x86_64/libtalloc.so.2".
 */
class LinuxEnvironment(
    filesDir: File,
    private val nativeLibDir: File,
    private val abi: String,
    private val fs: FsOps = AndroidFsOps,
    private val killer: (Int) -> Unit = { pid -> try { android.system.Os.kill(pid, 9) } catch (_: Exception) {} },
    private val assets: (String) -> InputStream,
) {
    private val base = File(filesDir, "linux")
    val rootfs = File(base, "rootfs")
    private val libs = File(base, "lib")
    private val tmp = File(base, "tmp")
    private val marker = File(base, ".installed")

    private val proot get() = File(nativeLibDir, "libproot.so")
    private val loader get() = File(nativeLibDir, "libproot-loader.so")

    val isInstalled: Boolean get() = marker.exists() && File(rootfs, "bin/busybox").exists()

    val isSupported: Boolean get() = proot.exists() && loader.exists()

    /** Alpine's name for this CPU, or null when unsupported. */
    val alpineArch: String?
        get() = when (abi) {
            "arm64-v8a" -> "aarch64"
            "x86_64" -> "x86_64"
            else -> null
        }

    /** Copies proot's two small helper libraries out of the APK (idempotent). */
    fun prepareRuntime() {
        libs.mkdirs()
        tmp.mkdirs()
        for (name in listOf("libtalloc.so.2", "libandroid-shmem.so")) {
            val out = File(libs, name)
            if (out.exists() && out.length() > 0) continue
            assets("linux/$abi/$name").use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        }
    }

    /** Downloads Alpine's minirootfs, verifies its SHA-256 and unpacks it. Returns a human-readable summary. */
    suspend fun install(onLog: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        val arch = alpineArch ?: throw LinuxException("This CPU ($abi) is not supported. Need arm64 or x86_64.")
        if (!isSupported) throw LinuxException("proot is missing from this build.")
        onLog("Looking up the latest Alpine release…")
        val index = httpGet("$ALPINE_BASE/$arch/latest-releases.yaml").decodeToString()
        val (file, sha) = parseLatestReleases(index)
            ?: throw LinuxException("Could not find the Alpine minirootfs in the release index.")
        onLog("Downloading $file…")
        val tgz = File(base, "download.tgz").also { base.mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open("$ALPINE_BASE/$arch/$file")
        conn.inputStream.use { i -> tgz.outputStream().use { o ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); digest.update(buf, 0, n) }
        } }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(sha, ignoreCase = true)) {
            tgz.delete()
            throw LinuxException("Download failed its SHA-256 check; refusing to install.")
        }
        onLog("Verified. Unpacking…")
        val summary = tgz.inputStream().use { installFrom(it, onLog) }
        tgz.delete()
        summary
    }

    /** Unpacks a rootfs tarball stream. Split from [install] so tests can use a local file. */
    suspend fun installFrom(tarGz: InputStream, onLog: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        prepareRuntime()
        marker.delete()
        rootfs.deleteRecursively()
        val stats = TarGzExtractor(fs).extract(tarGz, rootfs)
        File(rootfs, "etc").mkdirs()
        File(rootfs, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        File(rootfs, "root").mkdirs()
        if (!File(rootfs, "bin/busybox").exists()) throw LinuxException("The archive did not contain a Linux userland (no /bin/busybox).")
        marker.writeText("ok")
        "Installed: ${stats.files} files, ${stats.symlinks} links" +
            if (stats.skipped.isNotEmpty()) ", ${stats.skipped.size} skipped" else ""
    }

    /** The exact command line and environment used to run [command] in the guest. Pure, so it is unit-tested. */
    fun buildCommand(
        command: String,
        workDir: String = "/root",
        binds: List<Pair<String, String>> = emptyList(),
    ): Pair<List<String>, Map<String, String>> {
        val bindArgs = binds.flatMap { (host, guest) -> listOf("-b", "$host:$guest") }
        val argv = listOf(
            proot.absolutePath,
            "-r", rootfs.absolutePath,
            "-0",                    // pretend to be root inside
            "--link2symlink",        // hard links are not allowed in app storage
            "--kill-on-exit",
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
        ) + bindArgs + listOf(
            "-w", workDir,
            "/usr/bin/env", "-i",
            "HOME=/root", "USER=root", "TERM=xterm-256color", "LANG=C.UTF-8", "TMPDIR=/tmp",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "/bin/sh", "-c", command,
        )
        val env = mapOf(
            "LD_LIBRARY_PATH" to libs.absolutePath,
            "PROOT_LOADER" to loader.absolutePath,
            "PROOT_TMP_DIR" to tmp.absolutePath,
        )
        return argv to env
    }

    /** Runs a shell command in the guest. Output is capped; the process is killed on timeout. */
    suspend fun exec(
        command: String,
        timeoutSeconds: Long = 60,
        maxOutputChars: Int = 12_000,
        workDir: String = "/root",
        binds: List<Pair<String, String>> = emptyList(),
    ): ExecResult = withContext(Dispatchers.IO) {
        if (!isInstalled) throw LinuxException("Linux is not installed yet.")
        prepareRuntime()
        val (argv, env) = buildCommand(command, workDir, binds)
        val pb = ProcessBuilder(argv).redirectErrorStream(true)
        pb.environment().putAll(env)
        val proc = pb.start()
        val collector = OutputCollector(maxOutputChars * 2)
        val reader = Thread { collector.drain(proc.inputStream) }.also { it.isDaemon = true; it.start() }
        var finished = false
        try {
            // Interruptible so cancelling the agent's job also stops the command.
            finished = runInterruptible { proc.waitFor(timeoutSeconds, TimeUnit.SECONDS) }
        } finally {
            if (!finished) killTree(proc)
        }
        reader.join(2000)
        val text = collector.snapshot()
        var truncated = collector.truncated
        val shown = if (text.length > maxOutputChars) {
            truncated = true
            text.take(maxOutputChars / 2) + "\n…[output truncated]…\n" + text.takeLast(maxOutputChars / 2)
        } else text
        ExecResult(if (finished) proc.exitValue() else -1, shown, !finished, truncated)
    }

    /** Kills the guest's whole process tree (leaves first), then proot itself. */
    private fun killTree(proc: Process) {
        val pid = ProcessTree.pidOf(proc)
        if (pid != null) ProcessTree.descendants(pid).asReversed().forEach { killer(it) }
        proc.destroyForcibly()
        proc.waitFor(5, TimeUnit.SECONDS)
    }

    companion object {
        const val ALPINE_BASE = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases"

        /** Picks the minirootfs file name and its sha256 from Alpine's latest-releases.yaml. */
        fun parseLatestReleases(yaml: String): Pair<String, String>? {
            var file: String? = null
            var sha: String? = null
            var inMini = false
            for (raw in yaml.lines()) {
                val line = raw.trim().removePrefix("- ").trim()
                if (line.startsWith("title:")) inMini = false
                if (line.startsWith("flavor:")) inMini = line.removePrefix("flavor:").trim() == "alpine-minirootfs"
                if (inMini && line.startsWith("file:")) file = line.removePrefix("file:").trim()
                if (inMini && line.startsWith("sha256:")) sha = line.removePrefix("sha256:").trim()
            }
            return if (file != null && sha != null) file to sha else null
        }

        private fun open(url: String): HttpURLConnection {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.setRequestProperty("User-Agent", "Kodrix-Agent/2")
            if (c.responseCode !in 200..299) throw LinuxException("HTTP ${c.responseCode} for $url")
            return c
        }

        private fun httpGet(url: String): ByteArray = open(url).inputStream.use { it.readBytes() }
    }
}

/**
 * Collects a process's output on a background thread.
 * [drain] must never throw: when a process is killed (e.g. on timeout) Android closes the pipe under the
 * blocked reader and read() throws IOException. An exception escaping a thread crashes the whole app.
 */
internal class OutputCollector(private val maxChars: Int) {
    private val sb = StringBuilder()
    @Volatile var truncated = false
        private set

    fun drain(input: InputStream) {
        try {
            input.bufferedReader().use { r ->
                val buf = CharArray(4096)
                while (true) {
                    val n = r.read(buf)
                    if (n < 0) break
                    synchronized(sb) {
                        if (sb.length < maxChars) sb.append(buf, 0, n) else truncated = true
                    }
                }
            }
        } catch (_: java.io.IOException) {
            // Pipe closed because the process was killed. Keep whatever was read so far.
        }
    }

    fun snapshot(): String = synchronized(sb) { sb.toString() }
}
