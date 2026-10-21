package com.example.util

import java.io.File

/**
 * Path helpers for anything that writes or serves files from a path we did not choose
 * ourselves: AI-generated file names, ZIP entries, HTTP request paths, terminal arguments.
 */
object SafePaths {

    /**
     * Resolves [relativePath] inside [baseDir]. Returns null when the path is empty, points at
     * [baseDir] itself, or would escape it (for example "../../shared_prefs/x.xml").
     * Leading slashes are treated as relative, matching how File(parent, child) already behaved.
     */
    fun resolveInside(baseDir: File, relativePath: String): File? {
        val cleaned = relativePath.trim().replace('\\', '/').trimStart('/')
        if (cleaned.isEmpty() || cleaned.indexOf('\u0000') >= 0) return null
        val base = baseDir.canonicalFile
        val target = File(base, cleaned).canonicalFile
        return if (target.path.startsWith(base.path + File.separator)) target else null
    }

    /** True when [file] is [baseDir] or somewhere below it, after resolving "..", "." and symlinks. */
    fun isInside(baseDir: File, file: File): Boolean {
        val base = baseDir.canonicalFile
        val target = file.canonicalFile
        return target == base || target.path.startsWith(base.path + File.separator)
    }

    /**
     * If every entry in [entryNames] sits under the same single top-level folder
     * (as in GitHub zipballs: "repo-abc123/..."), returns that folder with a trailing slash.
     */
    fun commonRootFolder(entryNames: List<String>): String? {
        val names = entryNames.map { it.replace('\\', '/') }.filter { it.isNotBlank() && !it.startsWith("__MACOSX") }
        if (names.isEmpty()) return null
        val first = names.first().substringBefore('/', missingDelimiterValue = "")
        if (first.isEmpty()) return null
        val prefix = "$first/"
        return if (names.all { it == prefix || it.startsWith(prefix) }) prefix else null
    }
}
