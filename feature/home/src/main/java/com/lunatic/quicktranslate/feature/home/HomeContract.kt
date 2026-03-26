package com.lunatic.quicktranslate.feature.home

data class HomeState(
    val title: String = "QuickTranslate",
    val message: String = "Import a local audio or video file to start your learning session.",
    val primaryActionLabel: String = "Import Audio / Video",
    val importLinkLabel: String = "Import Media Link",
    val transcodeEntryLabel: String = "Transcode Tasks",
    val recentProjects: List<RecentProjectUi> = emptyList(),
    val pendingDeletionProject: RecentProjectUi? = null
)

data class ImportedMedia(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val durationMs: Long
)

data class RecentProjectUi(
    val id: Long,
    val mediaUri: String,
    val mimeType: String,
    val durationMs: Long,
    val displayName: String,
    val mediaTypeLabel: String,
    val subtitleStatusLabel: String,
    val recentLearnedAtLabel: String
)

sealed interface HomeIntent {
    data object PrimaryActionClicked : HomeIntent
    data object ImportLinkClicked : HomeIntent
    data object TranscodeEntryClicked : HomeIntent
    data class RecentProjectClicked(val projectId: Long) : HomeIntent
    data class MediaImported(val media: ImportedMedia) : HomeIntent
    data class MediaImportFailed(val message: String) : HomeIntent
    data class DeleteProjectClicked(val projectId: Long) : HomeIntent
    data object ConfirmDeleteProject : HomeIntent
    data object DismissDeleteDialog : HomeIntent
}

sealed interface HomeEffect {
    data object LaunchFilePicker : HomeEffect
    data object NavigateToLinkImport : HomeEffect
    data object NavigateToTranscodeTasks : HomeEffect
    data class NavigateToSession(
        val projectId: Long,
        val media: ImportedMedia
    ) : HomeEffect
    data class ShowError(val message: String) : HomeEffect
}
