package com.example

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Probe, not a pass/fail assertion: records what Android lets THIS app process execute.
 * Run inside the app's own SELinux domain (unlike `adb run-as`, which uses a more permissive one).
 * Results are logged under tag EXECPROBE.
 */
@RunWith(AndroidJUnit4::class)
class LinuxExecProbeTest {

    private fun run(label: String, env: Map<String, String>, vararg cmd: String) {
        val result = try {
            val pb = ProcessBuilder(*cmd).redirectErrorStream(true)
            pb.environment().putAll(env)
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText().trim().take(160)
            val done = p.waitFor(10, TimeUnit.SECONDS)
            "exit=${if (done) p.exitValue() else "timeout"} out=[$out]"
        } catch (e: Exception) {
            "EXCEPTION ${e.javaClass.simpleName}: ${e.message?.take(160)}"
        }
        Log.i("EXECPROBE", "$label -> $result")
    }

    @Test
    fun probe() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val usr = File(ctx.filesDir, "usr")
        val bash = File(usr, "bin/bash")
        val env = mapOf(
            "LD_LIBRARY_PATH" to File(usr, "lib").absolutePath,
            "PREFIX" to usr.absolutePath,
        )
        Log.i("EXECPROBE", "sdk=${android.os.Build.VERSION.SDK_INT} targetSdk=${ctx.applicationInfo.targetSdkVersion} " +
            "bashExists=${bash.exists()} bashSize=${bash.length()} canExec=${bash.canExecute()} " +
            "nativeLibDir=${ctx.applicationInfo.nativeLibraryDir}")

        run("A direct exec of data-dir bash", env, bash.absolutePath, "--version")
        run("B via /system/bin/sh -c (what Kodrix does)", env, "/system/bin/sh", "-c", "${bash.absolutePath} --version")
        run("C via linker64 on data-dir bash", env, "/system/bin/linker64", bash.absolutePath, "--version")

        // Control: system binaries must still run.
        run("D control /system/bin/toybox", emptyMap(), "/system/bin/toybox", "uname", "-a")

        // Control 2: a binary shipped as a native library IS executable (the documented workaround).
        val libs = File(ctx.applicationInfo.nativeLibraryDir)
        Log.i("EXECPROBE", "nativeLibDir contents: ${libs.list()?.toList()}")
    }
}
