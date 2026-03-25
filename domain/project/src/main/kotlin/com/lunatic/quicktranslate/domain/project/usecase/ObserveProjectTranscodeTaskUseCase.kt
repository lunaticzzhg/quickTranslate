package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskType
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskRepository
import kotlinx.coroutines.flow.Flow

class ObserveProjectTranscodeTaskUseCase(
    private val repository: ProjectTranscodeTaskRepository
) {
    operator fun invoke(
        projectId: Long,
        taskType: String = ProjectTranscodeTaskType.TRANSCRIBE
    ): Flow<ProjectTranscodeTask?> {
        return repository.observeProjectTask(
            projectId = projectId,
            taskType = taskType
        )
    }
}
