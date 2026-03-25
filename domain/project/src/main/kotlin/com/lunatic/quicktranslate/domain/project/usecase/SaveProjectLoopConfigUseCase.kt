package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.ProjectLoopConfig
import com.lunatic.quicktranslate.domain.project.repository.ProjectLoopConfigRepository

class SaveProjectLoopConfigUseCase(
    private val repository: ProjectLoopConfigRepository
) {
    suspend operator fun invoke(
        projectId: Long,
        config: ProjectLoopConfig
    ) {
        repository.saveProjectLoopConfig(
            projectId = projectId,
            config = config
        )
    }
}
