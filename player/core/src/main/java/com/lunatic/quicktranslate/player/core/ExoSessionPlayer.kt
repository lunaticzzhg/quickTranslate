package com.lunatic.quicktranslate.player.core

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ExoSessionPlayer(
    context: Context
) : SessionPlayer {
    private val exoPlayer = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackState())

    override val player: Player = exoPlayer
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            publishState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishState()
        }
    }

    init {
        exoPlayer.addListener(listener)
        scope.launch {
            while (isActive) {
                publishState()
                delay(300L)
            }
        }
    }

    override fun setMedia(uri: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        publishState()
    }

    override fun play() {
        val duration = exoPlayer.duration
        val nearEnd = duration > 0L && exoPlayer.currentPosition >= duration - 300L
        if (exoPlayer.playbackState == Player.STATE_ENDED || nearEnd) {
            exoPlayer.seekTo(0L)
        }
        exoPlayer.playWhenReady = true
        exoPlayer.play()
        publishState()
    }

    override fun pause() {
        exoPlayer.pause()
        publishState()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        publishState()
    }

    override fun release() {
        exoPlayer.removeListener(listener)
        exoPlayer.release()
        scope.cancel()
    }

    private fun publishState() {
        mutableState.value = PlaybackState(
            isPlaying = exoPlayer.isPlaying,
            isLoading = exoPlayer.playbackState == Player.STATE_BUFFERING,
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L,
            hasVideo = exoPlayer.videoSize.width > 0 && exoPlayer.videoSize.height > 0
        )
    }
}
