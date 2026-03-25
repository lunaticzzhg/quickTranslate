package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskType
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeQueueEngine
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskRepository

class EnqueueProjectTranscodeTaskUseCase(
    private val repository: ProjectTranscodeTaskRepository,
    private val queueEngine: ProjectTranscodeQueueEngine
) {
    suspend operator fun invoke(
        projectId: Long,
        mediaUri: String,
        taskType: String = ProjectTranscodeTaskType.TRANSCRIBE
    ) {
        repository.enqueueOrRefresh(
            projectId = projectId,
            mediaUri = mediaUri,
            taskType = taskType
        )
        queueEngine.signal()
    }
}
