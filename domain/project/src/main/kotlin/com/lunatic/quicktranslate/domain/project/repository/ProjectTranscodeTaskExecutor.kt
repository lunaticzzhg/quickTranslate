package com.lunatic.quicktranslate.domain.project.repository

import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask

interface ProjectTranscodeTaskExecutor {
    suspend fun execute(task: ProjectTranscodeTask): Result<Unit>
}
