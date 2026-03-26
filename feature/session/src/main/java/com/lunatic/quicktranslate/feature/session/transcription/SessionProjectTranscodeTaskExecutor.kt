package com.lunatic.quicktranslate.feature.session.transcription

import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskStage
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskExecutor
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskRepository
import com.lunatic.quicktranslate.domain.project.usecase.UpdateProjectMediaSourceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SessionProjectTranscodeTaskExecutor(
    private val downloadStage: SessionRemoteMediaDownloadStage,
    private val pipeline: SessionTranscriptionPipeline,
    private val updateProjectMediaSourceUseCase: UpdateProjectMediaSourceUseCase,
    private val transcodeTaskRepository: ProjectTranscodeTaskRepository
) : ProjectTranscodeTaskExecutor {
    override suspend fun execute(task: ProjectTranscodeTask): Result<Unit> {
        transcodeTaskRepository.updateRunningTaskProgress(
            taskId = task.id,
            stage = ProjectTranscodeTaskStage.RESOLVING,
            progress = 0
        )
        val localMedia = runCatching {
            downloadStage.ensureLocalMedia(
                projectId = task.projectId,
                mediaUri = task.mediaUri,
                onProgress = { progress ->
                    emitProgressAsync(
                        taskId = task.id,
                        stage = ProjectTranscodeTaskStage.DOWNLOADING,
                        progress = progress
                    )
                }
            )
        }.getOrElse { error ->
            return Result.failure(error)
        }
        if (!localMedia.downloadedFromRemote) {
            transcodeTaskRepository.updateRunningTaskProgress(
                taskId = task.id,
                stage = ProjectTranscodeTaskStage.TRANSCRIBING,
                progress = 0
            )
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
            mediaUri = localMedia.localPath,
            onProgress = { progress ->
                emitProgressAsync(
                    taskId = task.id,
                    stage = ProjectTranscodeTaskStage.TRANSCRIBING,
                    progress = progress
                )
            }
        ).map { Unit }
    }

    private fun emitProgressAsync(
        taskId: Long,
        stage: ProjectTranscodeTaskStage,
        progress: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                transcodeTaskRepository.updateRunningTaskProgress(
                    taskId = taskId,
                    stage = stage,
                    progress = progress
                )
            }
        }
    }
}
