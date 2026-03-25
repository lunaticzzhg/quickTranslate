package com.lunatic.quicktranslate.feature.session.transcription

import com.lunatic.quicktranslate.domain.project.model.ProjectSubtitle
import com.lunatic.quicktranslate.domain.project.usecase.GetProjectSubtitlesUseCase
import com.lunatic.quicktranslate.feature.session.subtitle.SubtitleSegment

class SessionTranscriptionCoordinator(
    private val pipeline: SessionTranscriptionPipeline,
    private val getProjectSubtitlesUseCase: GetProjectSubtitlesUseCase
) {

    suspend fun restorePersistedSubtitles(projectId: Long): List<SubtitleSegment> {
        if (projectId <= 0L) {
            return emptyList()
        }
        return runCatching {
            getProjectSubtitlesUseCase(projectId).map { it.toUiSubtitle() }
        }.getOrDefault(emptyList())
    }

    suspend fun transcribeAndPersist(
        projectId: Long,
        mediaUri: String,
        onProgress: ((Int) -> Unit)? = null,
        onPartialSubtitles: ((List<SubtitleSegment>) -> Unit)? = null
    ): Result<List<SubtitleSegment>> {
        return pipeline.run(
            projectId = projectId,
            mediaUri = mediaUri,
            onProgress = onProgress,
            onPartialSubtitles = onPartialSubtitles
        )
    }

    private fun ProjectSubtitle.toUiSubtitle(): SubtitleSegment {
        return SubtitleSegment(
            id = sequenceIndex + 1L,
            startMs = startMs,
            endMs = endMs,
            text = text
        )
    }
}
