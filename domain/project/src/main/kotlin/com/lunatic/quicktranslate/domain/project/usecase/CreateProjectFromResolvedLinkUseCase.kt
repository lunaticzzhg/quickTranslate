package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.CreateProjectInput
import com.lunatic.quicktranslate.domain.project.model.Project

class CreateProjectFromResolvedLinkUseCase(
    private val getProjectByMediaUriUseCase: GetProjectByMediaUriUseCase,
    private val createProjectUseCase: CreateProjectUseCase,
    private val enqueueProjectTranscodeTaskUseCase: EnqueueProjectTranscodeTaskUseCase
) {
    suspend operator fun invoke(
        sourceUrl: String,
        resolvedMediaUrl: String,
        displayName: String,
        mimeType: String?
    ): Project {
        val normalizedSource = sourceUrl.trim()
        val normalizedResolved = resolvedMediaUrl.trim()
        getProjectByMediaUriUseCase(normalizedResolved)?.let { existing ->
            enqueueProjectTranscodeTaskUseCase(
                projectId = existing.id,
                mediaUri = normalizedResolved
            )
            return existing
        }

        val project = createProjectUseCase(
            CreateProjectInput(
                displayName = displayName.ifBlank { normalizedSource },
                mediaUri = normalizedResolved,
                mimeType = mimeType ?: "application/octet-stream",
                durationMs = -1L
            )
        )
        enqueueProjectTranscodeTaskUseCase(
            projectId = project.id,
            mediaUri = normalizedResolved
        )
        return project
    }
}

