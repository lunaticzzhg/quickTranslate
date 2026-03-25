package com.lunatic.quicktranslate.feature.session

data class SessionState(
    val title: String = "Learning Session",
    val message: String = "Session detail page is ready. Media player and subtitle linkage will be added in next tasks.",
    val importedName: String = "Unknown file",
    val importedMime: String = "unknown",
    val importedDuration: String = "Unknown duration",
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
}

sealed interface SessionEffect {
    data object NavigateBack : SessionEffect
}
