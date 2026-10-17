package com.example.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

fun List<AnsiSpan>.toAnnotatedString(defaultForeground: Color, defaultBackground: Color): AnnotatedString =
    buildAnnotatedString {
        for (span in this@toAnnotatedString) {
            val style = span.style
            var fg = style.foreground?.let { Color(it) }
            var bg = style.background?.let { Color(it) }
            if (style.inverse) {
                val swappedFg = bg ?: defaultBackground
                bg = fg ?: defaultForeground
                fg = swappedFg
            }
            if (style.dim && fg == null) fg = defaultForeground.copy(alpha = 0.6f)
            pushStyle(
                SpanStyle(
                    color = fg ?: Color.Unspecified,
                    background = bg ?: Color.Unspecified,
                    fontWeight = if (style.bold) FontWeight.Bold else null,
                    fontStyle = if (style.italic) FontStyle.Italic else null,
                    textDecoration = if (style.underline) TextDecoration.Underline else null
                )
            )
            append(span.text)
            pop()
        }
    }
