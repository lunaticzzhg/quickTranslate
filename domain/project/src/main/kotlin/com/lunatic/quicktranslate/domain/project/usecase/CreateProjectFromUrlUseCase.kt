package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.CreateProjectInput
import com.lunatic.quicktranslate.domain.project.model.Project
import java.net.URI

class CreateProjectFromUrlUseCase(
    private val getProjectByMediaUriUseCase: GetProjectByMediaUriUseCase,
    private val createProjectUseCase: CreateProjectUseCase,
    private val enqueueProjectTranscodeTaskUseCase: EnqueueProjectTranscodeTaskUseCase
) {
    suspend operator fun invoke(sourceUrl: String): Project {
        val normalizedUrl = sourceUrl.trim()
        getProjectByMediaUriUseCase(normalizedUrl)?.let { existing ->
            enqueueProjectTranscodeTaskUseCase(
                projectId = existing.id,
                mediaUri = normalizedUrl
            )
            return existing
        }

        val project = createProjectUseCase(
            CreateProjectInput(
                displayName = normalizedUrl.defaultDisplayName(),
                mediaUri = normalizedUrl,
                mimeType = "application/octet-stream",
                durationMs = -1L
            )
        )
        enqueueProjectTranscodeTaskUseCase(
            projectId = project.id,
            mediaUri = normalizedUrl
        )
        return project
    }
}

private fun String.defaultDisplayName(): String {
    val uri = runCatching { URI(this) }.getOrNull()
    val host = uri?.host.orEmpty()
    val path = uri?.path.orEmpty()
    val tail = path.substringAfterLast('/').ifBlank { "media-link" }
    return if (host.isNotBlank()) {
        "$host/$tail"
    } else {
        "Imported media link"
    }
}

