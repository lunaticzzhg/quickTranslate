package com.lunatic.quicktranslate.feature.session.transcription

import com.lunatic.quicktranslate.domain.project.model.ProjectSubtitle
import com.lunatic.quicktranslate.domain.project.model.SubtitleStatus
import com.lunatic.quicktranslate.domain.project.usecase.ReplaceProjectSubtitlesUseCase
import com.lunatic.quicktranslate.domain.project.usecase.UpdateProjectSubtitleStatusUseCase
import com.lunatic.quicktranslate.feature.session.subtitle.SubtitleSegment

class SessionSubtitlePersistStage(
    private val updateProjectSubtitleStatusUseCase: UpdateProjectSubtitleStatusUseCase,
    private val replaceProjectSubtitlesUseCase: ReplaceProjectSubtitlesUseCase
) {
    suspend fun markProcessing(projectId: Long) {
        if (projectId <= 0L) return
        persistStatus(projectId, SubtitleStatus.PROCESSING)
    }

    suspend fun onSuccess(
        projectId: Long,
        subtitles: List<SubtitleSegment>
    ): Result<List<SubtitleSegment>> {
        if (projectId <= 0L) {
            return Result.success(subtitles)
        }
        val persistResult = runCatching {
            replaceProjectSubtitlesUseCase(
                projectId = projectId,
                subtitles = subtitles.map { it.toProjectSubtitle() }
            )
        }
        if (persistResult.isFailure) {
            persistStatus(projectId, SubtitleStatus.FAILED)
            return Result.failure(
                IllegalStateException(
                    "Transcription succeeded but failed to persist subtitles.",
                    persistResult.exceptionOrNull()
                )
            )
        }
        persistStatus(projectId, SubtitleStatus.COMPLETED)
        return Result.success(subtitles)
    }

    suspend fun <T> onFailure(projectId: Long, error: Throwable): Result<T> {
        if (projectId > 0L) {
            persistStatus(projectId, SubtitleStatus.FAILED)
        }
        return Result.failure(error)
    }

    private suspend fun persistStatus(projectId: Long, status: SubtitleStatus) {
        runCatching {
            updateProjectSubtitleStatusUseCase(
                projectId = projectId,
                status = status
            )
        }
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
