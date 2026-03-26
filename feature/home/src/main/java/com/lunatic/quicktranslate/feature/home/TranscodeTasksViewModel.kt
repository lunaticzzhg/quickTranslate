package com.lunatic.quicktranslate.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.quicktranslate.domain.project.model.Project
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskStage
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskStatus
import com.lunatic.quicktranslate.domain.project.repository.ProjectRepository
import com.lunatic.quicktranslate.domain.project.usecase.CancelProjectTranscodeTaskUseCase
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
    projectRepository: ProjectRepository,
    private val cancelProjectTranscodeTaskUseCase: CancelProjectTranscodeTaskUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(TranscodeTasksState())
    val state: StateFlow<TranscodeTasksState> = mutableState.asStateFlow()

    private val mutableEffect = MutableSharedFlow<TranscodeTasksEffect>()
    val effect: SharedFlow<TranscodeTasksEffect> = mutableEffect.asSharedFlow()

    private var projectsById: Map<Long, Project> = emptyMap()
    private var latestTasks: List<ProjectTranscodeTask> = emptyList()

    init {
        viewModelScope.launch {
            projectRepository.observeRecentProjects().collect { projects ->
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
            is TranscodeTasksIntent.DeleteTaskClicked -> {
                viewModelScope.launch {
                    cancelProjectTranscodeTaskUseCase(intent.taskId)
                }
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
            stageLabel = stage.toStageLabel(),
            progressLabel = progress?.let { "$it%" },
            etaLabel = toEtaLabel(),
            isFailed = status == ProjectTranscodeTaskStatus.FAILED,
            errorMessage = errorMessage
        )
    }

    private fun ProjectTranscodeTask.toEtaLabel(): String? {
        if (status != ProjectTranscodeTaskStatus.RUNNING) {
            return null
        }
        val startedAt = startedAtEpochMs ?: return null
        val value = progress?.coerceIn(0, 100) ?: return null
        if (value <= 0 || value >= 100) {
            return null
        }
        val elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        if (elapsedMs < 1_000L) {
            return null
        }
        val totalEstimateMs = (elapsedMs * 100L) / value
        val remainingMs = (totalEstimateMs - elapsedMs).coerceAtLeast(0L)
        if (remainingMs <= 0L) {
            return null
        }
        return "ETA ${formatEta(remainingMs)}"
    }

    private fun formatEta(valueMs: Long): String {
        val totalSeconds = (valueMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun ProjectTranscodeTaskStage.toStageLabel(): String {
        return when (this) {
            ProjectTranscodeTaskStage.QUEUED -> "Queued"
            ProjectTranscodeTaskStage.RESOLVING -> "Resolving"
            ProjectTranscodeTaskStage.DOWNLOADING -> "Downloading"
            ProjectTranscodeTaskStage.TRANSCRIBING -> "Transcribing"
            ProjectTranscodeTaskStage.SUCCEEDED -> "Succeeded"
            ProjectTranscodeTaskStage.FAILED -> "Failed"
            ProjectTranscodeTaskStage.CANCELED -> "Canceled"
        }
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
