package com.lunatic.quicktranslate.data.project

import com.lunatic.quicktranslate.data.project.local.ProjectSubtitleDao
import com.lunatic.quicktranslate.domain.project.model.ProjectSubtitle
import com.lunatic.quicktranslate.domain.project.repository.ProjectSubtitleRepository

class RoomProjectSubtitleRepository(
    private val subtitleDao: ProjectSubtitleDao
) : ProjectSubtitleRepository {
    override suspend fun getProjectSubtitles(projectId: Long): List<ProjectSubtitle> {
        return subtitleDao.getByProjectId(projectId).map { it.toDomain() }
    }

    override suspend fun replaceProjectSubtitles(
        projectId: Long,
        subtitles: List<ProjectSubtitle>
    ) {
        subtitleDao.replaceByProjectId(
            projectId = projectId,
            entities = subtitles.map { it.toEntity(projectId) }
        )
    }
}
