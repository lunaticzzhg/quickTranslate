package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.repository.ProjectRepository

class UpdateProjectMediaSourceUseCase(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(
        projectId: Long,
        mediaUri: String,
        mimeType: String
    ) {
        repository.updateProjectMediaSource(
            projectId = projectId,
            mediaUri = mediaUri,
            mimeType = mimeType
        )
    }
}

