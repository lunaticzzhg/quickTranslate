package com.lunatic.quicktranslate.domain.project.repository

import com.lunatic.quicktranslate.domain.project.model.ProjectSubtitle

interface ProjectSubtitleRepository {
    suspend fun getProjectSubtitles(projectId: Long): List<ProjectSubtitle>
    suspend fun replaceProjectSubtitles(
        projectId: Long,
        subtitles: List<ProjectSubtitle>
    )
}
