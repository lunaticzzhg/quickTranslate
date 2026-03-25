package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.repository.ProjectPlaybackStateRepository

class GetProjectPlaybackPositionUseCase(
    private val repository: ProjectPlaybackStateRepository
) {
    suspend operator fun invoke(projectId: Long): Long? {
        return repository.getPlaybackPosition(projectId)
    }
}
