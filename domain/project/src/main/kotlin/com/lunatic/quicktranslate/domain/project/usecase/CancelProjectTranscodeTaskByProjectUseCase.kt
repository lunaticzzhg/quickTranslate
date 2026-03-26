package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskType
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskRepository

class CancelProjectTranscodeTaskByProjectUseCase(
    private val repository: ProjectTranscodeTaskRepository
) {
    suspend operator fun invoke(
        projectId: Long,
        taskType: String = ProjectTranscodeTaskType.TRANSCRIBE
    ): Boolean {
        if (projectId <= 0L) {
            return false
        }
        return repository.cancelTaskByProject(
            projectId = projectId,
            taskType = taskType
        )
    }
}

