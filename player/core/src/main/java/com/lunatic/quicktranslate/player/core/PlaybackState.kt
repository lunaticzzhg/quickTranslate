package com.lunatic.quicktranslate.player.core

data class PlaybackState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasVideo: Boolean = false
)
