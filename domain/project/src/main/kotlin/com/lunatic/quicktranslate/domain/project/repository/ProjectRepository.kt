package com.lunatic.quicktranslate.domain.project.repository

import com.lunatic.quicktranslate.domain.project.model.CreateProjectInput
import com.lunatic.quicktranslate.domain.project.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeRecentProjects(): Flow<List<Project>>
    suspend fun createProject(input: CreateProjectInput): Project
    suspend fun deleteProject(projectId: Long)
}
