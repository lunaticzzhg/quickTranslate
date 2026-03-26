package com.lunatic.quicktranslate.feature.session.transcription

import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskExecutor
import com.lunatic.quicktranslate.domain.project.usecase.UpdateProjectMediaSourceUseCase

class SessionProjectTranscodeTaskExecutor(
    private val downloadStage: SessionRemoteMediaDownloadStage,
    private val pipeline: SessionTranscriptionPipeline,
    private val updateProjectMediaSourceUseCase: UpdateProjectMediaSourceUseCase
) : ProjectTranscodeTaskExecutor {
    override suspend fun execute(task: ProjectTranscodeTask): Result<Unit> {
        val localMedia = runCatching {
            downloadStage.ensureLocalMedia(
                projectId = task.projectId,
                mediaUri = task.mediaUri
            )
        }.getOrElse { error ->
            return Result.failure(error)
        }
        if (localMedia.downloadedFromRemote) {
            runCatching {
                updateProjectMediaSourceUseCase(
                    projectId = task.projectId,
                    mediaUri = localMedia.localPath,
                    mimeType = localMedia.mimeType ?: "application/octet-stream"
                )
            }.getOrElse { error ->
                return Result.failure(error)
            }
        }
        return pipeline.run(
            projectId = task.projectId,
            mediaUri = localMedia.localPath
        ).map { Unit }
    }
}
