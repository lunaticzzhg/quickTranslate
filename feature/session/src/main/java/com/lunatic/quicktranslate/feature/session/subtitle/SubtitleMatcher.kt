package com.lunatic.quicktranslate.feature.session.subtitle

object SubtitleMatcher {
    fun findActiveIndex(
        segments: List<SubtitleSegment>,
        positionMs: Long
    ): Int {
        return segments.indexOfFirst { segment ->
            positionMs in segment.startMs until segment.endMs
        }
    }
}
