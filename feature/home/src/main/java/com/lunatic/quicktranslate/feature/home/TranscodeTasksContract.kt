package com.lunatic.quicktranslate.feature.home

data class TranscodeTasksState(
    val title: String = "Transcode Tasks",
    val tasks: List<TranscodeTaskUi> = emptyList()
)

data class TranscodeTaskUi(
    val taskId: Long,
    val projectId: Long,
    val projectName: String,
    val mediaUri: String,
    val mimeType: String,
    val durationMs: Long,
    val statusLabel: String,
    val stageLabel: String,
    val progressLabel: String?,
    val isFailed: Boolean,
    val errorMessage: String?
)

sealed interface TranscodeTasksIntent {
    data object BackClicked : TranscodeTasksIntent
    data class TaskClicked(val task: TranscodeTaskUi) : TranscodeTasksIntent
    data class DeleteTaskClicked(val taskId: Long) : TranscodeTasksIntent
}

sealed interface TranscodeTasksEffect {
    data object NavigateBack : TranscodeTasksEffect
    data class NavigateToSession(
        val projectId: Long,
        val media: ImportedMedia
    ) : TranscodeTasksEffect
}
