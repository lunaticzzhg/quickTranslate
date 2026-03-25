package com.lunatic.quicktranslate.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

@Composable
fun SessionScreen(
    state: SessionState,
    player: Player,
    onIntent: (SessionIntent) -> Unit
) {
    val subtitleListState = rememberLazyListState()
    var isLoopPanelExpanded by remember { mutableStateOf(false) }

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
            Button(onClick = { isLoopPanelExpanded = !isLoopPanelExpanded }) {
                Text(text = if (isLoopPanelExpanded) "Hide Loop" else "Loop")
            }
        }

        if (state.isLoading) {
            Text(
                text = "Buffering...",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (state.isLooping) {
            Text(
                text = "Seek is disabled while loop is active.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (isLoopPanelExpanded) {
            Text(
                text = "Loop Controls",
                style = MaterialTheme.typography.titleSmall
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
            }
            if (state.isLooping) {
                val remaining = state.loopRemainingCount?.toString() ?: "∞"
                Text(
                    text = "Looping... remaining: $remaining",
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
            itemsIndexed(state.subtitles, key = { _, item -> item.id }) { index, segment ->
                val isActive = index == state.activeSubtitleIndex
                val isSelected = segment.id == state.selectedSubtitleId
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onIntent(SessionIntent.SubtitleClicked(segment)) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "${formatClock(segment.startMs)} - ${formatClock(segment.endMs)}",
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
                        if (isSelected) {
                            Text(
                                text = "Selected for loop",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatClock(valueMs: Long): String {
    val totalSeconds = valueMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
