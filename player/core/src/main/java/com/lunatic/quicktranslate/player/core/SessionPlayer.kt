package com.lunatic.quicktranslate.player.core

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

interface SessionPlayer {
    val player: Player
    val state: StateFlow<PlaybackState>

    fun setMedia(uri: String)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
}
