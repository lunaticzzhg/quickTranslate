package com.lunatic.quicktranslate.feature.home

data class HomeState(
    val title: String = "QuickTranslate",
    val message: String = "Import a local audio or video file to start your learning session.",
    val primaryActionLabel: String = "Import Audio / Video"
)

data class ImportedMedia(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val durationMs: Long
)

sealed interface HomeIntent {
    data object PrimaryActionClicked : HomeIntent
    data class MediaImported(val media: ImportedMedia) : HomeIntent
    data class MediaImportFailed(val message: String) : HomeIntent
}

sealed interface HomeEffect {
    data object LaunchFilePicker : HomeEffect
    data class NavigateToSession(val media: ImportedMedia) : HomeEffect
    data class ShowError(val message: String) : HomeEffect
}
