package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.CreateProjectInput
import com.lunatic.quicktranslate.domain.project.model.Project
import com.lunatic.quicktranslate.domain.project.repository.ProjectRepository

class CreateProjectFromResolvedLinkUseCase(
    private val projectRepository: ProjectRepository,
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
        projectRepository.getProjectByMediaUri(normalizedSource)?.let { existing ->
            return existing
        }

        val project = createProjectUseCase(
            CreateProjectInput(
                displayName = displayName.ifBlank { normalizedSource },
                mediaUri = normalizedResolved,
                sourceUri = normalizedSource,
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
