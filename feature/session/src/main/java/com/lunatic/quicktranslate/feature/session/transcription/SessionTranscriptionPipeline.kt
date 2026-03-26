package com.lunatic.quicktranslate.feature.session.transcription

import com.lunatic.quicktranslate.feature.session.subtitle.SubtitleSegment

class SessionTranscriptionPipeline(
    private val prepareStage: SessionMediaPrepareStage,
    private val executeStage: SessionTranscriptionExecuteStage,
    private val persistStage: SessionSubtitlePersistStage
) {
    suspend fun run(
        projectId: Long,
        mediaUri: String,
        onProgress: ((Int) -> Unit)? = null,
        onPartialSubtitles: ((List<SubtitleSegment>) -> Unit)? = null
    ): Result<List<SubtitleSegment>> {
        persistStage.markProcessing(projectId)
        val prepared = runCatching {
            prepareStage.prepare(
                mediaUri = mediaUri,
                forceTranscodeToWav = false
            )
        }
            .getOrElse { error ->
                return persistStage.onFailure(projectId, error)
            }
        val firstResult = executeStage.execute(
            prepared = prepared,
            onProgress = onProgress,
            onPartialSubtitles = onPartialSubtitles
        )
        val transcriptionResult = if (
            firstResult.isFailure &&
            !prepared.transcodedToWav &&
            shouldRetryWithForcedWav(firstResult.exceptionOrNull())
        ) {
            val retryPrepared = runCatching {
                prepareStage.prepare(
                    mediaUri = mediaUri,
                    forceTranscodeToWav = true
                )
            }.getOrElse { error ->
                return persistStage.onFailure(projectId, error)
            }
            executeStage.execute(
                prepared = retryPrepared,
                onProgress = onProgress,
                onPartialSubtitles = onPartialSubtitles
            )
        } else {
            firstResult
        }
        return transcriptionResult.fold(
            onSuccess = { subtitles ->
                persistStage.onSuccess(projectId = projectId, subtitles = subtitles)
            },
            onFailure = { error ->
                persistStage.onFailure(projectId, error)
            }
        )
    }

    private fun shouldRetryWithForcedWav(error: Throwable?): Boolean {
        val message = error?.message?.lowercase().orEmpty()
        if (message.isBlank()) {
            return false
        }
        return message.contains("produced no subtitles") ||
            message.contains("unsupported audio") ||
            message.contains("bad audio")
    }
}
