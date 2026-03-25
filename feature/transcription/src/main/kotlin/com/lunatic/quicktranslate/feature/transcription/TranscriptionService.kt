package com.lunatic.quicktranslate.feature.transcription

interface TranscriptionService {
    suspend fun transcribe(
        mediaPath: String,
        onProgress: ((Int) -> Unit)? = null,
        onPartialResult: ((List<TranscriptionSegment>) -> Unit)? = null
    ): List<TranscriptionSegment>
}
