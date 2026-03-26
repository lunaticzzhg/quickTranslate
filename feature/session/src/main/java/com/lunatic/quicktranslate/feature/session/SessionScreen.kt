package com.lunatic.quicktranslate.feature.session

import android.widget.Toast
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskStage
import com.lunatic.quicktranslate.feature.transcription.TranscriptionStatus

@Composable
fun SessionScreen(
    state: SessionState,
    player: Player,
    onIntent: (SessionIntent) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val subtitleListState = rememberLazyListState()

    LaunchedEffect(state.activeSubtitleIndex) {
        if (state.activeSubtitleIndex >= 0 && state.activeSubtitleIndex < state.subtitles.size) {
            subtitleListState.animateScrollToItem(state.activeSubtitleIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = state.importedName,
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            text = "${state.importedMime} · ${state.importedDuration}",
            style = MaterialTheme.typography.bodySmall
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = transcriptionStatusLabel(
                    status = state.transcriptionStatus,
                    stage = state.transcodeStage,
                    progress = state.transcriptionProgress
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.transcriptionStatus == TranscriptionStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (
                state.transcriptionStatus == TranscriptionStatus.FAILED ||
                (state.transcriptionStatus == TranscriptionStatus.SUCCESS && state.subtitles.isEmpty())
            ) {
                Button(onClick = { onIntent(SessionIntent.RetryTranscriptionClicked) }) {
                    Text(text = "Retry")
                }
            }
        }

        if (state.transcriptionStatus == TranscriptionStatus.PROCESSING) {
            val progress = (state.transcriptionProgress ?: 0).coerceIn(0, 100)
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
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
            enabled = !state.isLoopMode,
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
            Button(
                onClick = { onIntent(SessionIntent.PlayPauseClicked) },
                enabled = !state.isLoopMode
            ) {
                Text(text = if (state.isPlaying) "Pause" else "Play")
            }
            Button(onClick = { onIntent(SessionIntent.LoopButtonClicked) }) {
                Text(text = if (state.isLoopMode) "Exit Loop" else "Loop")
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
                    if (state.transcriptionStatus == TranscriptionStatus.SUCCESS) {
                        EmptyTranscriptionGuidanceCard(
                            state = state,
                            onRetry = { onIntent(SessionIntent.RetryTranscriptionClicked) },
                            onChooseAnother = { onIntent(SessionIntent.BackClicked) },
                            onCopyError = { message ->
                                clipboardManager.setText(AnnotatedString(message))
                                Toast.makeText(
                                    context,
                                    "Error copied",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    } else {
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
                }
            } else {
                val rangeStart = selectedRangeStart(state)
                val rangeEnd = selectedRangeEnd(state)
                val pendingLoopStart = if (
                    state.isLoopMode &&
                    state.selectedRangeStartIndex != null &&
                    state.selectedRangeEndIndex == null
                ) {
                    state.selectedRangeStartIndex
                } else {
                    null
                }
                itemsIndexed(state.subtitles, key = { _, item -> item.id }) { index, segment ->
                    val isActive = index == state.activeSubtitleIndex
                    val isPendingLoopStart = pendingLoopStart == index
                    val isInSelectedLoopRange = state.isLoopMode &&
                        rangeStart != null &&
                        rangeEnd != null &&
                        index in rangeStart..rangeEnd
                    val loopColor = MaterialTheme.colorScheme.tertiary
                    val defaultActiveBarColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val isLoopStyled = isInSelectedLoopRange || isPendingLoopStart
                    val baseContainerColor = if (isLoopStyled) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    val activeContainerColor = deepenColor(baseContainerColor, amount = 0.08f)
                    val activeBarColor = if (isLoopStyled) loopColor else defaultActiveBarColor
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isActive) 2.dp else 0.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) {
                                activeContainerColor
                            } else {
                                baseContainerColor
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
                                                color = activeBarColor,
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
                                    .padding(if (isActive) 12.dp else 10.dp)
                            ) {
                                if (isPendingLoopStart) {
                                    Text(
                                        text = "Loop Start Selected",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = "Tap another segment to confirm range",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (isInSelectedLoopRange) {
                                    Text(
                                        text = "In Loop Range",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
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
                }
            }
        }
    }
}

@Composable
private fun EmptyTranscriptionGuidanceCard(
    state: SessionState,
    onRetry: () -> Unit,
    onChooseAnother: () -> Unit,
    onCopyError: (String) -> Unit
) {
    val errorText = state.transcriptionError
        ?: "This is usually caused by low speech clarity, strong background noise, or non-English audio with the current model."
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Transcription finished, but no subtitles were generated.",
                style = MaterialTheme.typography.titleSmall
            )
            SelectionContainer {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "Suggestions:",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "• Use a 10-30 second clip with clear speech.\n" +
                    "• Ensure the audio language matches model capability.\n" +
                    "• Increase media volume and reduce background noise.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onRetry) {
                    Text(text = "Retry")
                }
                Button(onClick = onChooseAnother) {
                    Text(text = "Choose Another")
                }
                Button(onClick = { onCopyError(errorText) }) {
                    Text(text = "Copy Error")
                }
            }
        }
    }
}

private fun transcriptionStatusLabel(
    status: TranscriptionStatus,
    stage: ProjectTranscodeTaskStage?,
    progress: Int?
): String {
    return when (status) {
        TranscriptionStatus.IDLE -> "Transcription: idle"
        TranscriptionStatus.QUEUED -> {
            val stageText = stage?.toStageDisplayName()
            if (stageText != null) "Task: queued · $stageText" else "Task: queued"
        }
        TranscriptionStatus.PROCESSING -> {
            val value = progress?.coerceIn(0, 100)
            val stageText = stage?.toStageDisplayName() ?: "Processing"
            if (value != null) "Task: $stageText · $value%" else "Task: $stageText"
        }
        TranscriptionStatus.SUCCESS -> "Transcription: ready"
        TranscriptionStatus.FAILED -> "Transcription: failed"
    }
}

private fun subtitlePlaceholderText(state: SessionState): String {
    return when (state.transcriptionStatus) {
        TranscriptionStatus.IDLE -> "No subtitles yet."
        TranscriptionStatus.QUEUED -> "Transcription queued. Preparing subtitle generation."
        TranscriptionStatus.PROCESSING -> {
            val value = state.transcriptionProgress?.coerceIn(0, 100)
            val stageText = state.transcodeStage?.toStageDisplayName() ?: "Processing"
            if (value != null) "$stageText... $value%" else "$stageText..."
        }
        TranscriptionStatus.SUCCESS -> "No subtitles generated."
        TranscriptionStatus.FAILED -> state.transcriptionError ?: "Transcription failed. Please retry."
    }
}

private fun ProjectTranscodeTaskStage.toStageDisplayName(): String {
    return when (this) {
        ProjectTranscodeTaskStage.QUEUED -> "Queued"
        ProjectTranscodeTaskStage.RESOLVING -> "Resolving link"
        ProjectTranscodeTaskStage.DOWNLOADING -> "Downloading media"
        ProjectTranscodeTaskStage.TRANSCRIBING -> "Transcribing audio"
        ProjectTranscodeTaskStage.SUCCEEDED -> "Completed"
        ProjectTranscodeTaskStage.FAILED -> "Failed"
        ProjectTranscodeTaskStage.CANCELED -> "Canceled"
    }
}

private fun formatClock(valueMs: Long): String {
    val totalSeconds = valueMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun deepenColor(
    color: Color,
    amount: Float
): Color {
    val boundedAmount = amount.coerceIn(0f, 1f)
    return color.copy(
        red = (color.red - boundedAmount).coerceIn(0f, 1f),
        green = (color.green - boundedAmount).coerceIn(0f, 1f),
        blue = (color.blue - boundedAmount).coerceIn(0f, 1f)
    )
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
