package com.lunatic.quicktranslate.feature.transcription

data class TranscriptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
