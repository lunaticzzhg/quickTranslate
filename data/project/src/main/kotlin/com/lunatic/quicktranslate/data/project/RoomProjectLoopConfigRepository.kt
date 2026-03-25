package com.lunatic.quicktranslate.data.project

import com.lunatic.quicktranslate.data.project.local.ProjectLoopConfigDao
import com.lunatic.quicktranslate.domain.project.model.ProjectLoopConfig
import com.lunatic.quicktranslate.domain.project.repository.ProjectLoopConfigRepository

class RoomProjectLoopConfigRepository(
    private val loopConfigDao: ProjectLoopConfigDao
) : ProjectLoopConfigRepository {
    override suspend fun getProjectLoopConfig(projectId: Long): ProjectLoopConfig? {
        return loopConfigDao.getByProjectId(projectId)?.toDomain()
    }

    override suspend fun saveProjectLoopConfig(
        projectId: Long,
        config: ProjectLoopConfig
    ) {
        loopConfigDao.upsert(config.toEntity(projectId))
    }
}
