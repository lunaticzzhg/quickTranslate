package com.lunatic.quicktranslate.feature.session.playback

import com.lunatic.quicktranslate.domain.project.usecase.GetProjectPlaybackPositionUseCase
import com.lunatic.quicktranslate.domain.project.usecase.UpdateProjectPlaybackPositionUseCase

class SessionPlaybackCoordinator(
    private val getProjectPlaybackPositionUseCase: GetProjectPlaybackPositionUseCase,
    private val updateProjectPlaybackPositionUseCase: UpdateProjectPlaybackPositionUseCase
) {
    suspend fun restorePlaybackPosition(projectId: Long): Long? {
        if (projectId <= 0L) {
            return null
        }
        return runCatching {
            getProjectPlaybackPositionUseCase(projectId)
        }.getOrNull()
    }

    suspend fun persistPlaybackPosition(
        projectId: Long,
        playbackPositionMs: Long
    ) {
        if (projectId <= 0L) {
            return
        }
        runCatching {
            updateProjectPlaybackPositionUseCase(
                projectId = projectId,
                playbackPositionMs = playbackPositionMs.coerceAtLeast(0L)
            )
        }
    }
}
