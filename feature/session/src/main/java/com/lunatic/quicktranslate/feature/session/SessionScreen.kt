package com.lunatic.quicktranslate.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.lunatic.quicktranslate.feature.transcription.TranscriptionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    state: SessionState,
    player: Player,
    onIntent: (SessionIntent) -> Unit
) {
    val subtitleListState = rememberLazyListState()
    var isLoopPanelOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.activeSubtitleIndex) {
        if (state.activeSubtitleIndex >= 0 && state.activeSubtitleIndex < state.subtitles.size) {
            subtitleListState.animateScrollToItem(state.activeSubtitleIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start
        )

        Text(
            text = "${state.importedName} · ${state.importedMime} · ${state.importedDuration}",
            style = MaterialTheme.typography.bodySmall
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = transcriptionStatusLabel(state.transcriptionStatus),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.transcriptionStatus == TranscriptionStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (state.transcriptionStatus == TranscriptionStatus.FAILED) {
                Button(onClick = { onIntent(SessionIntent.RetryTranscriptionClicked) }) {
                    Text(text = "Retry")
                }
            }
        }

        if (state.hasVideo) {
            Card(modifier = Modifier.fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    factory = { context ->
                        PlayerView(context).apply {
                            useController = false
                            this.player = player
                        }
                    },
                    update = { view ->
                        view.player = player
                    }
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Audio mode",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        val durationMs = state.durationMs.coerceAtLeast(0L)
        val currentMs = state.currentPositionMs.coerceIn(0L, durationMs.takeIf { it > 0L } ?: 0L)
        val progress = if (durationMs > 0L) {
            currentMs.toFloat() / durationMs.toFloat()
        } else {
            0f
        }

        Slider(
            value = progress,
            onValueChange = { value ->
                if (durationMs > 0L) {
                    onIntent(
                        SessionIntent.SeekToRequested(
                            positionMs = (durationMs * value).toLong()
                        )
                    )
                }
            },
            enabled = !state.isLooping,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatClock(currentMs))
            Text(text = formatClock(durationMs))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onIntent(SessionIntent.PlayPauseClicked) }) {
                Text(text = if (state.isPlaying) "Pause" else "Play")
            }
            Button(onClick = { isLoopPanelOpen = true }) {
                Text(text = "Loop")
            }
        }

        if (state.isLoading) {
            Text(
                text = "Buffering...",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text(
            text = "Subtitles",
            style = MaterialTheme.typography.titleMedium
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = subtitleListState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.subtitles.isEmpty()) {
                item(key = "subtitle-empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = subtitlePlaceholderText(state),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                val rangeStart = selectedRangeStart(state)
                val rangeEnd = selectedRangeEnd(state)
                itemsIndexed(state.subtitles, key = { _, item -> item.id }) { index, segment ->
                    if (rangeStart != null && index == rangeStart) {
                        SelectionBracketItem(
                            top = true,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    val isActive = index == state.activeSubtitleIndex
                    val activeColor = MaterialTheme.colorScheme.primary
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        onClick = { onIntent(SessionIntent.SubtitleClicked(segment)) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .width(4.dp)
                                        .height(64.dp)
                                        .drawBehind {
                                            drawLine(
                                                color = activeColor,
                                                start = Offset(size.width / 2, 0f),
                                                end = Offset(size.width / 2, size.height),
                                                strokeWidth = size.width
                                            )
                                        }
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "${formatClock(segment.startMs)} - ${formatClock(segment.endMs)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "#${index + 1}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = segment.text,
                                    style = if (isActive) {
                                        MaterialTheme.typography.titleMedium
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    }
                                )
                            }
                        }
                    }

                    if (rangeEnd != null && index == rangeEnd) {
                        SelectionBracketItem(
                            top = false,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }

    if (isLoopPanelOpen) {
        ModalBottomSheet(onDismissRequest = { isLoopPanelOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Loop Controls",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoopCountOption.entries.forEach { option ->
                        Button(
                            onClick = { onIntent(SessionIntent.LoopCountChanged(option)) },
                            enabled = !state.isLooping
                        ) {
                            val suffix = if (state.loopCountOption == option) " *" else ""
                            Text(text = option.label + suffix)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onIntent(SessionIntent.StartLoopClicked) },
                        enabled = !state.isLooping
                    ) {
                        Text(text = "Start Loop")
                    }
                    Button(
                        onClick = { onIntent(SessionIntent.StopLoopClicked) },
                        enabled = state.isLooping
                    ) {
                        Text(text = "Stop Loop")
                    }
                    Button(onClick = { isLoopPanelOpen = false }) {
                        Text(text = "Done")
                    }
                }
            }
        }
    }
}

private fun transcriptionStatusLabel(status: TranscriptionStatus): String {
    return when (status) {
        TranscriptionStatus.IDLE -> "Transcription: idle"
        TranscriptionStatus.QUEUED -> "Transcription: queued"
        TranscriptionStatus.PROCESSING -> "Transcription: processing..."
        TranscriptionStatus.SUCCESS -> "Transcription: ready"
        TranscriptionStatus.FAILED -> "Transcription: failed"
    }
}

private fun subtitlePlaceholderText(state: SessionState): String {
    return when (state.transcriptionStatus) {
        TranscriptionStatus.IDLE -> "No subtitles yet."
        TranscriptionStatus.QUEUED -> "Transcription queued. Preparing subtitle generation."
        TranscriptionStatus.PROCESSING -> "Generating subtitles..."
        TranscriptionStatus.SUCCESS -> "No subtitles generated."
        TranscriptionStatus.FAILED -> state.transcriptionError ?: "Transcription failed. Please retry."
    }
}

private fun formatClock(valueMs: Long): String {
    val totalSeconds = valueMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun selectedRangeStart(state: SessionState): Int? {
    val start = state.selectedRangeStartIndex ?: return null
    val end = state.selectedRangeEndIndex ?: return null
    return minOf(start, end)
}

private fun selectedRangeEnd(state: SessionState): Int? {
    val start = state.selectedRangeStartIndex ?: return null
    val end = state.selectedRangeEndIndex ?: return null
    return maxOf(start, end)
}

@Composable
private fun SelectionBracketItem(
    top: Boolean,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .drawBehind {
                    drawLine(
                        color = color.copy(alpha = 0.65f),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = size.height
                    )
                }
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = color.copy(alpha = 0.14f)
        ) {
            Text(
                text = if (top) "Loop Start" else "Loop End",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = color,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .drawBehind {
                    drawLine(
                        color = color.copy(alpha = 0.65f),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = size.height
                    )
                }
        )
    }
}
