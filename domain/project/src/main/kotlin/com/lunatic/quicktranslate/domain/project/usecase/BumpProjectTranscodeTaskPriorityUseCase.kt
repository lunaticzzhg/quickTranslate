package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskType
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeQueueEngine
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskRepository

class BumpProjectTranscodeTaskPriorityUseCase(
    private val repository: ProjectTranscodeTaskRepository,
    private val queueEngine: ProjectTranscodeQueueEngine
) {
    suspend operator fun invoke(
        projectId: Long,
        taskType: String = ProjectTranscodeTaskType.TRANSCRIBE
    ) {
        val bumped = repository.bumpPendingTaskPriority(
            projectId = projectId,
            taskType = taskType
        )
        if (bumped) {
            queueEngine.signal()
        }
    }
}
