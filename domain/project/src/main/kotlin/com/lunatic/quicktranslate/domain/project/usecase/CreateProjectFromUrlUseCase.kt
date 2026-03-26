package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.CreateProjectInput
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolveResult
import com.lunatic.quicktranslate.domain.project.model.Project
import com.lunatic.quicktranslate.domain.project.repository.ProjectRepository
import java.net.URI

class CreateProjectFromUrlUseCase(
    private val resolvePlatformLinkUseCase: ResolvePlatformLinkUseCase,
    private val projectRepository: ProjectRepository,
    private val createProjectUseCase: CreateProjectUseCase,
    private val enqueueProjectTranscodeTaskUseCase: EnqueueProjectTranscodeTaskUseCase
) {
    suspend operator fun invoke(sourceUrl: String): Project {
        val normalizedUrl = sourceUrl.trim()
        val resolved = resolvePlatformLinkUseCase(normalizedUrl)
        val resolvedMediaUrl = when (resolved) {
            is PlatformLinkResolveResult.Success -> {
                resolved.media.items.firstOrNull()?.resolvedMediaUrl
                    ?: throw IllegalArgumentException("No downloadable media was found.")
            }
            is PlatformLinkResolveResult.Failure -> {
                throw IllegalArgumentException(resolved.error.message)
            }
        }

        projectRepository.getProjectByMediaUri(normalizedUrl)?.let { existing ->
            return existing
        }

        val project = createProjectUseCase(
            CreateProjectInput(
                displayName = normalizedUrl.defaultDisplayName(),
                mediaUri = resolvedMediaUrl,
                sourceUri = normalizedUrl,
                mimeType = "application/octet-stream",
                durationMs = -1L
            )
        )
        enqueueProjectTranscodeTaskUseCase(
            projectId = project.id,
            mediaUri = resolvedMediaUrl
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
