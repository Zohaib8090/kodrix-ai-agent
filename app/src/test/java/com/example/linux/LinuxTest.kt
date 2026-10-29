package com.example.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

/** Tiny tar writer so tests can build archives with exactly the features/attacks we care about. */
private class TarBuilder {
    val out = ByteArrayOutputStream()

    fun entry(name: String, type: Char = '0', data: ByteArray = ByteArray(0), link: String = "", mode: Int = 0b110_100_100): TarBuilder {
        val h = ByteArray(512)
        fun put(off: Int, s: String, len: Int) = s.toByteArray().copyInto(h, off, 0, minOf(len, s.toByteArray().size))
        put(0, name, 100)
        put(100, "%07o".format(mode), 8)
        put(124, "%011o".format(data.size), 12)
        h[156] = type.code.toByte()
        put(157, link, 100)
        put(257, "ustar", 6)
        for (i in 148 until 156) h[i] = ' '.code.toByte()
        val sum = h.sumOf { it.toInt() and 0xFF }
        put(148, "%06o".format(sum), 7)
        h[154] = 0; h[155] = ' '.code.toByte()
        out.write(h)
        out.write(data)
        out.write(ByteArray((512 - data.size % 512) % 512))
        return this
    }

    fun gzip(): ByteArray {
        out.write(ByteArray(1024))
        val bo = ByteArrayOutputStream()
        GZIPOutputStream(bo).use { it.write(out.toByteArray()) }
        return bo.toByteArray()
    }
}

class TarGzExtractorTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun extract(tar: TarBuilder): Pair<File, ExtractStats> {
        val dest = File(tmp.root, "rootfs")
        val st = TarGzExtractor(JvmFsOps).extract(ByteArrayInputStream(tar.gzip()), dest)
        return dest to st
    }

    @Test
    fun extractsFilesDirsSymlinksAndHardlinks() {
        val t = TarBuilder()
            .entry("bin/", '5', mode = 0b101_101_101) // r-xr-xr-x: must become owner-writable
            .entry("bin/busybox", data = "BUSYBOX".toByteArray(), mode = 0b111_101_101)
            .entry("bin/ls", '2', link = "/bin/busybox")
            .entry("bin/hl", '1', link = "bin/busybox")
        val (d, st) = extract(t)
        assertEquals("BUSYBOX", File(d, "bin/busybox").readText())
        assertTrue(Files.isSymbolicLink(File(d, "bin/ls").toPath()))
        assertEquals("/bin/busybox", Files.readSymbolicLink(File(d, "bin/ls").toPath()).toString())
        assertEquals("BUSYBOX", File(d, "bin/hl").readText())
        assertTrue("dir must stay writable for the app", File(d, "bin").canWrite())
        assertTrue("exec bit preserved", File(d, "bin/busybox").canExecute())
        assertEquals(2, st.files + st.hardlinks)
        assertEquals(1, st.symlinks)
    }

    @Test
    fun rejectsParentDirectoryTraversal() {
        val t = TarBuilder().entry("../evil.txt", data = "x".toByteArray())
        try { extract(t); org.junit.Assert.fail("accepted ../") } catch (e: TarException) { /* expected */ }
        assertFalse(File(tmp.root, "evil.txt").exists())
    }

    @Test
    fun refusesToWriteThroughASymlinkThatEscapes() {
        val outside = tmp.newFolder("outside")
        val t = TarBuilder()
            .entry("a", '2', link = outside.absolutePath)
            .entry("a/pwned.txt", data = "x".toByteArray())
        val (_, st) = extract(t)
        assertFalse(File(outside, "pwned.txt").exists())
        assertTrue(st.skipped.contains("a/pwned.txt"))
    }

    @Test
    fun absoluteEntryNamesStayInsideRoot() {
        val t = TarBuilder().entry("/etc/passwd", data = "root".toByteArray())
        val (d, _) = extract(t)
        assertEquals("root", File(d, "etc/passwd").readText())
    }

    @Test
    fun handlesLongNamesViaGnuLongNameHeader() {
        val long = "usr/share/" + "d".repeat(120) + "/file.txt"
        val t = TarBuilder()
            .entry("././@LongLink", 'L', data = (long + "\u0000").toByteArray())
            .entry("short", data = "hello".toByteArray())
        val (d, _) = extract(t)
        assertEquals("hello", File(d, long).readText())
    }

    @Test
    fun handlesPaxPathHeader() {
        val rec = "path=pax/dir/real-name.txt\n"
        val len = (rec.length + 3 + 1) // "NN " + rec; computed below to be self-consistent
        var total = rec.length + 3
        if (total.toString().length != 2) total = rec.length + 1 + total.toString().length
        val line = "$total $rec"
        val t = TarBuilder()
            .entry("PaxHeader", 'x', data = line.toByteArray())
            .entry("ignored-name", data = "pax".toByteArray())
        val (d, _) = extract(t)
        assertEquals("pax", File(d, "pax/dir/real-name.txt").readText())
    }

    @Test
    fun skipsDeviceNodesWithoutFailing() {
        val t = TarBuilder().entry("dev/null", '3').entry("ok.txt", data = "1".toByteArray())
        val (d, st) = extract(t)
        assertTrue(File(d, "ok.txt").exists()); assertTrue(st.skipped.contains("dev/null"))
    }

    @Test
    fun extractsRealAlpineRootfsWhenAvailable() {
        val path = System.getenv("KODRIX_ALPINE_TGZ")
        assumeTrue("set KODRIX_ALPINE_TGZ to run", path != null && File(path).exists())
        val dest = File(tmp.root, "alpine")
        val st = File(path!!).inputStream().use { TarGzExtractor(JvmFsOps).extract(it, dest) }
        assertTrue(File(dest, "bin/busybox").length() > 100_000)
        assertTrue(Files.isSymbolicLink(File(dest, "bin/sh").toPath()))
        assertTrue(File(dest, "etc/os-release").readText().contains("Alpine"))
        assertTrue("files=${st.files}", st.files > 20)
    }
}

class LinuxEnvironmentTest {
    @get:Rule val tmp = TemporaryFolder()

    private val yaml = """
        -
          title: "Netboot"
          flavor: alpine-netboot
          file: alpine-netboot-3.24.3-x86_64.tar.gz
          sha256: aaaa
        -
          title: "Mini root filesystem"
          desc: |
            Minimal root filesystem.
          arch: x86_64
          flavor: alpine-minirootfs
          file: alpine-minirootfs-3.24.3-x86_64.tar.gz
          size: 3704080
          sha256: f8e1cafba9b1b283b5d30d06f9885c8d2bc161ffe9083ade8e2e157813a53407
          sha512: 64c4
        -
          title: "Standard"
          flavor: alpine-standard
          file: alpine-standard-3.24.3-x86_64.iso
          sha256: bbbb
    """.trimIndent()

    @Test
    fun parsesMinirootfsFromReleaseIndex() {
        val (file, sha) = LinuxEnvironment.parseLatestReleases(yaml)!!
        assertEquals("alpine-minirootfs-3.24.3-x86_64.tar.gz", file)
        assertEquals("f8e1cafba9b1b283b5d30d06f9885c8d2bc161ffe9083ade8e2e157813a53407", sha)
    }

    @Test
    fun parserReturnsNullWhenNoMinirootfs() {
        assertEquals(null, LinuxEnvironment.parseLatestReleases("- \n  flavor: alpine-standard\n  file: x.iso\n  sha256: y"))
    }

    @Test
    fun commandUsesNativeLibProotAndAndroidSafeSettings() {
        val env = LinuxEnvironment(tmp.root, File("/native"), "x86_64") { throw AssertionError() }
        val (argv, e) = env.buildCommand("echo hi; ls")
        assertEquals("/native/libproot.so", argv[0])
        assertTrue(argv.containsAll(listOf("-0", "--link2symlink", "--kill-on-exit")))
        assertEquals("echo hi; ls", argv.last())
        assertEquals(listOf("/bin/sh", "-c"), argv.takeLast(3).take(2))
        assertTrue("PATH must be a Linux PATH, not Android's", argv.any { it.startsWith("PATH=/usr/") })
        assertEquals("/native/libproot-loader.so", e["PROOT_LOADER"])
        assertTrue(e.getValue("LD_LIBRARY_PATH").endsWith("linux/lib"))
    }

    @Test
    fun cpuSupportMapping() {
        fun arch(abi: String) = LinuxEnvironment(tmp.root, File("/n"), abi) { throw AssertionError() }.alpineArch
        assertEquals("aarch64", arch("arm64-v8a")); assertEquals("x86_64", arch("x86_64")); assertEquals(null, arch("armeabi-v7a"))
    }

    @Test
    fun notInstalledUntilRootfsAndMarkerExist() {
        val env = LinuxEnvironment(tmp.root, File("/n"), "x86_64") { throw AssertionError() }
        assertFalse(env.isInstalled)
    }

    @Test
    fun installFromRejectsArchivesWithoutALinuxUserland() = kotlinx.coroutines.test.runTest {
        val dir = tmp.newFolder("native"); File(dir, "libproot.so").writeText("x"); File(dir, "libproot-loader.so").writeText("x")
        val env = LinuxEnvironment(tmp.root, dir, "x86_64", fs = JvmFsOps) { "lib".byteInputStream() }
        val tar = TarBuilder().entry("README", data = "hi".toByteArray()).gzip()
        try { env.installFrom(ByteArrayInputStream(tar)); org.junit.Assert.fail() } catch (e: LinuxException) { assertTrue(e.message!!.contains("busybox")) }
        assertFalse(env.isInstalled)
    }
}

/**
 * Runs LinuxEnvironment.exec against a fake "proot" (a host shell script that runs the final shell command),
 * so process handling is tested on the JVM: exit codes, output cap, timeout kill without crashing.
 */
class LinuxExecProcessTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun env(): LinuxEnvironment {
        val native = tmp.newFolder("native")
        val fake = File(native, "libproot.so")
        fake.writeText("#!/bin/sh\nfor a; do last=\"\$a\"; done\nexec /bin/sh -c \"\$last\"\n")
        fake.setExecutable(true)
        File(native, "libproot-loader.so").writeText("x")
        val e = LinuxEnvironment(tmp.root, native, "x86_64", fs = JvmFsOps, killer = { pid -> Runtime.getRuntime().exec(arrayOf("kill", "-9", pid.toString())).waitFor() }) { "lib".byteInputStream() }
        // mark installed
        File(tmp.root, "linux/rootfs/bin").mkdirs(); File(tmp.root, "linux/rootfs/bin/busybox").writeText("x"); File(tmp.root, "linux/.installed").writeText("ok")
        return e
    }

    @Test fun capturesOutputAndExitCode() = kotlinx.coroutines.test.runTest {
        val r = env().exec("echo hello; echo err 1>&2; exit 3")
        assertEquals(3, r.exitCode); assertFalse(r.timedOut)
        assertTrue(r.output.contains("hello") && r.output.contains("err"))
    }

    @Test fun timeoutKillsTheProcessWithoutCrashingThePlatform() = kotlinx.coroutines.test.runTest {
        val uncaught = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
        try {
            val started = System.currentTimeMillis()
            val r = env().exec("sleep 30", timeoutSeconds = 1)
            Thread.sleep(500) // give the reader thread time to hit the closed pipe
            assertTrue(r.timedOut); assertEquals(-1, r.exitCode)
            assertTrue("must return promptly", System.currentTimeMillis() - started < 10_000)
            assertEquals("reader thread must not throw: $uncaught", 0, uncaught.size)
        } finally { Thread.setDefaultUncaughtExceptionHandler(old) }
    }

    @Test fun hugeOutputIsTruncatedWithMarker() = kotlinx.coroutines.test.runTest {
        val r = env().exec("yes | head -c 500000", maxOutputChars = 1000)
        assertTrue(r.truncated); assertTrue(r.output.length < 1200); assertTrue(r.output.contains("truncated"))
    }

    @Test fun refusesToRunWhenNotInstalled() = kotlinx.coroutines.test.runTest {
        val native = tmp.newFolder("n2")
        val e = LinuxEnvironment(tmp.root, native, "x86_64", fs = JvmFsOps) { "x".byteInputStream() }
        try { e.exec("echo hi"); org.junit.Assert.fail() } catch (ex: LinuxException) { assertTrue(ex.message!!.contains("not installed")) }
    }
}

class OutputCollectorTest {
    private class DiesMidRead(private val first: String) : java.io.InputStream() {
        private val data = first.toByteArray(); private var i = 0
        override fun read(): Int = if (i < data.size) data[i++].toInt() and 0xFF else throw java.io.IOException("read failed: EBADF (Bad file descriptor)")
    }

    @Test fun streamClosedByKillDoesNotEscapeAndKeepsEarlierOutput() {
        val c = OutputCollector(1000)
        c.drain(DiesMidRead("partial output"))   // must not throw
        assertEquals("partial output", c.snapshot())
    }

    @Test fun capsStoredOutput() {
        val c = OutputCollector(100)
        c.drain(ByteArrayInputStream("x".repeat(10_000).toByteArray()))
        assertTrue(c.truncated); assertTrue(c.snapshot().length < 5000)
    }
}

class ProcessTreeTest {
    private fun alive(marker: String): Boolean = File("/proc").listFiles().orEmpty().any { d ->
        d.name.toIntOrNull() != null && try { File(d, "cmdline").readText().contains(marker) } catch (_: Exception) { false }
    }

    @Test fun parsesPpidEvenWhenCommandNameHasParensAndSpaces() {
        assertEquals(42, ProcessTree.parsePpid("123 (my (weird) cmd) S 42 123 123 0 -1"))
        assertEquals(1, ProcessTree.parsePpid("5 (sh) S 1 5 5 0 -1"))
        assertEquals(null, ProcessTree.parsePpid("garbage"))
    }

    @Test fun findsGrandchildren() {
        val p = ProcessBuilder("/bin/sh", "-c", "sleep 31411 & sleep 31412 & wait").start()
        try {
            Thread.sleep(400)
            val pid = ProcessTree.pidOf(p)!!
            val d = ProcessTree.descendants(pid)
            assertTrue("expected 2+ descendants, got $d", d.size >= 2)
        } finally { p.destroyForcibly(); Runtime.getRuntime().exec(arrayOf("pkill", "-f", "sleep 3141")).waitFor() }
    }

    @Test fun timeoutLeavesNoOrphanedGuestProcesses() = kotlinx.coroutines.test.runTest {
        val marker = "sleep 31417"
        val tmpDir = java.nio.file.Files.createTempDirectory("lx").toFile()
        val native = File(tmpDir, "native").apply { mkdirs() }
        File(native, "libproot.so").apply { writeText("#!/bin/sh\nfor a; do last=\"\$a\"; done\nexec /bin/sh -c \"\$last\"\n"); setExecutable(true) }
        File(native, "libproot-loader.so").writeText("x")
        File(tmpDir, "linux/rootfs/bin").mkdirs(); File(tmpDir, "linux/rootfs/bin/busybox").writeText("x"); File(tmpDir, "linux/.installed").writeText("ok")
        val env = LinuxEnvironment(tmpDir, native, "x86_64", fs = JvmFsOps, killer = { pid -> Runtime.getRuntime().exec(arrayOf("kill", "-9", pid.toString())).waitFor() }) { "lib".byteInputStream() }
        val started = System.currentTimeMillis()
        val r = env.exec("$marker; echo unreachable", timeoutSeconds = 1)
        assertTrue(r.timedOut)
        assertTrue("returned in ${System.currentTimeMillis() - started} ms", System.currentTimeMillis() - started < 6_000)
        Thread.sleep(500)
        assertFalse("guest process must not survive the timeout", alive(marker))
        tmpDir.deleteRecursively()
    }
}

class PackageCommandTest {
    @org.junit.Test fun pkgInstallBecomesApkAdd() {
        assertEquals("apk add --no-cache nodejs git", translatePackageCommand("pkg install -y nodejs git"))
        assertEquals("apk add --no-cache nodejs", translatePackageCommand("apt-get install -y nodejs-lts"))
    }
    @org.junit.Test fun updateAndRemoveAndPassThrough() {
        assertEquals("apk update && apk upgrade", translatePackageCommand("pkg update"))
        assertEquals("apk del git", translatePackageCommand("apt remove git"))
        assertEquals("ls -la", translatePackageCommand("ls -la"))
        assertEquals("pkg install", translatePackageCommand("pkg install"))
    }
}

class BindMountTest {
    @org.junit.Test fun projectFolderIsBoundIntoTheGuest() {
        val tmp = java.nio.file.Files.createTempDirectory("b").toFile()
        val e = LinuxEnvironment(tmp, File(tmp, "n"), "x86_64", fs = JvmFsOps) { "x".byteInputStream() }
        val (argv, _) = e.buildCommand("ls", "/work", listOf("/data/proj" to "/work"))
        val i = argv.indexOf("/data/proj:/work")
        assertTrue(i > 0 && argv[i - 1] == "-b")
        assertEquals("/work", argv[argv.indexOf("-w") + 1])
        assertTrue("binds must come before the command", i < argv.indexOf("/usr/bin/env"))
        tmp.deleteRecursively()
    }
}
