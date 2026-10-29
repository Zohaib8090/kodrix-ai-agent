package com.example.linux

import android.content.Context
import java.io.File

/** One shared [LinuxEnvironment] per app process, so the terminal, chat and assistant use the same Alpine. */
object LinuxProvider {
    @Volatile private var instance: LinuxEnvironment? = null

    fun get(context: Context): LinuxEnvironment {
        val app = context.applicationContext
        return instance ?: synchronized(this) {
            instance ?: LinuxEnvironment(
                app.filesDir,
                File(app.applicationInfo.nativeLibraryDir),
                android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "x86_64" } ?: "",
            ) { path -> app.assets.open(path) }.also { instance = it }
        }
    }
}
