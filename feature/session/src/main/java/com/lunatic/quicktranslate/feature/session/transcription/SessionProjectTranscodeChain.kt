package com.lunatic.quicktranslate.feature.session.transcription

import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskStage
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskRepository
import com.lunatic.quicktranslate.domain.project.usecase.UpdateProjectMediaSourceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SessionProjectTranscodeChain(
    private val steps: List<SessionProjectTranscodeStep>
) {
    suspend fun run(context: SessionProjectTranscodeContext): Result<Unit> {
        val orderedSteps = steps.sortedBy { it.order }
        orderedSteps.forEach { step ->
            val result = runCatching {
                step.execute(context)
            }
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull() ?: IllegalStateException("Unknown transcode step failure."))
            }
        }
        return Result.success(Unit)
    }
}

interface SessionProjectTranscodeStep {
    val order: Int

    suspend fun execute(context: SessionProjectTranscodeContext)
}

class SessionProjectTranscodeContext(
    val task: ProjectTranscodeTask,
    private val transcodeTaskRepository: ProjectTranscodeTaskRepository
) {
    var localMedia: DownloadedTranscriptionMedia? = null

    suspend fun updateProgress(stage: ProjectTranscodeTaskStage, progress: Int?) {
        transcodeTaskRepository.updateRunningTaskProgress(
            taskId = task.id,
            stage = stage,
            progress = progress
        )
    }

    fun emitProgressAsync(stage: ProjectTranscodeTaskStage, progress: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                transcodeTaskRepository.updateRunningTaskProgress(
                    taskId = task.id,
                    stage = stage,
                    progress = progress
                )
            }
        }
    }
}

class SessionMarkResolvingStep : SessionProjectTranscodeStep {
    override val order: Int = 100

    override suspend fun execute(context: SessionProjectTranscodeContext) {
        context.updateProgress(
            stage = ProjectTranscodeTaskStage.RESOLVING,
            progress = 0
        )
    }
}

class SessionEnsureLocalMediaStep(
    private val downloadStage: SessionRemoteMediaDownloadStage
) : SessionProjectTranscodeStep {
    override val order: Int = 200

    override suspend fun execute(context: SessionProjectTranscodeContext) {
        context.localMedia = downloadStage.ensureLocalMedia(
            projectId = context.task.projectId,
            mediaUri = context.task.mediaUri,
            onProgress = { progress ->
                context.emitProgressAsync(
                    stage = ProjectTranscodeTaskStage.DOWNLOADING,
                    progress = progress
                )
            }
        )
    }
}

class SessionSyncProjectMediaSourceStep(
    private val updateProjectMediaSourceUseCase: UpdateProjectMediaSourceUseCase
) : SessionProjectTranscodeStep {
    override val order: Int = 300

    override suspend fun execute(context: SessionProjectTranscodeContext) {
        val localMedia = context.localMedia
            ?: error("Local media is missing before project media source sync step.")
        if (!localMedia.downloadedFromRemote) {
            context.updateProgress(
                stage = ProjectTranscodeTaskStage.TRANSCRIBING,
                progress = 0
            )
            return
        }
        updateProjectMediaSourceUseCase(
            projectId = context.task.projectId,
            mediaUri = localMedia.localPath,
            mimeType = localMedia.mimeType ?: "application/octet-stream"
        )
    }
}

class SessionTranscribeStep(
    private val pipeline: SessionTranscriptionPipeline
) : SessionProjectTranscodeStep {
    override val order: Int = 400

    override suspend fun execute(context: SessionProjectTranscodeContext) {
        val localMedia = context.localMedia
            ?: error("Local media is missing before transcription step.")
        pipeline.run(
            projectId = context.task.projectId,
            mediaUri = localMedia.localPath,
            onProgress = { progress ->
                context.emitProgressAsync(
                    stage = ProjectTranscodeTaskStage.TRANSCRIBING,
                    progress = progress
                )
            }
        ).getOrThrow()
    }
}
