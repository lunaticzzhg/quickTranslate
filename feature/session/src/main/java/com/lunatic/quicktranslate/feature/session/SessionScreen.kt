package com.lunatic.quicktranslate.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = state.message,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start
        )

        Text(
            text = "File: ${state.importedName}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Type: ${state.importedMime}",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Duration: ${state.importedDuration}",
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium
        )

        if (state.hasVideo) {
            Card(modifier = Modifier.fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
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
                    .height(120.dp),
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
            Button(onClick = { onIntent(SessionIntent.BackClicked) }) {
                Text(text = state.backLabel)
            }
        }

        if (state.isLoading) {
            Text(
                text = "Buffering...",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatClock(valueMs: Long): String {
    val totalSeconds = valueMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
