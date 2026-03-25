package com.lunatic.quicktranslate.domain.project.model

data class ProjectTranscodeTask(
    val id: Long,
    val projectId: Long,
    val mediaUri: String,
    val taskType: String,
    val status: ProjectTranscodeTaskStatus,
    val basePriority: Int,
    val boostSeq: Long,
    val retryCount: Int,
    val errorMessage: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val startedAtEpochMs: Long?,
    val finishedAtEpochMs: Long?
)

enum class ProjectTranscodeTaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED
}

object ProjectTranscodeTaskType {
    const val TRANSCRIBE = "TRANSCRIBE"
}
