package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.ProjectSubtitle
import com.lunatic.quicktranslate.domain.project.repository.ProjectSubtitleRepository

class GetProjectSubtitlesUseCase(
    private val repository: ProjectSubtitleRepository
) {
    suspend operator fun invoke(projectId: Long): List<ProjectSubtitle> {
        return repository.getProjectSubtitles(projectId)
    }
}
