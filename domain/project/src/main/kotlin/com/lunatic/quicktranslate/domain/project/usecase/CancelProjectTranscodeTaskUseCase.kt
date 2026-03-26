package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskRepository

class CancelProjectTranscodeTaskUseCase(
    private val repository: ProjectTranscodeTaskRepository
) {
    suspend operator fun invoke(taskId: Long): Boolean {
        if (taskId <= 0L) {
            return false
        }
        return repository.cancelTask(taskId)
    }
}

