package com.lunatic.quicktranslate.feature.session

data class SessionState(
    val title: String = "Learning Session",
    val message: String = "Use the controls below to practice with this media file.",
    val importedName: String = "Unknown file",
    val importedMime: String = "unknown",
    val importedDuration: String = "Unknown duration",
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val hasVideo: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val backLabel: String = "Back To Home"
)

data class ImportedSessionMedia(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val durationMs: Long
)

sealed interface SessionIntent {
    data object BackClicked : SessionIntent
    data object PlayPauseClicked : SessionIntent
    data class SeekToRequested(val positionMs: Long) : SessionIntent
}

sealed interface SessionEffect {
    data object NavigateBack : SessionEffect
}
