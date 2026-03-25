package com.lunatic.quicktranslate.data.project

import com.lunatic.quicktranslate.data.project.local.ProjectSubtitleEntity
import com.lunatic.quicktranslate.domain.project.model.ProjectSubtitle

fun ProjectSubtitleEntity.toDomain(): ProjectSubtitle {
    return ProjectSubtitle(
        sequenceIndex = sequenceIndex,
        startMs = startMs,
        endMs = endMs,
        text = text
    )
}

fun ProjectSubtitle.toEntity(projectId: Long): ProjectSubtitleEntity {
    return ProjectSubtitleEntity(
        projectId = projectId,
        sequenceIndex = sequenceIndex,
        startMs = startMs,
        endMs = endMs,
        text = text
    )
}
