package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.InterFontFamily
import com.example.ui.viewmodel.PendingChanges
import com.example.ui.viewmodel.PendingFileChange
import com.example.util.DiffLine
import com.example.util.DiffLineType
import com.example.util.LineDiff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AddedBackground = Color(0xFF16A34A).copy(alpha = 0.18f)
private val RemovedBackground = Color(0xFFDC2626).copy(alpha = 0.18f)
private val AddedMark = Color(0xFF16A34A)
private val RemovedMark = Color(0xFFDC2626)

// Rendering thousands of rows in a bottom sheet is slow; the full file is still what gets written.
private const val MAX_DIFF_ROWS = 300

/** Slim bar above the message box reminding the user that AI edits are waiting for review. */
@Composable
fun PendingChangesBanner(fileCount: Int, onReview: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onReview)
            .testTag("pending_changes_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.RateReview, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$fileCount file change${if (fileCount == 1) "" else "s"} waiting for review",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Review",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeReviewSheet(
    pending: PendingChanges,
    onToggleFile: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onHide: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = pending.files.filter { it.selected }
    val totalAdded = selected.sumOf { it.added }
    val totalRemoved = selected.sumOf { it.removed }

    // Swiping the sheet away only hides it; the banner brings it back. Nothing is applied or lost.
    ModalBottomSheet(onDismissRequest = onHide, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = "Review changes",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${selected.size} of ${pending.files.size} selected",
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text("+$totalAdded", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = AddedMark)
                Spacer(Modifier.width(6.dp))
                Text("-$totalRemoved", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = RemovedMark)
                Spacer(Modifier.weight(1f))
                val allSelected = selected.size == pending.files.size
                TextButton(onClick = { onSelectAll(!allSelected) }) {
                    Text(if (allSelected) "Clear all" else "Select all")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pending.files, key = { it.path }) { change ->
                    FileChangeCard(change, onToggle = { onToggleFile(change.path) })
                }
                if (pending.commands.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Applying will also run in the terminal:",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                pending.commands.forEach { cmd ->
                                    Text(
                                        text = "$ $cmd",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("review_discard")
                ) { Text("Discard all") }
                Button(
                    onClick = onApply,
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("review_apply")
                ) { Text("Apply ${selected.size}") }
            }
        }
    }
}

@Composable
private fun FileChangeCard(change: PendingFileChange, onToggle: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = change.selected, onCheckedChange = { onToggle() })
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = change.path,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row {
                        if (change.isNew) {
                            Text(
                                "new file  ",
                                fontFamily = InterFontFamily, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text("+${change.added}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = AddedMark)
                        Spacer(Modifier.width(6.dp))
                        Text("-${change.removed}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = RemovedMark)
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide diff" else "Show diff",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) DiffView(change)
        }
    }
}

@Composable
private fun DiffView(change: PendingFileChange) {
    val rows by produceState<List<DiffLine>?>(null, change.path, change.oldContent, change.newContent) {
        value = withContext(Dispatchers.Default) {
            val full = LineDiff.diff(change.oldContent, change.newContent)
            // A brand new file is all additions; show it whole rather than as collapsed context.
            if (change.isNew) full else LineDiff.collapse(full)
        }
    }

    val lines = rows
    if (lines == null) {
        Text(
            "Computing diff...",
            fontFamily = InterFontFamily, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
        return
    }
    if (lines.isEmpty()) {
        Text(
            "No visible differences.",
            fontFamily = InterFontFamily, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1220))
            .horizontalScroll(rememberScrollState())
    ) {
        lines.take(MAX_DIFF_ROWS).forEach { line ->
            val (background, mark, markColor) = when (line.type) {
                DiffLineType.ADDED -> Triple(AddedBackground, "+", AddedMark)
                DiffLineType.REMOVED -> Triple(RemovedBackground, "-", RemovedMark)
                DiffLineType.CONTEXT -> Triple(Color.Transparent, " ", Color(0xFF64748B))
            }
            Row(
                modifier = Modifier
                    .widthIn(min = 320.dp)
                    .background(background)
                    .padding(horizontal = 6.dp)
            ) {
                Text(
                    text = (line.newLineNumber ?: line.oldLineNumber)?.toString() ?: "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.width(34.dp)
                )
                Text(
                    text = mark,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = markColor,
                    modifier = Modifier.width(14.dp)
                )
                Text(
                    text = line.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = Color(0xFFE2E8F0),
                    softWrap = false
                )
            }
        }
        if (lines.size > MAX_DIFF_ROWS) {
            Text(
                text = "${lines.size - MAX_DIFF_ROWS} more lines not shown. They will still be written if you apply this file.",
                fontFamily = InterFontFamily,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
