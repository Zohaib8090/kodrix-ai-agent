package com.example.data.ai

import com.example.data.model.SourceFile

/**
 * Parses the plain-text protocol the system prompts ask models to follow:
 *
 *     FILE: path/to/file
 *     ```lang
 *     ...complete file...
 *     ```
 *     ENDFILE
 *
 *     COMMAND: npm install axios
 *
 * Shared by code generation, the self-healing fixer and the workspace chat so they all agree.
 */
object AiResponseParser {

    const val LINUX_SUBSYSTEM_TAG = "<REQUEST_LINUX_SUBSYSTEM>"

    private val fileHeader = Regex("""FILE:[ \t]*([^\r\n]+?)[ \t]*\r?\n```[^\r\n`]*\r?\n""", RegexOption.IGNORE_CASE)
    private val fenceBeforeEndFile = Regex("""\r?\n```[ \t]*\r?\nENDFILE""", RegexOption.IGNORE_CASE)
    private val closingFenceLine = Regex("""(?m)^```[ \t]*$""")
    private val trailingEndFile = Regex("""^[ \t]*\r?\nENDFILE""", RegexOption.IGNORE_CASE)

    // Looser formats some models fall back to: "### src/App.tsx" or "**File:** src/App.tsx"
    private val headingFileBlock = Regex(
        """(?:###\s*|\*\*File:?\*\*\s*|File:\s*)([a-zA-Z0-9_./\\-]+\.[a-zA-Z0-9]+)\s*\r?\n```[^\r\n`]*\r?\n([\s\S]*?)```""",
        RegexOption.IGNORE_CASE
    )

    // Only an explicit COMMAND: at the start of a line counts. Matching "run:" or "exec:" anywhere
    // used to execute lines like `run: npm ci` from generated GitHub workflow files.
    private val commandLine = Regex("""(?m)^[ \t]*(?:[-*][ \t]+)?COMMAND:[ \t]*(.+?)[ \t]*$""")

    private data class FileBlock(val path: String, val content: String, val range: IntRange, val complete: Boolean)

    /**
     * Finds FILE blocks by scanning header to header. A block ends at the fence right before its
     * ENDFILE marker (so files that contain their own ``` fences survive) or, without a marker,
     * at the first bare closing fence. A block with no closing fence was cut off mid-file.
     */
    private fun findFileBlocks(raw: String): List<FileBlock> {
        val headers = fileHeader.findAll(raw).toList()
        return headers.mapIndexed { index, header ->
            val bodyStart = header.range.last + 1
            val limit = headers.getOrNull(index + 1)?.range?.first ?: raw.length
            val segment = raw.substring(bodyStart, limit)
            val path = cleanPath(header.groupValues[1])

            val withEnd = fenceBeforeEndFile.find(segment)
            if (withEnd != null) {
                return@mapIndexed FileBlock(path, segment.substring(0, withEnd.range.first), header.range.first until bodyStart + withEnd.range.last + 1, true)
            }
            val fence = closingFenceLine.find(segment)
            if (fence != null) {
                var end = bodyStart + fence.range.last + 1
                trailingEndFile.find(raw.substring(end, limit))?.let { end += it.range.last + 1 }
                return@mapIndexed FileBlock(path, segment.substring(0, fence.range.first), header.range.first until end, true)
            }
            FileBlock(path, segment, header.range.first until limit, false)
        }
    }

    /** Complete files in [raw]; when the same path appears more than once the last version wins. */
    fun parseFiles(raw: String): List<SourceFile> {
        val files = LinkedHashMap<String, String>()
        fun add(path: String, content: String) {
            if (path.isNotBlank() && content.isNotBlank()) {
                files.remove(path)
                files[path] = content.trimEnd('\r', '\n') + "\n"
            }
        }

        findFileBlocks(raw).filter { it.complete }.forEach { add(it.path, it.content) }
        if (files.isEmpty()) {
            headingFileBlock.findAll(raw).forEach { add(cleanPath(it.groupValues[1]), it.groupValues[2]) }
        }
        return files.map { (path, content) -> SourceFile(path, content) }
    }

    /** Paths of FILE blocks that were cut off before their closing fence (for example at max tokens). */
    fun parseIncompleteFilePaths(raw: String): List<String> =
        findFileBlocks(raw).filter { !it.complete && it.path.isNotBlank() }.map { it.path }

    /** Terminal commands requested with line-start `COMMAND:` outside of file blocks. */
    fun parseCommands(raw: String): List<String> {
        return commandLine.findAll(stripFileBlocks(raw))
            .map { it.groupValues[1].trim().removeSurrounding("`").trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    fun requestsLinuxSubsystem(raw: String): Boolean = raw.contains(LINUX_SUBSYSTEM_TAG)

    /** What is left for the chat bubble once file blocks, commands and control tags are removed. */
    fun extractConversationalText(raw: String): String {
        return commandLine.replace(stripFileBlocks(raw), "")
            .replace(LINUX_SUBSYSTEM_TAG, "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    private fun stripFileBlocks(raw: String): String {
        val blocks = findFileBlocks(raw)
        val text = if (blocks.isEmpty()) {
            headingFileBlock.replace(raw, "\n")
        } else {
            val sb = StringBuilder()
            var cursor = 0
            for (block in blocks) {
                if (block.range.first > cursor) sb.append(raw, cursor, block.range.first)
                sb.append('\n')
                cursor = block.range.last + 1
            }
            if (cursor < raw.length) sb.append(raw, cursor, raw.length)
            sb.toString()
        }
        return text
    }

    /** Removes markdown decoration models often put around paths: `src/App.tsx`, **index.html**. */
    internal fun cleanPath(rawPath: String): String {
        return rawPath.trim()
            .removeSurrounding("**")
            .removeSuffix("**")
            .removeSurrounding("`")
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .removeSuffix(":")
            .trim()
            .removePrefix("./")
    }
}
