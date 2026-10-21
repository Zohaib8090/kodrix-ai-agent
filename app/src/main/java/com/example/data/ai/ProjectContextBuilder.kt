package com.example.data.ai

import com.example.data.model.SourceFile
import java.util.Locale

/**
 * Builds the "here is the project" part of a prompt. Every file is listed, and full contents are
 * included until the character budget is used up. A file is never cut in half: the model is asked
 * to return complete files, so showing it a truncated one would make it overwrite the rest.
 */
object ProjectContextBuilder {

    const val OMITTED_NOTE = "content not included"

    // Generated files whose contents only cost tokens.
    private val listOnlyFiles = setOf("package-lock.json", "yarn.lock", "pnpm-lock.yaml", "bun.lockb", "gradle-wrapper.jar")

    fun describeFiles(files: List<SourceFile>, maxChars: Int, priorityPath: String? = null): String {
        if (files.isEmpty()) return "The project has no files yet."

        // The file the user has open first, then smallest first so as many files as possible fit.
        val ordered = files.sortedWith(
            compareBy<SourceFile> { it.path != priorityPath }.thenBy { it.content.length }
        )
        var remaining = maxChars
        val included = mutableListOf<SourceFile>()
        for (file in ordered) {
            if (file.path.substringAfterLast('/') in listOnlyFiles) continue
            if (file.content.length <= remaining) {
                included.add(file)
                remaining -= file.content.length
            }
        }
        val includedPaths = included.map { it.path }.toSet()

        return buildString {
            append("Project files (${files.size}):\n")
            for (file in files) {
                append("- ").append(file.path).append(" (").append(formatSize(file.content.length)).append(")")
                if (file.path !in includedPaths) append(" [").append(OMITTED_NOTE).append("]")
                append('\n')
            }
            for (file in included) {
                append("\n=== ").append(file.path).append(" ===\n")
                append(file.content)
                if (!file.content.endsWith("\n")) append('\n')
                append("=== end of ").append(file.path).append(" ===\n")
            }
        }
    }

    private fun formatSize(chars: Int): String =
        if (chars < 1024) "$chars B" else String.format(Locale.US, "%.1f KB", chars / 1024.0)
}
