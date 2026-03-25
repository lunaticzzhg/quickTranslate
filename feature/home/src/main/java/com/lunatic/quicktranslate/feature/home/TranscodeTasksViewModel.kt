package com.lunatic.quicktranslate.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.quicktranslate.domain.project.model.Project
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskStatus
import com.lunatic.quicktranslate.domain.project.usecase.ObserveRecentProjectsUseCase
import com.lunatic.quicktranslate.domain.project.usecase.ObserveTranscodeDashboardTasksUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TranscodeTasksViewModel(
    observeTranscodeDashboardTasksUseCase: ObserveTranscodeDashboardTasksUseCase,
    observeRecentProjectsUseCase: ObserveRecentProjectsUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(TranscodeTasksState())
    val state: StateFlow<TranscodeTasksState> = mutableState.asStateFlow()

    private val mutableEffect = MutableSharedFlow<TranscodeTasksEffect>()
    val effect: SharedFlow<TranscodeTasksEffect> = mutableEffect.asSharedFlow()

    private var projectsById: Map<Long, Project> = emptyMap()
    private var latestTasks: List<ProjectTranscodeTask> = emptyList()

    init {
        viewModelScope.launch {
            observeRecentProjectsUseCase().collect { projects ->
                projectsById = projects.associateBy { it.id }
                rebuildTaskList(latestTasks)
            }
        }
        viewModelScope.launch {
            observeTranscodeDashboardTasksUseCase().collect { tasks ->
                latestTasks = tasks
                rebuildTaskList(tasks)
            }
        }
    }

    fun onIntent(intent: TranscodeTasksIntent) {
        when (intent) {
            TranscodeTasksIntent.BackClicked -> emitEffect(TranscodeTasksEffect.NavigateBack)
            is TranscodeTasksIntent.TaskClicked -> {
                emitEffect(
                    TranscodeTasksEffect.NavigateToSession(
                        projectId = intent.task.projectId,
                        media = ImportedMedia(
                            uri = intent.task.mediaUri,
                            displayName = intent.task.projectName,
                            mimeType = intent.task.mimeType,
                            durationMs = intent.task.durationMs
                        )
                    )
                )
            }
        }
    }

    private fun rebuildTaskList(tasks: List<ProjectTranscodeTask>) {
        if (tasks.isEmpty()) {
            mutableState.value = mutableState.value.copy(tasks = emptyList())
            return
        }
        val ordered = tasks.sortedWith(
            compareBy<ProjectTranscodeTask> {
                when (it.status) {
                    ProjectTranscodeTaskStatus.RUNNING -> 0
                    ProjectTranscodeTaskStatus.PENDING -> 1
                    ProjectTranscodeTaskStatus.FAILED -> 2
                    else -> 3
                }
            }
                .thenByDescending { it.boostSeq }
                .thenByDescending { it.basePriority }
                .thenBy { it.createdAtEpochMs }
        )
        mutableState.value = mutableState.value.copy(
            tasks = ordered.map { task ->
                val project = projectsById[task.projectId]
                task.toTaskUi(project)
            }
        )
    }

    private fun ProjectTranscodeTask.toTaskUi(project: Project?): TranscodeTaskUi {
        return TranscodeTaskUi(
            taskId = id,
            projectId = projectId,
            projectName = project?.displayName ?: "Project #$projectId",
            mediaUri = project?.mediaUri ?: mediaUri,
            mimeType = project?.mimeType ?: "application/octet-stream",
            durationMs = project?.durationMs ?: -1L,
            statusLabel = status.toStatusLabel(),
            isFailed = status == ProjectTranscodeTaskStatus.FAILED,
            errorMessage = errorMessage
        )
    }

    private fun ProjectTranscodeTaskStatus.toStatusLabel(): String {
        return when (this) {
            ProjectTranscodeTaskStatus.RUNNING -> "Running"
            ProjectTranscodeTaskStatus.PENDING -> "Queued"
            ProjectTranscodeTaskStatus.FAILED -> "Failed"
            ProjectTranscodeTaskStatus.SUCCEEDED -> "Succeeded"
            ProjectTranscodeTaskStatus.CANCELED -> "Canceled"
        }
    }

    private fun emitEffect(effect: TranscodeTasksEffect) {
        viewModelScope.launch {
            mutableEffect.emit(effect)
        }
    }
}
