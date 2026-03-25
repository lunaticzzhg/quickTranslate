package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.SubtitleStatus
import com.lunatic.quicktranslate.domain.project.repository.ProjectRepository

class UpdateProjectSubtitleStatusUseCase(
    private val repository: ProjectRepository
) {
    suspend operator fun invoke(projectId: Long, status: SubtitleStatus) {
        repository.updateProjectSubtitleStatus(
            projectId = projectId,
            status = status
        )
    }
}
