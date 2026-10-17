package com.example.util

enum class DiffLineType { CONTEXT, ADDED, REMOVED }

data class DiffLine(
    val type: DiffLineType,
    val text: String,
    val oldLineNumber: Int?,
    val newLineNumber: Int?
)

data class DiffStats(val added: Int, val removed: Int)

/**
 * Line based diff used to preview AI file edits before they are written to disk.
 */
object LineDiff {

    // Above this many cells in the LCS table the middle section is shown as a plain replace.
    private const val MAX_LCS_CELLS = 4_000_000L

    fun diff(oldText: String?, newText: String): List<DiffLine> {
        val oldLines = splitLines(oldText ?: "")
        val newLines = splitLines(newText)

        var prefix = 0
        while (prefix < oldLines.size && prefix < newLines.size && oldLines[prefix] == newLines[prefix]) prefix++
        var suffix = 0
        while (
            suffix < oldLines.size - prefix &&
            suffix < newLines.size - prefix &&
            oldLines[oldLines.size - 1 - suffix] == newLines[newLines.size - 1 - suffix]
        ) suffix++

        val oldMid = oldLines.subList(prefix, oldLines.size - suffix)
        val newMid = newLines.subList(prefix, newLines.size - suffix)

        val result = ArrayList<DiffLine>(oldLines.size + newLines.size)
        for (i in 0 until prefix) {
            result.add(DiffLine(DiffLineType.CONTEXT, oldLines[i], i + 1, i + 1))
        }

        var oldNo = prefix + 1
        var newNo = prefix + 1
        for (op in diffMiddle(oldMid, newMid)) {
            when (op.type) {
                DiffLineType.CONTEXT -> result.add(DiffLine(op.type, op.text, oldNo++, newNo++))
                DiffLineType.REMOVED -> result.add(DiffLine(op.type, op.text, oldNo++, null))
                DiffLineType.ADDED -> result.add(DiffLine(op.type, op.text, null, newNo++))
            }
        }

        for (i in 0 until suffix) {
            result.add(DiffLine(DiffLineType.CONTEXT, oldLines[oldLines.size - suffix + i], oldNo++, newNo++))
        }
        return result
    }

    fun stats(lines: List<DiffLine>): DiffStats =
        DiffStats(
            added = lines.count { it.type == DiffLineType.ADDED },
            removed = lines.count { it.type == DiffLineType.REMOVED }
        )

    /**
     * Collapses long runs of unchanged lines, keeping [context] lines around each change.
     * A collapsed run is replaced by a single CONTEXT line whose text is "..." with null line numbers.
     */
    fun collapse(lines: List<DiffLine>, context: Int = 3): List<DiffLine> {
        if (lines.none { it.type != DiffLineType.CONTEXT }) return emptyList()
        val keep = BooleanArray(lines.size)
        lines.forEachIndexed { index, line ->
            if (line.type != DiffLineType.CONTEXT) {
                for (j in maxOf(0, index - context)..minOf(lines.lastIndex, index + context)) keep[j] = true
            }
        }
        val out = ArrayList<DiffLine>()
        var skipping = false
        lines.forEachIndexed { index, line ->
            if (keep[index]) {
                skipping = false
                out.add(line)
            } else if (!skipping) {
                skipping = true
                out.add(DiffLine(DiffLineType.CONTEXT, "...", null, null))
            }
        }
        return out
    }

    private fun splitLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val normalized = text.replace("\r\n", "\n")
        val lines = normalized.split('\n')
        return if (lines.last().isEmpty()) lines.dropLast(1) else lines
    }

    private class Op(val type: DiffLineType, val text: String)

    private fun diffMiddle(a: List<String>, b: List<String>): List<Op> {
        if (a.isEmpty()) return b.map { Op(DiffLineType.ADDED, it) }
        if (b.isEmpty()) return a.map { Op(DiffLineType.REMOVED, it) }
        if (a.size.toLong() * b.size.toLong() > MAX_LCS_CELLS) {
            return a.map { Op(DiffLineType.REMOVED, it) } + b.map { Op(DiffLineType.ADDED, it) }
        }

        val n = a.size
        val m = b.size
        // lcs[i][j] = length of the LCS of a[i..] and b[j..]
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }

        val ops = ArrayList<Op>(n + m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { ops.add(Op(DiffLineType.CONTEXT, a[i])); i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> { ops.add(Op(DiffLineType.REMOVED, a[i])); i++ }
                else -> { ops.add(Op(DiffLineType.ADDED, b[j])); j++ }
            }
        }
        while (i < n) ops.add(Op(DiffLineType.REMOVED, a[i++]))
        while (j < m) ops.add(Op(DiffLineType.ADDED, b[j++]))
        return ops
    }
}
