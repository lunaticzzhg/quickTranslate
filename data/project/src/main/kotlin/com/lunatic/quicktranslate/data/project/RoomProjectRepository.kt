package com.lunatic.quicktranslate.data.project

import com.lunatic.quicktranslate.data.project.local.ProjectDao
import com.lunatic.quicktranslate.data.project.local.ProjectEntity
import com.lunatic.quicktranslate.domain.project.model.CreateProjectInput
import com.lunatic.quicktranslate.domain.project.model.Project
import com.lunatic.quicktranslate.domain.project.model.SubtitleStatus
import com.lunatic.quicktranslate.domain.project.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomProjectRepository(
    private val projectDao: ProjectDao
) : ProjectRepository {
    override fun observeRecentProjects(): Flow<List<Project>> {
        return projectDao.observeRecentProjects().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getProjectById(projectId: Long): Project? {
        return projectDao.getById(projectId)?.toDomain()
    }

    override suspend fun getProjectByMediaUri(mediaUri: String): Project? {
        if (mediaUri.isBlank()) {
            return null
        }
        return projectDao.getByMediaUri(mediaUri)?.toDomain()
    }

    override suspend fun createProject(input: CreateProjectInput): Project {
        val now = System.currentTimeMillis()
        val entity = ProjectEntity(
            displayName = input.displayName,
            mediaUri = input.mediaUri,
            sourceUri = input.sourceUri.ifBlank { input.mediaUri },
            mimeType = input.mimeType,
            durationMs = input.durationMs,
            subtitleStatus = SubtitleStatus.NOT_STARTED.name,
            updatedAtEpochMs = now
        )
        val id = projectDao.insert(entity)
        return projectDao.getById(id)?.toDomain()
            ?: entity.copy(id = id).toDomain()
    }

    override suspend fun deleteProject(projectId: Long) {
        projectDao.deleteById(projectId)
    }

    override suspend fun updateProjectSubtitleStatus(
        projectId: Long,
        status: SubtitleStatus
    ) {
        projectDao.updateSubtitleStatus(
            id = projectId,
            subtitleStatus = status.name,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    override suspend fun updateProjectMediaSource(
        projectId: Long,
        mediaUri: String,
        mimeType: String
    ) {
        projectDao.updateMediaSource(
            id = projectId,
            mediaUri = mediaUri,
            mimeType = mimeType,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }
}
