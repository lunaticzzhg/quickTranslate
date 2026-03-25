package com.lunatic.quicktranslate.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.quicktranslate.domain.project.model.CreateProjectInput
import com.lunatic.quicktranslate.domain.project.usecase.CreateProjectUseCase
import com.lunatic.quicktranslate.domain.project.usecase.ObserveRecentProjectsUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(
    private val createProjectUseCase: CreateProjectUseCase,
    observeRecentProjectsUseCase: ObserveRecentProjectsUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = mutableState.asStateFlow()

    private val mutableEffect = MutableSharedFlow<HomeEffect>()
    val effect: SharedFlow<HomeEffect> = mutableEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            observeRecentProjectsUseCase().collect { projects ->
                mutableState.value = mutableState.value.copy(
                    recentProjects = projects.map { project ->
                        RecentProjectUi(
                            id = project.id,
                            displayName = project.displayName,
                            mimeType = project.mimeType
                        )
                    }
                )
            }
        }
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.PrimaryActionClicked -> emitEffect(HomeEffect.LaunchFilePicker)
            is HomeIntent.MediaImported -> createProjectThenNavigate(intent.media)
            is HomeIntent.MediaImportFailed -> emitEffect(HomeEffect.ShowError(intent.message))
        }
    }

    private fun createProjectThenNavigate(media: ImportedMedia) {
        viewModelScope.launch {
            runCatching {
                createProjectUseCase(
                    CreateProjectInput(
                        displayName = media.displayName,
                        mediaUri = media.uri,
                        mimeType = media.mimeType,
                        durationMs = media.durationMs
                    )
                )
            }.onSuccess {
                emitEffect(HomeEffect.NavigateToSession(media))
            }.onFailure {
                emitEffect(
                    HomeEffect.ShowError(
                        "Failed to create project record. Please try again."
                    )
                )
            }
        }
    }

    private fun emitEffect(effect: HomeEffect) {
        viewModelScope.launch {
            mutableEffect.emit(effect)
        }
    }
}
