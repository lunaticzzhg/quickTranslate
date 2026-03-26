package com.lunatic.quicktranslate.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolveResult
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolvedItem
import com.lunatic.quicktranslate.domain.project.usecase.CreateProjectFromResolvedLinkUseCase
import com.lunatic.quicktranslate.domain.project.usecase.ResolvePlatformLinkUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class LinkImportViewModel(
    private val resolvePlatformLinkUseCase: ResolvePlatformLinkUseCase,
    private val createProjectFromResolvedLinkUseCase: CreateProjectFromResolvedLinkUseCase
) : ViewModel() {
    private val mutableEffect = MutableSharedFlow<LinkImportEffect>()
    val effect: SharedFlow<LinkImportEffect> = mutableEffect.asSharedFlow()

    fun submitUrl(url: String) {
        viewModelScope.launch {
            runCatching {
                resolvePlatformLinkUseCase(url)
            }.onSuccess { result ->
                when (result) {
                    is PlatformLinkResolveResult.Success -> {
                        val candidate = selectPreferredCandidate(result.media.items)
                        if (candidate == null) {
                            emitEffect(
                                LinkImportEffect.ShowError("No downloadable media was found.")
                            )
                            return@onSuccess
                        }
                        val projectMediaUrl = if (result.media.isDirectMedia) {
                            candidate.resolvedMediaUrl
                        } else {
                            url.trim()
                        }
                        createProjectFromResolvedLinkUseCase(
                            sourceUrl = url.trim(),
                            resolvedMediaUrl = projectMediaUrl,
                            displayName = result.media.suggestedProjectName,
                            mimeType = candidate.mimeType
                        ).let { project ->
                            emitEffect(
                                LinkImportEffect.NavigateToSession(
                                    projectId = project.id,
                                    media = ImportedMedia(
                                        uri = project.mediaUri,
                                        displayName = project.displayName,
                                        mimeType = project.mimeType,
                                        durationMs = project.durationMs
                                    )
                                )
                            )
                        }
                    }
                    is PlatformLinkResolveResult.Failure -> {
                        emitEffect(LinkImportEffect.ShowError(result.error.message))
                    }
                }
            }.onFailure { error ->
                emitEffect(
                    LinkImportEffect.ShowError(
                        error.message ?: "Failed to parse link. Please retry."
                    )
                )
            }
        }
    }

    private suspend fun emitEffect(effect: LinkImportEffect) {
        mutableEffect.emit(effect)
    }

    private fun selectPreferredCandidate(
        items: List<PlatformLinkResolvedItem>
    ): PlatformLinkResolvedItem? {
        if (items.isEmpty()) {
            return null
        }
        return items.firstOrNull { it.id.startsWith("durl_") }
            ?: items.firstOrNull { it.mimeType?.startsWith("audio/") == true }
            ?: items.first()
    }
}

sealed interface LinkImportEffect {
    data class NavigateToSession(
        val projectId: Long,
        val media: ImportedMedia
    ) : LinkImportEffect

    data class ShowError(val message: String) : LinkImportEffect
}
