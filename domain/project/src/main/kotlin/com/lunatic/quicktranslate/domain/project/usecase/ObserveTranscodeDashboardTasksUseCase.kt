package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskType
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskRepository
import kotlinx.coroutines.flow.Flow

class ObserveTranscodeDashboardTasksUseCase(
    private val repository: ProjectTranscodeTaskRepository
) {
    operator fun invoke(
        taskType: String = ProjectTranscodeTaskType.TRANSCRIBE
    ): Flow<List<ProjectTranscodeTask>> {
        return repository.observeTasksForDashboard(taskType)
    }
}
