package com.lunatic.quicktranslate.data.project

import com.lunatic.quicktranslate.data.project.local.ProjectEntity
import com.lunatic.quicktranslate.domain.project.model.Project

fun ProjectEntity.toDomain(): Project {
    return Project(
        id = id,
        displayName = displayName,
        mediaUri = mediaUri,
        mimeType = mimeType,
        durationMs = durationMs,
        subtitleStatus = subtitleStatus.toSubtitleStatus(),
        updatedAtEpochMs = updatedAtEpochMs
    )
}
