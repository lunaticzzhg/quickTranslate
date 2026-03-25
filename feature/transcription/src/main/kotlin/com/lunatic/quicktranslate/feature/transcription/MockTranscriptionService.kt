package com.lunatic.quicktranslate.feature.transcription

import kotlinx.coroutines.delay

class MockTranscriptionService {
    suspend fun transcribe(mediaUri: String): List<TranscriptionSegment> {
        require(mediaUri.isNotBlank()) { "Media uri is required for transcription." }
        delay(1200L)
        return listOf(
            TranscriptionSegment(0L, 2800L, "Welcome to your listening practice session."),
            TranscriptionSegment(2800L, 6200L, "Tap any subtitle line to jump and replay quickly."),
            TranscriptionSegment(6200L, 9800L, "Use loop training to repeat short chunks."),
            TranscriptionSegment(9800L, 13200L, "Focus on stress, rhythm, and linking sounds."),
            TranscriptionSegment(13200L, 17600L, "Keep your practice consistent for better progress.")
        )
    }
}
