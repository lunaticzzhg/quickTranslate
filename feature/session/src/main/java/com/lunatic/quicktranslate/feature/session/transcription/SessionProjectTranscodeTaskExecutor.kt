package com.lunatic.quicktranslate.feature.session.transcription

import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskExecutor

class SessionProjectTranscodeTaskExecutor(
    private val pipeline: SessionTranscriptionPipeline
) : ProjectTranscodeTaskExecutor {
    override suspend fun execute(task: ProjectTranscodeTask): Result<Unit> {
        return pipeline.run(
            projectId = task.projectId,
            mediaUri = task.mediaUri
        ).map { Unit }
    }
}
