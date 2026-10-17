package com.example.ui.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext

private val TerminalBackground = Color(0xFF030712)
private val TerminalForeground = Color(0xFFCBD5E1)

private class RenderedLine(val text: AnnotatedString, val baseColor: Color)

/** Fallback color for lines the process did not color itself, so app messages stay readable. */
private fun baseColorFor(line: String): Color = when {
    line.startsWith("$ ") -> Color(0xFF4ADE80)
    line.startsWith(">> ") -> Color(0xFF38BDF8)
    line.startsWith("[Exit") -> Color(0xFFA78BFA)
    line.startsWith("^C") -> Color(0xFFFACC15)
    line.contains("error", ignoreCase = true) || line.contains("failed", ignoreCase = true) ||
        line.contains("fatal", ignoreCase = true) -> Color(0xFFF87171)
    else -> TerminalForeground
}

/**
 * Renders process output with ANSI colors and styles. Parsing runs off the main thread, the view keeps
 * following new output until the user scrolls up, and a button jumps back to the bottom.
 */
@Composable
fun AnsiTerminalOutput(lines: List<String>, modifier: Modifier = Modifier) {
    val rendered by produceState(emptyList<RenderedLine>(), lines) {
        value = withContext(Dispatchers.Default) {
            val parser = AnsiParser()
            lines.map { raw ->
                RenderedLine(
                    text = parser.parseLine(raw).toAnnotatedString(TerminalForeground, TerminalBackground),
                    baseColor = baseColorFor(raw)
                )
            }
        }
    }

    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect { follow = !listState.canScrollForward }
    }
    LaunchedEffect(rendered) {
        if (follow && rendered.isNotEmpty()) listState.scrollToItem(rendered.lastIndex)
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(10.dp)
        ) {
            itemsIndexed(rendered) { _, line ->
                Text(
                    text = line.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = line.baseColor
                )
            }
        }
        if (!follow) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clickable { follow = true }
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Jump to latest output",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}
