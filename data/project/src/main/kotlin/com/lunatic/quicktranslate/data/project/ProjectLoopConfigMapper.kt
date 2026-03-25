package com.lunatic.quicktranslate.data.project

import com.lunatic.quicktranslate.data.project.local.ProjectLoopConfigEntity
import com.lunatic.quicktranslate.domain.project.model.ProjectLoopConfig

fun ProjectLoopConfigEntity.toDomain(): ProjectLoopConfig {
    return ProjectLoopConfig(
        selectedRangeStartIndex = selectedRangeStartIndex,
        selectedRangeEndIndex = selectedRangeEndIndex,
        loopCountOptionName = loopCountOptionName
    )
}

fun ProjectLoopConfig.toEntity(projectId: Long): ProjectLoopConfigEntity {
    return ProjectLoopConfigEntity(
        projectId = projectId,
        selectedRangeStartIndex = selectedRangeStartIndex,
        selectedRangeEndIndex = selectedRangeEndIndex,
        loopCountOptionName = loopCountOptionName,
        updatedAtEpochMs = System.currentTimeMillis()
    )
}
