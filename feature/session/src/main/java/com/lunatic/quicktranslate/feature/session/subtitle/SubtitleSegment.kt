package com.lunatic.quicktranslate.feature.session.subtitle

data class SubtitleSegment(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String
)
