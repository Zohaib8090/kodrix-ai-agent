package com.example.util

import com.example.data.model.SourceFile

/**
 * Parsing helpers for the structured text the coding agent is prompted to produce:
 * FILE blocks, COMMAND lines and the Linux subsystem request tag.
 */
object AiResponse {

    const val LINUX_REQUEST_TAG = "<REQUEST_LINUX_SUBSYSTEM>"

    private val fileBlock = Regex(
        """FILE:\s*([^\r\n]+)\s*\r?\n```[a-z0-9_-]*\r?\n([\s\S]*?)```(?:\s*\r?\nENDFILE)?""",
        RegexOption.IGNORE_CASE
    )
    private val secondaryFileBlock = Regex(
        """(?:###\s*|\*\*File:?\*\*\s*|File:\s*)([a-zA-Z0-9_./\\-]+\.[a-zA-Z0-9]+)\s*\r?\n```[a-z0-9_-]*\r?\n([\s\S]*?)```""",
        RegexOption.IGNORE_CASE
    )
    private val commandLine = Regex("""(?:COMMAND|RUN|EXEC):\s*([^\r\n]+)""", RegexOption.IGNORE_CASE)
    private val openFileBlock = Regex(
        """FILE:\s*([^\r\n]+)\s*\r?\n```[a-z0-9_-]*\r?\n([\s\S]*)$""",
        RegexOption.IGNORE_CASE
    )
    private val danglingFileHeader = Regex("""FILE:[^\r\n]*$""", RegexOption.IGNORE_CASE)

    fun parseFiles(raw: String): List<SourceFile> {
        val files = mutableListOf<SourceFile>()
        for (match in fileBlock.findAll(raw)) {
            val path = match.groupValues[1].trim()
            val content = match.groupValues[2]
            if (path.isNotBlank() && content.isNotBlank()) files.add(SourceFile(path, content))
        }
        if (files.isEmpty()) {
            for (match in secondaryFileBlock.findAll(raw)) {
                val path = match.groupValues[1].trim()
                val content = match.groupValues[2]
                if (path.isNotBlank() && content.isNotBlank()) files.add(SourceFile(path, content))
            }
        }
        return files
    }

    /**
     * Normalizes a model supplied file path to a safe project-relative path, or null when it would
     * point outside the project (absolute paths are re-rooted, `..` segments are rejected).
     */
    fun safeRelativePath(path: String): String? {
        val cleaned = path.trim().trim('`', '*', '"', '\'').trim()
            .replace('\\', '/')
            .removePrefix("./")
            .trimStart('/')
        if (cleaned.isEmpty()) return null
        val segments = cleaned.split('/')
        if (segments.any { it == ".." || it.isEmpty() }) return null
        return segments.joinToString("/")
    }

    fun parseCommands(raw: String): List<String> =
        commandLine.findAll(raw)
            .map { it.groupValues[1].trim().removeSurrounding("`").trim() }
            .filter { it.isNotBlank() }
            .toList()

    /** Removes the Linux subsystem request tag and reports whether it was present. */
    fun stripLinuxRequest(raw: String): Pair<String, Boolean> {
        if (!raw.contains(LINUX_REQUEST_TAG)) return raw to false
        return raw.replace(LINUX_REQUEST_TAG, "").trim() to true
    }

    /** The prose of a response with file blocks and command lines removed. */
    fun conversationalText(raw: String, hasAction: Boolean): String {
        if (!hasAction) return raw.trim()
        var cleaned = fileBlock.replace(raw, "")
        cleaned = secondaryFileBlock.replace(cleaned, "")
        cleaned = commandLine.replace(cleaned, "")
        return cleaned.trim()
    }

    /**
     * Text safe to show while the response is still streaming: finished and in-progress file blocks are
     * collapsed to a one line status so the chat never fills with half written code.
     */
    fun streamingPreview(partial: String): String {
        var text = fileBlock.replace(partial) { m ->
            "Wrote ${m.groupValues[1].trim()} (${countLines(m.groupValues[2])} lines)"
        }
        text = openFileBlock.replace(text) { m ->
            "Writing ${m.groupValues[1].trim()} (${countLines(m.groupValues[2])} lines so far)"
        }
        text = danglingFileHeader.replace(text, "Preparing next file")
        text = commandLine.replace(text) { m -> "Will run: ${m.groupValues[1].trim()}" }
        // A partially received tag such as "<REQUEST_LIN" must not flash on screen.
        text = text.replace(LINUX_REQUEST_TAG, "")
        val tagStart = text.lastIndexOf('<')
        if (tagStart >= 0 && LINUX_REQUEST_TAG.startsWith(text.substring(tagStart))) {
            text = text.substring(0, tagStart)
        }
        return text.trim()
    }

    private fun countLines(code: String): Int =
        if (code.isEmpty()) 0 else code.trimEnd('\n', '\r').count { it == '\n' } + 1
}
