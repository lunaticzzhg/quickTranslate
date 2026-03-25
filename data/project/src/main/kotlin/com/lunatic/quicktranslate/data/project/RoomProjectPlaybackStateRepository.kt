package com.lunatic.quicktranslate.data.project

import com.lunatic.quicktranslate.data.project.local.ProjectPlaybackStateDao
import com.lunatic.quicktranslate.data.project.local.ProjectPlaybackStateEntity
import com.lunatic.quicktranslate.domain.project.repository.ProjectPlaybackStateRepository

class RoomProjectPlaybackStateRepository(
    private val playbackStateDao: ProjectPlaybackStateDao
) : ProjectPlaybackStateRepository {
    override suspend fun getPlaybackPosition(projectId: Long): Long? {
        return playbackStateDao.getByProjectId(projectId)?.playbackPositionMs
    }

    override suspend fun savePlaybackPosition(
        projectId: Long,
        playbackPositionMs: Long
    ) {
        playbackStateDao.upsert(
            ProjectPlaybackStateEntity(
                projectId = projectId,
                playbackPositionMs = playbackPositionMs.coerceAtLeast(0L),
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }
}
