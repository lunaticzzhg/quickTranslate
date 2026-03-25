package com.lunatic.quicktranslate.feature.transcription

import kotlinx.coroutines.delay

class MockTranscriptionService : TranscriptionService {
    override suspend fun transcribe(
        mediaPath: String,
        onProgress: ((Int) -> Unit)?,
        onPartialResult: ((List<TranscriptionSegment>) -> Unit)?
    ): List<TranscriptionSegment> {
        require(mediaPath.isNotBlank()) { "Media path is required for transcription." }
        val all = listOf(
            TranscriptionSegment(0L, 2800L, "Welcome to your listening practice session."),
            TranscriptionSegment(2800L, 6200L, "Tap any subtitle line to jump and replay quickly."),
            TranscriptionSegment(6200L, 9800L, "Use loop training to repeat short chunks."),
            TranscriptionSegment(9800L, 13200L, "Focus on stress, rhythm, and linking sounds."),
            TranscriptionSegment(13200L, 17600L, "Keep your practice consistent for better progress.")
        )
        onProgress?.invoke(5)
        delay(200L)
        onPartialResult?.invoke(all.take(1))
        onProgress?.invoke(25)
        delay(250L)
        onPartialResult?.invoke(all.take(2))
        onProgress?.invoke(55)
        delay(300L)
        onPartialResult?.invoke(all.take(4))
        onProgress?.invoke(80)
        delay(1200L)
        onProgress?.invoke(100)
        onPartialResult?.invoke(all)
        return all
    }
}
