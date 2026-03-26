package com.lunatic.quicktranslate.domain.project.repository

import com.lunatic.quicktranslate.domain.project.model.CreateProjectInput
import com.lunatic.quicktranslate.domain.project.model.Project
import com.lunatic.quicktranslate.domain.project.model.SubtitleStatus
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeRecentProjects(): Flow<List<Project>>
    suspend fun getProjectById(projectId: Long): Project?
    suspend fun getProjectByMediaUri(mediaUri: String): Project?
    suspend fun createProject(input: CreateProjectInput): Project
    suspend fun deleteProject(projectId: Long)
    suspend fun updateProjectSubtitleStatus(
        projectId: Long,
        status: SubtitleStatus
    )
}
