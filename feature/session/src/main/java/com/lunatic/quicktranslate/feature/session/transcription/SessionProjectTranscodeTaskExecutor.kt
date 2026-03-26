package com.lunatic.quicktranslate.feature.session.transcription

import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskExecutor
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskRepository

class SessionProjectTranscodeTaskExecutor(
    private val chain: SessionProjectTranscodeChain,
    private val transcodeTaskRepository: ProjectTranscodeTaskRepository
) : ProjectTranscodeTaskExecutor {
    override suspend fun execute(task: ProjectTranscodeTask): Result<Unit> {
        val context = SessionProjectTranscodeContext(
            task = task,
            transcodeTaskRepository = transcodeTaskRepository
        )
        return chain.run(context)
    }
}
