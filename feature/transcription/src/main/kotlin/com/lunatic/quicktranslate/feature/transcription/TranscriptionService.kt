package com.lunatic.quicktranslate.feature.transcription

interface TranscriptionService {
    suspend fun transcribe(
        mediaPath: String,
        onProgress: ((Int) -> Unit)? = null
    ): List<TranscriptionSegment>
}
