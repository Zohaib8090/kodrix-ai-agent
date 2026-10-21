package com.example.util

/**
 * Turns a free-form build prompt into a short project name, and keeps project
 * names unique so that two projects never share a folder on disk.
 */
object ProjectNaming {

    const val FALLBACK_NAME = "New Project"
    private const val MAX_WORDS = 4
    private const val MAX_LENGTH = 40

    // Words that open most prompts ("I want to build a ...") and say nothing about the app.
    private val leadingFiller = setOf(
        "i", "we", "want", "wanna", "would", "like", "need", "to", "please", "can", "could",
        "you", "build", "create", "make", "generate", "develop", "design", "code", "write",
        "me", "us", "a", "an", "the", "new", "simple", "basic", "my", "our", "help", "just"
    )

    // Words that usually start a description of features rather than the name.
    private val stopWords = setOf(
        "with", "that", "which", "where", "using", "and", "for", "to", "in", "on", "of",
        "so", "by", "from", "like", "including"
    )

    fun deriveName(prompt: String): String {
        val firstLine = prompt.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val words = firstLine.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
        val picked = words
            .dropWhile { it.lowercase() in leadingFiller }
            .takeWhile { it.lowercase() !in stopWords }
            .take(MAX_WORDS)
        if (picked.isEmpty()) return FALLBACK_NAME
        return picked.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            .take(MAX_LENGTH)
            .trim()
    }

    /**
     * Folder name used on disk for a project. Leading dots are dropped so that names such as
     * ".." can never resolve to the parent of the projects folder.
     */
    fun toDirectoryName(projectName: String): String =
        projectName.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").trimStart('.').ifEmpty { "default_project" }

    /**
     * Returns [baseName], or "[baseName] 2", "[baseName] 3", ... so that the result's
     * folder name does not collide with any name in [takenNames] (compared case-insensitively).
     */
    fun uniqueName(baseName: String, takenNames: Collection<String>): String {
        val base = baseName.trim().ifBlank { FALLBACK_NAME }
        val taken = takenNames.map { toDirectoryName(it).lowercase() }.toSet()
        var candidate = base
        var suffix = 2
        while (toDirectoryName(candidate).lowercase() in taken) {
            candidate = "$base $suffix"
            suffix++
        }
        return candidate
    }
}
