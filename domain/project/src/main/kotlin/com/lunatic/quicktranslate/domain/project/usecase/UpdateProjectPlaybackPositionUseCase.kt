package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.repository.ProjectPlaybackStateRepository

class UpdateProjectPlaybackPositionUseCase(
    private val repository: ProjectPlaybackStateRepository
) {
    suspend operator fun invoke(
        projectId: Long,
        playbackPositionMs: Long
    ) {
        repository.savePlaybackPosition(
            projectId = projectId,
            playbackPositionMs = playbackPositionMs
        )
    }
}
