package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.Project
import com.lunatic.quicktranslate.domain.project.repository.ProjectRepository

class GetProjectByIdUseCase(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(projectId: Long): Project? {
        return repository.getProjectById(projectId)
    }
}

