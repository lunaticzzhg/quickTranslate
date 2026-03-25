package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.ProjectSubtitle
import com.lunatic.quicktranslate.domain.project.repository.ProjectSubtitleRepository

class ReplaceProjectSubtitlesUseCase(
    private val repository: ProjectSubtitleRepository
) {
    suspend operator fun invoke(
        projectId: Long,
        subtitles: List<ProjectSubtitle>
    ) {
        repository.replaceProjectSubtitles(
            projectId = projectId,
            subtitles = subtitles
        )
    }
}
