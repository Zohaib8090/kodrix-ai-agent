package com.example.ui.terminal

/** Visual attributes produced by ANSI SGR sequences. Colors are ARGB ints, null means the terminal default. */
data class AnsiStyle(
    val foreground: Int? = null,
    val background: Int? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false
)

data class AnsiSpan(val text: String, val style: AnsiStyle)

/**
 * Minimal ANSI escape sequence interpreter for line oriented process output.
 *
 * Supports SGR styling (16, 256 and truecolor), carriage-return overwrites (progress bars) and drops every
 * other escape sequence (cursor movement, erase, OSC titles) so raw escape bytes never reach the screen.
 * Style state carries over between lines, as it does in a real terminal.
 */
class AnsiParser {

    private var style = AnsiStyle()

    fun parseAll(lines: List<String>): List<List<AnsiSpan>> = lines.map { parseLine(it) }

    fun parseLine(raw: String): List<AnsiSpan> {
        val spans = ArrayList<AnsiSpan>()
        val text = StringBuilder()

        fun flush() {
            if (text.isNotEmpty()) {
                spans.add(AnsiSpan(text.toString(), style))
                text.setLength(0)
            }
        }

        var i = 0
        val n = raw.length
        while (i < n) {
            val c = raw[i]
            when {
                c == ESC && i + 1 < n && raw[i + 1] == '[' -> {
                    // CSI: parameters 0x30-0x3F, intermediates 0x20-0x2F, final byte 0x40-0x7E
                    var j = i + 2
                    while (j < n && raw[j].code in 0x30..0x3F) j++
                    val params = raw.substring(i + 2, j)
                    while (j < n && raw[j].code in 0x20..0x2F) j++
                    if (j < n && raw[j].code in 0x40..0x7E) {
                        if (raw[j] == 'm') {
                            flush()
                            applySgr(params)
                        }
                        i = j + 1
                    } else {
                        i = n // truncated sequence at end of line
                    }
                }
                c == ESC && i + 1 < n && raw[i + 1] == ']' -> {
                    // OSC: terminated by BEL or ESC \
                    var j = i + 2
                    while (j < n && raw[j] != BEL && !(raw[j] == ESC && j + 1 < n && raw[j + 1] == '\\')) j++
                    i = if (j < n && raw[j] == BEL) j + 1 else j + 2
                }
                c == ESC -> i += 2
                c == '\r' -> {
                    // Carriage return rewinds to column 0; later text overwrites what came before.
                    if (i + 1 < n && raw[i + 1] != '\r') {
                        text.setLength(0)
                        spans.clear()
                    }
                    i++
                }
                c == '\b' -> {
                    if (text.isNotEmpty()) text.setLength(text.length - 1)
                    i++
                }
                c == '\t' -> { text.append("    "); i++ }
                c.code < 0x20 || c.code == 0x7F -> i++
                else -> { text.append(c); i++ }
            }
        }
        flush()
        return spans
    }

    private fun applySgr(paramString: String) {
        val params = if (paramString.isEmpty()) listOf(0)
        else paramString.split(';', ':').map { it.toIntOrNull() ?: 0 }

        var k = 0
        while (k < params.size) {
            when (val p = params[k]) {
                0 -> style = AnsiStyle()
                1 -> style = style.copy(bold = true)
                2 -> style = style.copy(dim = true)
                3 -> style = style.copy(italic = true)
                4 -> style = style.copy(underline = true)
                7 -> style = style.copy(inverse = true)
                22 -> style = style.copy(bold = false, dim = false)
                23 -> style = style.copy(italic = false)
                24 -> style = style.copy(underline = false)
                27 -> style = style.copy(inverse = false)
                in 30..37 -> style = style.copy(foreground = PALETTE[p - 30])
                39 -> style = style.copy(foreground = null)
                in 40..47 -> style = style.copy(background = PALETTE[p - 40])
                49 -> style = style.copy(background = null)
                in 90..97 -> style = style.copy(foreground = PALETTE[p - 90 + 8])
                in 100..107 -> style = style.copy(background = PALETTE[p - 100 + 8])
                38, 48 -> {
                    val color: Int?
                    val consumed: Int
                    when (params.getOrNull(k + 1)) {
                        5 -> {
                            color = params.getOrNull(k + 2)?.let { xterm256(it) }
                            consumed = 2
                        }
                        2 -> {
                            val r = params.getOrNull(k + 2)
                            val g = params.getOrNull(k + 3)
                            val b = params.getOrNull(k + 4)
                            color = if (r != null && g != null && b != null) {
                                rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
                            } else null
                            consumed = 4
                        }
                        else -> {
                            color = null
                            consumed = 0
                        }
                    }
                    if (color != null) {
                        style = if (p == 38) style.copy(foreground = color) else style.copy(background = color)
                    }
                    k += consumed
                }
            }
            k++
        }
    }

    companion object {
        private const val ESC = '\u001B'
        private const val BEL = '\u0007'

        private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        val PALETTE: IntArray = intArrayOf(
            rgb(0x1E, 0x1E, 0x2E), rgb(0xEF, 0x44, 0x44), rgb(0x22, 0xC5, 0x5E), rgb(0xEA, 0xB3, 0x08),
            rgb(0x3B, 0x82, 0xF6), rgb(0xA8, 0x55, 0xF7), rgb(0x06, 0xB6, 0xD4), rgb(0xD1, 0xD5, 0xDB),
            rgb(0x6B, 0x72, 0x80), rgb(0xF8, 0x71, 0x71), rgb(0x4A, 0xDE, 0x80), rgb(0xFA, 0xCC, 0x15),
            rgb(0x60, 0xA5, 0xFA), rgb(0xC0, 0x84, 0xFC), rgb(0x22, 0xD3, 0xEE), rgb(0xF9, 0xFA, 0xFB)
        )

        fun xterm256(index: Int): Int? {
            if (index !in 0..255) return null
            if (index < 16) return PALETTE[index]
            if (index < 232) {
                val v = index - 16
                val steps = intArrayOf(0, 95, 135, 175, 215, 255)
                return rgb(steps[v / 36], steps[(v / 6) % 6], steps[v % 6])
            }
            val gray = 8 + (index - 232) * 10
            return rgb(gray, gray, gray)
        }
    }
}
