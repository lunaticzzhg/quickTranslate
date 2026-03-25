package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.ProjectLoopConfig
import com.lunatic.quicktranslate.domain.project.repository.ProjectLoopConfigRepository

class GetProjectLoopConfigUseCase(
    private val repository: ProjectLoopConfigRepository
) {
    suspend operator fun invoke(projectId: Long): ProjectLoopConfig? {
        return repository.getProjectLoopConfig(projectId)
    }
}
