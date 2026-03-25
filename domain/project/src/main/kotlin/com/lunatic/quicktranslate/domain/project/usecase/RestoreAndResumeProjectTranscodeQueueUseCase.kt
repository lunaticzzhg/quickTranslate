package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeQueueEngine

class RestoreAndResumeProjectTranscodeQueueUseCase(
    private val queueEngine: ProjectTranscodeQueueEngine
) {
    suspend operator fun invoke() {
        queueEngine.restoreAndSignal()
    }
}
