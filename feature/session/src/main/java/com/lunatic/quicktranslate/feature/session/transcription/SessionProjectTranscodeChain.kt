package com.lunatic.quicktranslate.feature.session.transcription

import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskStage
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskRepository
import com.lunatic.quicktranslate.domain.project.usecase.UpdateProjectMediaSourceUseCase
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var canceledSnapshot: Boolean = false

    init {
        progressScope.launch {
            while (isActive) {
                canceledSnapshot = transcodeTaskRepository.isTaskCanceled(task.id)
                if (canceledSnapshot) {
                    break
                }
                delay(500L)
            }
        }
    }

    suspend fun updateProgress(stage: ProjectTranscodeTaskStage, progress: Int?) {
        transcodeTaskRepository.updateRunningTaskProgress(
            taskId = task.id,
            stage = stage,
            progress = toOverallProgress(stage, progress)
        )
    }

    fun updateProgressAsync(stage: ProjectTranscodeTaskStage, progress: Int?) {
        if (canceledSnapshot) {
            return
        }
        progressScope.launch {
            transcodeTaskRepository.updateRunningTaskProgress(
                taskId = task.id,
                stage = stage,
                progress = toOverallProgress(stage, progress)
            )
        }
    }

    suspend fun refreshCanceledState() {
        canceledSnapshot = transcodeTaskRepository.isTaskCanceled(task.id)
    }

    fun ensureNotCanceled() {
        if (canceledSnapshot) {
            throw CancellationException("Task ${task.id} was canceled.")
        }
    }

    fun isCanceled(): Boolean = canceledSnapshot

    private fun toOverallProgress(stage: ProjectTranscodeTaskStage, stageProgress: Int?): Int? {
        val bounded = stageProgress?.coerceIn(0, 100)
        return when (stage) {
            ProjectTranscodeTaskStage.QUEUED -> 0
            ProjectTranscodeTaskStage.RESOLVING -> bounded?.let { scale(it, 0, 10) } ?: 0
            ProjectTranscodeTaskStage.DOWNLOADING -> bounded?.let { scale(it, 10, 60) } ?: 10
            ProjectTranscodeTaskStage.TRANSCRIBING -> bounded?.let { scale(it, 60, 99) } ?: 60
            ProjectTranscodeTaskStage.SUCCEEDED -> 100
            ProjectTranscodeTaskStage.FAILED -> bounded?.coerceIn(0, 99)
            ProjectTranscodeTaskStage.CANCELED -> bounded?.coerceIn(0, 99)
        }
    }

    private fun scale(progress: Int, start: Int, end: Int): Int {
        val span = (end - start).coerceAtLeast(0)
        return start + ((span * progress) / 100)
    }
}

class SessionMarkResolvingStep : SessionProjectTranscodeStep {
    override val order: Int = 100

    override suspend fun execute(context: SessionProjectTranscodeContext) {
        context.refreshCanceledState()
        context.ensureNotCanceled()
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
        context.refreshCanceledState()
        context.ensureNotCanceled()
        context.updateProgress(
            stage = ProjectTranscodeTaskStage.DOWNLOADING,
            progress = 0
        )
        context.localMedia = downloadStage.ensureLocalMedia(
            projectId = context.task.projectId,
            mediaUri = context.task.mediaUri,
            onProgress = progress@{ progress ->
                if (context.isCanceled()) {
                    return@progress
                }
                context.updateProgressAsync(
                    stage = ProjectTranscodeTaskStage.DOWNLOADING,
                    progress = progress
                )
            }
        )
        context.refreshCanceledState()
        context.ensureNotCanceled()
    }
}

class SessionSyncProjectMediaSourceStep(
    private val updateProjectMediaSourceUseCase: UpdateProjectMediaSourceUseCase
) : SessionProjectTranscodeStep {
    override val order: Int = 300

    override suspend fun execute(context: SessionProjectTranscodeContext) {
        context.refreshCanceledState()
        context.ensureNotCanceled()
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
        context.refreshCanceledState()
        context.ensureNotCanceled()
        val localMedia = context.localMedia
            ?: error("Local media is missing before transcription step.")
        context.updateProgress(
            stage = ProjectTranscodeTaskStage.TRANSCRIBING,
            progress = 0
        )
        pipeline.run(
            projectId = context.task.projectId,
            mediaUri = localMedia.localPath,
            onProgress = progress@{ progress ->
                if (context.isCanceled()) {
                    return@progress
                }
                context.updateProgressAsync(
                    stage = ProjectTranscodeTaskStage.TRANSCRIBING,
                    progress = progress
                )
            }
        ).getOrThrow()
        context.refreshCanceledState()
        context.ensureNotCanceled()
    }
}
