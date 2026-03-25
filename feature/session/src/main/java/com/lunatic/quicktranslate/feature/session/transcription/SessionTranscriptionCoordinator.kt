package com.lunatic.quicktranslate.feature.session.transcription

import com.lunatic.quicktranslate.domain.project.model.ProjectSubtitle
import com.lunatic.quicktranslate.domain.project.model.SubtitleStatus
import com.lunatic.quicktranslate.domain.project.usecase.GetProjectSubtitlesUseCase
import com.lunatic.quicktranslate.domain.project.usecase.ReplaceProjectSubtitlesUseCase
import com.lunatic.quicktranslate.domain.project.usecase.UpdateProjectSubtitleStatusUseCase
import com.lunatic.quicktranslate.feature.session.subtitle.SubtitleSegment
import com.lunatic.quicktranslate.feature.transcription.MockTranscriptionService

class SessionTranscriptionCoordinator(
    private val transcriptionService: MockTranscriptionService,
    private val updateProjectSubtitleStatusUseCase: UpdateProjectSubtitleStatusUseCase,
    private val getProjectSubtitlesUseCase: GetProjectSubtitlesUseCase,
    private val replaceProjectSubtitlesUseCase: ReplaceProjectSubtitlesUseCase
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
        mediaUri: String
    ): Result<List<SubtitleSegment>> {
        if (projectId > 0L) {
            persistStatus(projectId, SubtitleStatus.PROCESSING)
        }
        val result = runCatching {
            transcriptionService.transcribe(mediaUri).mapIndexed { index, segment ->
                SubtitleSegment(
                    id = index + 1L,
                    startMs = segment.startMs,
                    endMs = segment.endMs,
                    text = segment.text
                )
            }
        }
        result.onSuccess { subtitles ->
            if (projectId > 0L) {
                runCatching {
                    replaceProjectSubtitlesUseCase(
                        projectId = projectId,
                        subtitles = subtitles.map { it.toProjectSubtitle() }
                    )
                }
                persistStatus(projectId, SubtitleStatus.COMPLETED)
            }
        }.onFailure {
            if (projectId > 0L) {
                persistStatus(projectId, SubtitleStatus.FAILED)
            }
        }
        return result
    }

    private suspend fun persistStatus(projectId: Long, status: SubtitleStatus) {
        runCatching {
            updateProjectSubtitleStatusUseCase(
                projectId = projectId,
                status = status
            )
        }
    }

    private fun ProjectSubtitle.toUiSubtitle(): SubtitleSegment {
        return SubtitleSegment(
            id = sequenceIndex + 1L,
            startMs = startMs,
            endMs = endMs,
            text = text
        )
    }

    private fun SubtitleSegment.toProjectSubtitle(): ProjectSubtitle {
        return ProjectSubtitle(
            sequenceIndex = (id - 1L).toInt(),
            startMs = startMs,
            endMs = endMs,
            text = text
        )
    }
}
