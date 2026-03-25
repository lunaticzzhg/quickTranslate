package com.lunatic.quicktranslate.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.quicktranslate.domain.project.model.CreateProjectInput
import com.lunatic.quicktranslate.domain.project.model.Project
import com.lunatic.quicktranslate.domain.project.model.SubtitleStatus
import com.lunatic.quicktranslate.domain.project.usecase.CreateProjectUseCase
import com.lunatic.quicktranslate.domain.project.usecase.DeleteProjectUseCase
import com.lunatic.quicktranslate.domain.project.usecase.ObserveRecentProjectsUseCase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(
    private val createProjectUseCase: CreateProjectUseCase,
    private val deleteProjectUseCase: DeleteProjectUseCase,
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
                        project.toRecentProjectUi()
                    }
                )
            }
        }
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.PrimaryActionClicked -> emitEffect(HomeEffect.LaunchFilePicker)
            is HomeIntent.RecentProjectClicked -> openRecentProject(intent.projectId)
            is HomeIntent.MediaImported -> createProjectThenNavigate(intent.media)
            is HomeIntent.MediaImportFailed -> emitEffect(HomeEffect.ShowError(intent.message))
            is HomeIntent.DeleteProjectClicked -> showDeleteDialog(intent.projectId)
            HomeIntent.ConfirmDeleteProject -> confirmDeleteProject()
            HomeIntent.DismissDeleteDialog -> dismissDeleteDialog()
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
            }.onSuccess { project ->
                emitEffect(
                    HomeEffect.NavigateToSession(
                        projectId = project.id,
                        media = media
                    )
                )
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

    private fun showDeleteDialog(projectId: Long) {
        val target = mutableState.value.recentProjects.firstOrNull { it.id == projectId } ?: return
        mutableState.value = mutableState.value.copy(
            pendingDeletionProject = target
        )
    }

    private fun dismissDeleteDialog() {
        mutableState.value = mutableState.value.copy(
            pendingDeletionProject = null
        )
    }

    private fun confirmDeleteProject() {
        val target = mutableState.value.pendingDeletionProject ?: return
        viewModelScope.launch {
            runCatching {
                deleteProjectUseCase(target.id)
            }.onFailure {
                emitEffect(HomeEffect.ShowError("Failed to delete project. Please try again."))
            }
            dismissDeleteDialog()
        }
    }

    private fun Project.toRecentProjectUi(): RecentProjectUi {
        return RecentProjectUi(
            id = id,
            mediaUri = mediaUri,
            mimeType = mimeType,
            durationMs = durationMs,
            displayName = displayName,
            mediaTypeLabel = mimeType.toMediaTypeLabel(),
            subtitleStatusLabel = subtitleStatus.toStatusLabel(),
            recentLearnedAtLabel = updatedAtEpochMs.toRecentLearnedAtLabel()
        )
    }

    private fun String.toMediaTypeLabel(): String {
        return when {
            startsWith("audio/") -> "Audio"
            startsWith("video/") -> "Video"
            else -> "Unknown"
        }
    }

    private fun SubtitleStatus.toStatusLabel(): String {
        return when (this) {
            SubtitleStatus.NOT_STARTED -> "Not Started"
            SubtitleStatus.PROCESSING -> "Processing"
            SubtitleStatus.COMPLETED -> "Completed"
            SubtitleStatus.FAILED -> "Failed"
        }
    }

    private fun Long.toRecentLearnedAtLabel(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
        return formatter.format(Instant.ofEpochMilli(this))
    }

    private fun openRecentProject(projectId: Long) {
        val project = mutableState.value.recentProjects.firstOrNull { it.id == projectId }
        if (project == null) {
            emitEffect(HomeEffect.ShowError("Project not found. Please refresh and retry."))
            return
        }
        emitEffect(
            HomeEffect.NavigateToSession(
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
