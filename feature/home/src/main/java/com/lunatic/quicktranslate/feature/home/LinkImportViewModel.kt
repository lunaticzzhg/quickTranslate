package com.lunatic.quicktranslate.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.quicktranslate.domain.project.usecase.CreateProjectFromUrlUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class LinkImportViewModel(
    private val createProjectFromUrlUseCase: CreateProjectFromUrlUseCase
) : ViewModel() {
    private val mutableEffect = MutableSharedFlow<LinkImportEffect>()
    val effect: SharedFlow<LinkImportEffect> = mutableEffect.asSharedFlow()

    fun submitUrl(url: String) {
        viewModelScope.launch {
            runCatching {
                createProjectFromUrlUseCase(url)
            }.onSuccess { project ->
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
            }.onFailure { error ->
                emitEffect(
                    LinkImportEffect.ShowError(
                        error.message ?: "Failed to create project from link. Please retry."
                    )
                )
            }
        }
    }

    private suspend fun emitEffect(effect: LinkImportEffect) {
        mutableEffect.emit(effect)
    }
}

sealed interface LinkImportEffect {
    data class NavigateToSession(
        val projectId: Long,
        val media: ImportedMedia
    ) : LinkImportEffect

    data class ShowError(val message: String) : LinkImportEffect
}
