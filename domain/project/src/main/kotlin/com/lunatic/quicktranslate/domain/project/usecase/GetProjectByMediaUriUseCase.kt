package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.Project
import com.lunatic.quicktranslate.domain.project.repository.ProjectRepository

class GetProjectByMediaUriUseCase(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(mediaUri: String): Project? {
        return repository.getProjectByMediaUri(mediaUri)
    }
}
