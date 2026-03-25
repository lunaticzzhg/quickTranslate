package com.lunatic.quicktranslate.domain.project.repository

interface ProjectPlaybackStateRepository {
    suspend fun getPlaybackPosition(projectId: Long): Long?
    suspend fun savePlaybackPosition(
        projectId: Long,
        playbackPositionMs: Long
    )
}
