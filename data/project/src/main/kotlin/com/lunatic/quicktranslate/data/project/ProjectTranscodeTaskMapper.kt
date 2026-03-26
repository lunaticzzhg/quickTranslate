package com.lunatic.quicktranslate.data.project

import com.lunatic.quicktranslate.data.project.local.ProjectTranscodeTaskEntity
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskStage
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskStatus

fun ProjectTranscodeTaskEntity.toDomain(): ProjectTranscodeTask {
    return ProjectTranscodeTask(
        id = id,
        projectId = projectId,
        mediaUri = mediaUri,
        taskType = taskType,
        status = status.toDomainTranscodeTaskStatus(),
        basePriority = basePriority,
        boostSeq = boostSeq,
        retryCount = retryCount,
        stage = stage.toDomainTranscodeTaskStage(
            fallbackStatus = status.toDomainTranscodeTaskStatus()
        ),
        progress = progress,
        errorMessage = errorMessage,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        startedAtEpochMs = startedAtEpochMs,
        finishedAtEpochMs = finishedAtEpochMs
    )
}

private fun String.toDomainTranscodeTaskStage(
    fallbackStatus: ProjectTranscodeTaskStatus
): ProjectTranscodeTaskStage {
    return runCatching { ProjectTranscodeTaskStage.valueOf(this) }
        .getOrElse {
            when (fallbackStatus) {
                ProjectTranscodeTaskStatus.PENDING -> ProjectTranscodeTaskStage.QUEUED
                ProjectTranscodeTaskStatus.RUNNING -> ProjectTranscodeTaskStage.RESOLVING
                ProjectTranscodeTaskStatus.SUCCEEDED -> ProjectTranscodeTaskStage.SUCCEEDED
                ProjectTranscodeTaskStatus.FAILED -> ProjectTranscodeTaskStage.FAILED
                ProjectTranscodeTaskStatus.CANCELED -> ProjectTranscodeTaskStage.CANCELED
            }
        }
}

private fun String.toDomainTranscodeTaskStatus(): ProjectTranscodeTaskStatus {
    return runCatching { ProjectTranscodeTaskStatus.valueOf(this) }
        .getOrDefault(ProjectTranscodeTaskStatus.PENDING)
}
