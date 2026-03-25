package com.lunatic.quicktranslate.domain.project.repository

import com.lunatic.quicktranslate.domain.project.model.ProjectLoopConfig

interface ProjectLoopConfigRepository {
    suspend fun getProjectLoopConfig(projectId: Long): ProjectLoopConfig?
    suspend fun saveProjectLoopConfig(
        projectId: Long,
        config: ProjectLoopConfig
    )
}
