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
        val prepared = runCatching { prepareStage.prepare(mediaUri) }
            .getOrElse { error ->
                return persistStage.onFailure(projectId, error)
            }
        val transcriptionResult = executeStage.execute(
            prepared = prepared,
            onProgress = onProgress,
            onPartialSubtitles = onPartialSubtitles
        )
        return transcriptionResult.fold(
            onSuccess = { subtitles ->
                persistStage.onSuccess(projectId = projectId, subtitles = subtitles)
            },
            onFailure = { error ->
                persistStage.onFailure(projectId, error)
            }
        )
    }
}
